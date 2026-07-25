package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import org.junit.Test

/** The Brainstorm wrap-up action (#58). */
class WrapUpTest {

    @Test
    fun `only Brainstorm has a session to close`() {
        assertThat(WrapUp.availableIn(Mode.BRAINSTORM)).isTrue()
        listOf(Mode.GENERAL, Mode.LOGIC, Mode.BENCH, Mode.DISCOVER, Mode.OVERLAY)
            .forEach { assertThat(WrapUp.availableIn(it)).isFalse() }
    }

    @Test
    fun `the instruction forbids the two things it actually did wrong`() {
        // On the phone it recited its own procedure and then asked a question.
        val text = WrapUp.INSTRUCTION.lowercase()
        assertThat(text).contains("do not name your method")
        assertThat(text).contains("do not end with a question")
    }

    @Test
    fun `the instruction asks for the summary rather than naming a method`() {
        // Naming a method is what it echoes back, so the instruction describes
        // the shape of the answer instead.
        assertThat(WrapUp.INSTRUCTION).doesNotContain("STARBURSTING")
        assertThat(WrapUp.INSTRUCTION).doesNotContain("converge")
        assertThat(WrapUp.INSTRUCTION.lowercase()).contains("themes")
        assertThat(WrapUp.INSTRUCTION.lowercase()).contains("unresolved")
    }

    @Test
    fun `the transcript note is short and says what happened`() {
        assertThat(WrapUp.NOTE).isEqualTo("Wrapping up.")
    }

    @Test
    fun `no em dashes, like the rest of the copy`() {
        assertThat(WrapUp.INSTRUCTION).doesNotContain("—")
        assertThat(WrapUp.NOTE).doesNotContain("—")
    }
}
