package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The measured speed figure shown in the model picker (item 22). */
class MeasuredSpeedTest {

    @Test
    fun `a model nobody has run says nothing`() {
        assertThat(MeasuredSpeed.describe(null)).isNull()
    }

    @Test
    fun `one run is a measurement, not a speed`() {
        // It could have been a cold start or a moment of throttling. Saying "about
        // 8 words a second" on that basis is the confident wrong number this app
        // is built not to produce.
        val once = MeasuredSpeed.fold(null, 11.0)
        assertThat(MeasuredSpeed.describe(once)).isNull()
    }

    @Test
    fun `two runs are enough to say something`() {
        var stored = MeasuredSpeed.fold(null, 11.0)
        stored = MeasuredSpeed.fold(stored, 11.0)
        assertThat(MeasuredSpeed.describe(stored)).isEqualTo("About 8 words a second on this phone")
    }

    @Test
    fun `the figures measured on the real phone read sensibly`() {
        // E4B decoded at 5.9 to 6.4 tok/s and E2B at 10.8 to 11.0, measured on
        // the owner's device. Those should read as different speeds, which is the
        // whole point of showing it.
        var e4b = MeasuredSpeed.fold(null, 5.9)
        e4b = MeasuredSpeed.fold(e4b, 6.4)
        var e2b = MeasuredSpeed.fold(null, 10.8)
        e2b = MeasuredSpeed.fold(e2b, 11.0)

        assertThat(MeasuredSpeed.describe(e4b)).isEqualTo("About 4 words a second on this phone")
        assertThat(MeasuredSpeed.describe(e2b)).isEqualTo("About 8 words a second on this phone")
    }

    @Test
    fun `one bad run cannot dominate a settled average`() {
        var stored = MeasuredSpeed.fold(null, 10.0)
        repeat(9) { stored = MeasuredSpeed.fold(stored, 10.0) }
        val settled = MeasuredSpeed.decode(stored)!!.first

        stored = MeasuredSpeed.fold(stored, 1.0)
        val after = MeasuredSpeed.decode(stored)!!.first

        assertThat(settled).isWithin(0.01).of(10.0)
        assertThat(after).isGreaterThan(9.0)
    }

    @Test
    fun `damaged or empty stored values are ignored rather than shown`() {
        listOf("", "nonsense", "12", "12|", "|3", "0|5", "8|0", "abc|def")
            .forEach { assertThat(MeasuredSpeed.decode(it)).isNull() }
    }

    @Test
    fun `a damaged value does not stop the next measurement being recorded`() {
        val recovered = MeasuredSpeed.fold("nonsense", 9.0)
        assertThat(MeasuredSpeed.decode(recovered)).isNotNull()
    }
}
