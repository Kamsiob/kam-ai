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

/**
 * Recognising a typed request to finish (#58).
 *
 * The Wrap-up control was built because the instruction lost against a long
 * history. Typing the same words went down the ordinary path and hit exactly the
 * failure the control exists to fix, and typing is what most people will do,
 * since the control is in a menu.
 *
 * These are weighted towards what must NOT fire. A false positive ends a session
 * somebody was still in the middle of.
 */
class WrapUpRequestTest {

    @org.junit.Test
    fun `the plain ways of asking are recognised`() {
        listOf(
            "let's wrap up",
            "ok wrap this up",
            "can we wrap it up",
            "that's enough for now",
            "let's stop there",
            "sum up what we have",
            "pull it together",
            "what have we got",
            "I'm done",
            "converge",
        ).forEach { assertThat(WrapUp.isRequest(it)).isTrue() }
    }

    @org.junit.Test
    fun `capitals and trailing punctuation do not matter`() {
        assertThat(WrapUp.isRequest("Let's Wrap Up!")).isTrue()
        assertThat(WrapUp.isRequest("  wrap up  ")).isTrue()
    }

    @org.junit.Test
    fun `an ordinary idea is not a request to stop`() {
        listOf(
            "what about a subscription box",
            "the tricky part is the packaging",
            "I think the market is teachers",
            "wrapping paper for the boxes could be recycled",
            "we could stop selling on Sundays",
        ).forEach { assertThat(WrapUp.isRequest(it)).isFalse() }
    }

    @org.junit.Test
    fun `asking about a method is not asking to end the session`() {
        // "summary" as a keyword would fire on this, which is a question inside
        // the session rather than a request to close it.
        assertThat(WrapUp.isRequest("give me a summary of what hub and spoke means")).isFalse()
        assertThat(WrapUp.isRequest("what is a good summary technique")).isFalse()
    }

    @org.junit.Test
    fun `a long message is treated as content, not a command`() {
        // Somebody mid-flow who happens to use the words is brainstorming, not
        // asking to finish. A request to stop is short.
        val long = "I keep going back and forth on this and I want to pull it together " +
            "eventually but first there is the question of who actually buys it, because " +
            "the schools angle and the parents angle are completely different businesses"
        assertThat(WrapUp.isRequest(long)).isFalse()
    }

    @org.junit.Test
    fun `an empty message asks for nothing`() {
        assertThat(WrapUp.isRequest("")).isFalse()
        assertThat(WrapUp.isRequest("   ")).isFalse()
    }
}
