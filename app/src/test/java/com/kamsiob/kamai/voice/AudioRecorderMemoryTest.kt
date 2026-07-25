package com.kamsiob.kamai.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What a long voice recording actually costs to hold (#39).
 *
 * `AudioRecorder` accumulated samples in an `ArrayList<Float>` under a comment
 * saying a minute cost "under two megabytes". That is true of the audio and
 * badly wrong about the container: every sample becomes a boxed
 * `java.lang.Float`. The flagship voice flow in MASTER_SPEC is a long ramble,
 * which is the one case that estimate had to be right about.
 *
 * These are arithmetic rather than a test of the recorder, because the recorder
 * needs a microphone and this needs neither. They pin the reasoning that
 * justified the change so nobody quietly puts the boxing back.
 */
class AudioRecorderMemoryTest {

    private val sampleRate = 16_000

    /** A boxed Float: object header and value, plus the reference held in the list. */
    private val bytesPerBoxedSample = 20

    private val bytesPerPrimitiveSample = 4

    private fun samplesIn(minutes: Int) = sampleRate * 60 * minutes

    @Test
    fun `a minute of boxed samples is nowhere near two megabytes`() {
        val megabytes = samplesIn(1).toLong() * bytesPerBoxedSample / 1_000_000
        assertThat(megabytes).isAtLeast(15L)
    }

    @Test
    fun `a five minute brain dump boxed runs to most of a hundred megabytes`() {
        // Beside a model already using most of the phone.
        val megabytes = samplesIn(5).toLong() * bytesPerBoxedSample / 1_000_000
        assertThat(megabytes).isAtLeast(90L)
    }

    @Test
    fun `the same five minutes as primitives is what the comment promised`() {
        val megabytes = samplesIn(5).toLong() * bytesPerPrimitiveSample / 1_000_000
        assertThat(megabytes).isAtMost(20L)
    }

    @Test
    fun `chunks add up to the same audio`() {
        // The shape of the change: many small FloatArrays concatenated once at
        // the end, rather than one array regrown as it fills.
        val chunks = listOf(
            FloatArray(3) { it * 0.1f },
            FloatArray(2) { 0.5f + it },
            FloatArray(0),
        )
        val total = chunks.sumOf { it.size }
        val out = FloatArray(total)
        var at = 0
        chunks.forEach { chunk ->
            chunk.copyInto(out, at)
            at += chunk.size
        }

        assertThat(out.size).isEqualTo(5)
        assertThat(out.toList()).containsExactly(0.0f, 0.1f, 0.2f, 0.5f, 1.5f).inOrder()
    }
}
