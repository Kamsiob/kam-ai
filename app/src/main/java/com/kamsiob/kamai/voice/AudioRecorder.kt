package com.kamsiob.kamai.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records microphone audio as the 16 kHz mono float PCM whisper.cpp expects.
 *
 * whisper wants exactly 16 kHz mono, which is also the rate AudioRecord is
 * guaranteed to support, so there is no resampling to get wrong. The recorded
 * samples accumulate in memory; a voice note is short, and holding a minute of
 * 16 kHz mono is under two megabytes.
 */
class AudioRecorder {

    private val recording = AtomicBoolean(false)
    private var job: Job? = null
    private val samples = ArrayList<Float>(SAMPLE_RATE * 8)

    val isRecording: Boolean get() = recording.get()

    /**
     * Starts recording on [scope]. Returns true if recording began; false if the
     * microphone could not be opened (permission or hardware). Call [stop] to get
     * the samples.
     */
    @SuppressLint("MissingPermission") // The caller holds RECORD_AUDIO before calling.
    fun start(scope: CoroutineScope): Boolean {
        if (recording.getAndSet(true)) return true

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            recording.set(false)
            return false
        }
        val bufferSize = minBuffer * 4

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: Exception) {
            recording.set(false)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            recording.set(false)
            return false
        }

        synchronized(samples) { samples.clear() }

        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(bufferSize / 2)
            try {
                record.startRecording()
                while (recording.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        synchronized(samples) {
                            for (i in 0 until read) {
                                // 16-bit PCM to float in [-1, 1].
                                samples.add(buffer[i] / 32768.0f)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // A hardware hiccup ends the recording; whatever was captured so
                // far is still returned by stop().
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
        return true
    }

    /** Stops recording and returns the captured 16 kHz mono float PCM. */
    fun stop(): FloatArray {
        recording.set(false)
        job?.cancel()
        job = null
        return synchronized(samples) { samples.toFloatArray() }
    }

    /** Abandons the recording and its samples, for example when a call arrives. */
    fun cancel() {
        recording.set(false)
        job?.cancel()
        job = null
        synchronized(samples) { samples.clear() }
    }

    /** Seconds captured so far, for a live duration read-out. */
    val seconds: Float
        get() = synchronized(samples) { samples.size.toFloat() / SAMPLE_RATE }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
