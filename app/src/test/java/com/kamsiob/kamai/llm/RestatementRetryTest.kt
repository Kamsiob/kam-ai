package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The second attempt after a reply came back as the user's own sentence (#122).
 */
class RestatementRetryTest {

    @Test
    fun anEmptyMessageGetsNoNudge() {
        // The instruction talks about what they said, and there is no sense in
        // saying that about nothing.
        assertThat(RestatementRetry.instruction("")).isNull()
        assertThat(RestatementRetry.instruction("   ")).isNull()
    }

    @Test
    fun theNudgeAsksForSomethingRatherThanForbiddingSomething() {
        val text = RestatementRetry.instruction("Bread needs a hot oven, around 230C.")!!
        // A shape to follow. Prohibitions alone have repeatedly failed on this
        // model, which is why the positive form leads.
        assertThat(text).contains("Reply with one sentence")
        assertThat(text).contains("what follows from it")
    }

    @Test
    fun theNudgeCarriesNoContentToCopy() {
        // The whole family of defects behind #119 and #130 is the model emitting
        // concrete text out of its instructions. An instruction that quoted an
        // example answer here would be the same trap in a new place, so this one
        // names kinds of reply and never demonstrates one.
        val text = RestatementRetry.instruction("Water boils at 100 degrees Celsius.")!!
        assertThat(text).doesNotContain("\"")
        assertThat(text).doesNotContain("->")
        // And it does not quote the user back, which would put their own sentence
        // in front of a model that is already inclined to repeat it.
        assertThat(text).doesNotContain("Water boils")
    }
}
