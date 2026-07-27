package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that an answer with nothing in it is never shown as one.
 *
 * Logic Partner, given a well-formed argument, produced `decode=0tok`: the model
 * emitted end-of-generation before writing anything. The transcript then showed an
 * empty bubble, which is the worst available failure, because from the outside it
 * is indistinguishable from the app having broken.
 *
 * The engine change is a single branch and needs a device to exercise end to end,
 * so what is asserted here is the copy: that the message says what happened and
 * what to do, and stays inside the standing rules.
 */
class EmptyAnswerTest {

    private val message = "No answer came back. Try sending that again."

    @Test
    fun `it says what happened rather than apologising for it`() {
        assertThat(message).doesNotContain("Sorry")
        assertThat(message).doesNotContain("sorry")
        assertThat(message).doesNotContain("Oops")
    }

    @Test
    fun `it tells the user what to do next`() {
        assertThat(message).contains("again")
    }

    @Test
    fun `it does not blame the model or leak its name`() {
        // The user installed Kam AI. What the thing underneath did is not their
        // problem and not something to explain at them.
        listOf("model", "Gemma", "token", "inference").forEach {
            assertThat(message.lowercase()).doesNotContain(it.lowercase())
        }
    }

    @Test
    fun `no em dash, per the standing rule`() {
        assertThat(message).doesNotContain("—")
    }
}
