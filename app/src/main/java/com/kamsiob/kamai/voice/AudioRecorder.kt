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
 * guaranteed to support, so there is no resampling to get wrong.
 *
 * The samples accumulate in memory, in chunks of primitive [FloatArray]. They
 * used to accumulate in an `ArrayList<Float>`, whose doc claimed a minute cost
 * under two megabytes. That is true of the floats and badly wrong about the
 * list: every sample becomes a boxed `java.lang.Float`, so a minute is closer to
 * nineteen megabytes and five minutes is most of a hundred, next to a model that
 * is already using most of the phone. The flagship voice flow in MASTER_SPEC is
 * a long ramble, which is exactly the case that estimate got wrong (#39).
 *
 * Chunks rather than one growing array, so a long recording never has to copy
 * the whole thing to make room.
 */
class AudioRecorder {

    private val recording = AtomicBoolean(false)
    private var job: Job? = null
    /** Captured audio, in the order recorded. Guarded by [lock]. */
    private val chunks = ArrayList<FloatArray>()
    private var sampleCount = 0
    private val lock = Any()

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

        synchronized(lock) { chunks.clear(); sampleCount = 0 }

        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(bufferSize / 2)
            try {
                record.startRecording()
                while (recording.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // 16-bit PCM to float in [-1, 1], into a chunk of its own.
                        val chunk = FloatArray(read) { buffer[it] / 32768.0f }
                        synchronized(lock) {
                            chunks.add(chunk)
                            sampleCount += read
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
        return synchronized(lock) {
            val out = FloatArray(sampleCount)
            var at = 0
            chunks.forEach { chunk ->
                chunk.copyInto(out, at)
                at += chunk.size
            }
            out
        }
    }

    /** Abandons the recording and its samples, for example when a call arrives. */
    fun cancel() {
        recording.set(false)
        job?.cancel()
        job = null
        synchronized(lock) { chunks.clear(); sampleCount = 0 }
    }

    /** Seconds captured so far, for a live duration read-out. */
    val seconds: Float
        get() = synchronized(lock) { sampleCount.toFloat() / SAMPLE_RATE }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
