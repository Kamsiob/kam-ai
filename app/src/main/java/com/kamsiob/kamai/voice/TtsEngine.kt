package com.kamsiob.kamai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

/**
 * Text to speech over the sherpa-onnx runtime, never the Android system voice.
 *
 * The Piper voices all share one espeak-ng phonemiser data set and one tokens
 * file, bundled in the app and unpacked to disk once on first use. A voice model
 * is a single downloaded file. sherpa-onnx synthesises the whole utterance to
 * float PCM, which is then streamed to an AudioTrack so playback can be stopped
 * the instant the user navigates away or a call arrives.
 *
 * @param onBeforeLoad a hook to make room before the runtime loads its model, the
 *   same voice-shares-the-budget discipline the speech-to-text engine uses.
 */
class TtsEngine(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val threadCount: () -> Int = {
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)
    },
    private val onBeforeLoad: suspend () -> Unit = {},
) {

    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    private val lock = Mutex()
    private var tts: OfflineTts? = null
    private var loadedVoiceId: String? = null

    private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)

    val isSpeaking: Boolean get() = playing.get()

    /**
     * Speaks [text] in the voice whose model is [voiceFile]. Loads the runtime if
     * needed, synthesises, and plays. Any playback already in flight is stopped
     * first. Returns when playback finishes, is stopped, or fails.
     */
    suspend fun speak(voice: TtsVoice, voiceFile: File, text: String): Result =
        withContext(dispatcher) {
            if (text.isBlank()) return@withContext Result.Ok
            stop()

            val engine = lock.withLock {
                if (tts != null && loadedVoiceId == voice.id) {
                    tts
                } else {
                    tts?.release()
                    tts = null
                    loadedVoiceId = null
                    onBeforeLoad()
                    val built = runCatching { build(voice, voiceFile) }.getOrNull()
                    if (built != null) {
                        tts = built
                        loadedVoiceId = voice.id
                    }
                    built
                }
            } ?: return@withContext Result.Error(
                "That voice could not be loaded. Try downloading it again.",
            )

            val audio = runCatching { engine.generate(text, sid = 0, speed = 1.0f) }.getOrNull()
                ?: return@withContext Result.Error("The voice could not read that. Try again.")

            play(audio.samples, audio.sampleRate)
            Result.Ok
        }

    /** Result of synthesis without playback, used to verify generation on device. */
    class Synthesis(val samples: FloatArray, val sampleRate: Int)

    /** Synthesises [text] to PCM without playing it. For verification. */
    suspend fun synthesize(voice: TtsVoice, voiceFile: File, text: String): Synthesis? =
        withContext(dispatcher) {
            lock.withLock {
                if (tts == null || loadedVoiceId != voice.id) {
                    tts?.release()
                    tts = runCatching { build(voice, voiceFile) }.getOrNull()
                    loadedVoiceId = if (tts != null) voice.id else null
                }
                tts
            }?.let { engine ->
                runCatching { engine.generate(text, sid = 0, speed = 1.0f) }.getOrNull()
                    ?.let { Synthesis(it.samples, it.sampleRate) }
            }
        }

    /** Stops any playback immediately. Safe to call when nothing is playing. */
    fun stop() {
        playing.set(false)
        track?.let {
            runCatching { it.pause(); it.flush(); it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    /** Frees the loaded model. Called under memory pressure and when backgrounded. */
    suspend fun release() {
        stop()
        lock.withLock {
            tts?.release()
            tts = null
            loadedVoiceId = null
        }
    }

    private fun build(voice: TtsVoice, voiceFile: File): OfflineTts {
        val common = ensureCommonData()
        val config = when (voice.kind) {
            TtsKind.PIPER -> OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = voiceFile.absolutePath,
                        tokens = File(common, "tokens.txt").absolutePath,
                        dataDir = File(common, "espeak-ng-data").absolutePath,
                    ),
                    numThreads = threadCount(),
                ),
            )
            TtsKind.KOKORO -> error("Kokoro is configured in its own path")
        }
        return OfflineTts(config = config)
    }

    /**
     * Unpacks the shared Piper data (espeak-ng-data and tokens) from the app's
     * assets to a stable directory on disk, once. sherpa-onnx reads them from the
     * filesystem, alongside the downloaded model, so everything must be real
     * files rather than assets.
     */
    private fun ensureCommonData(): File {
        val dir = File(context.filesDir, "voice/piper-common")
        val tokens = File(dir, "tokens.txt")
        val espeak = File(dir, "espeak-ng-data")
        if (tokens.exists() && espeak.isDirectory) return dir

        dir.mkdirs()
        context.assets.open("piper-tokens.txt").use { input ->
            tokens.outputStream().use { input.copyTo(it) }
        }
        ZipInputStream(context.assets.open("espeak-ng-data.zip")).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val out = File(dir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
        return dir
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(sampleRate * 2)

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = newTrack
        playing.set(true)
        newTrack.play()

        var offset = 0
        while (playing.get() && offset < samples.size) {
            val count = minOf(1024, samples.size - offset)
            val written = newTrack.write(
                samples, offset, count, AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) break
            offset += written
        }
        // Let the last buffered audio finish before tearing the track down, unless
        // we were stopped.
        if (playing.get()) {
            runCatching {
                newTrack.stop()
            }
        }
        if (track === newTrack) {
            runCatching { newTrack.release() }
            track = null
        }
        playing.set(false)
    }
}
