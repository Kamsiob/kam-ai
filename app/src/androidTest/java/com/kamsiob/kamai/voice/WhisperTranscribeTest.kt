package com.kamsiob.kamai.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kamsiob.kamai.data.KamRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Proves whisper.cpp actually transcribes on device, not just that the plumbing
 * compiles. It runs the standard whisper sample (jfk.wav, "...ask not what your
 * country can do for you...") through the real SttEngine and checks the words
 * come back.
 *
 * The Standard voice model must already be downloaded (Settings, Voice). The test
 * skips itself if it is not there, rather than failing, so it is safe to run on a
 * device that has not set voice up.
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscribeTest {

    @Test
    fun transcribesTheJfkSample() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Assets live in the test APK, reachable through the instrumentation
        // context, not the app-under-test context.
        val testAssets = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().context.assets
        val repository = KamRepository.get(context)
        // Prefer the model the app actually installed; otherwise use a bundled
        // test asset if one is present. This keeps the test self-contained even
        // though the test harness reinstalls the app (wiping downloads) between
        // runs. If neither is available, skip rather than fail.
        val installed = File(repository.voiceDir(), SttCatalog.STANDARD.fileName)
        val modelFile: File = when {
            installed.exists() -> installed
            hasAsset(testAssets, "ggml-base.bin") -> {
                File(context.cacheDir, "ggml-base.bin").also { out ->
                    if (!out.exists() || out.length() == 0L) {
                        testAssets.open("ggml-base.bin").use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                    }
                }
            }
            else -> File("/nonexistent")
        }
        assumeTrue(
            "No whisper model available; skipping on-device transcription test.",
            modelFile.exists(),
        )

        val pcm = readWavMono16k(testAssets.open("jfk.wav").readBytes())
        assertTrue("sample should hold several seconds of audio", pcm.size > 16_000)

        val result = SttEngine().transcribe(modelFile, pcm, language = "en")
        assertTrue("transcription should succeed", result is SttEngine.Result.Ok)

        val text = (result as SttEngine.Result.Ok).text.lowercase()
        // The clip is unmistakable; require the distinctive words, not an exact
        // string, since whisper's punctuation and casing vary.
        assertTrue(
            "expected the JFK line, got: $text",
            text.contains("country") && text.contains("you"),
        )
    }

    private fun hasAsset(assets: android.content.res.AssetManager, name: String): Boolean =
        runCatching { assets.open(name).use { true } }.getOrDefault(false)

    /** Minimal WAV reader for the 16 kHz mono 16-bit PCM the sample uses. */
    private fun readWavMono16k(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // Skip "RIFF" size "WAVE".
        buf.position(12)
        var dataOffset = -1
        var dataLen = 0
        while (buf.remaining() >= 8) {
            val id = ByteArray(4).also { buf.get(it) }
            val size = buf.int
            if (String(id) == "data") {
                dataOffset = buf.position()
                dataLen = size
                break
            }
            buf.position(buf.position() + size)
        }
        require(dataOffset >= 0) { "no data chunk in WAV" }

        val samples = FloatArray(dataLen / 2)
        val pcm = ByteBuffer.wrap(bytes, dataOffset, dataLen).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) {
            samples[i] = pcm.short / 32768.0f
        }
        return samples
    }
}
