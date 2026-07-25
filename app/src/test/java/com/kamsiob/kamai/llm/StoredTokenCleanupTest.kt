package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Cleaning stray template tokens out of text that is already on disk (#59).
 *
 * #49 stopped the leak; these are the messages written before it. Nothing
 * re-examined stored content on the way to the screen, so a conversation from
 * before the fix still shows "Hello. How can I help you. </start_of_turn>" in
 * the chat list today.
 */
class StoredTokenCleanupTest {

    @Test
    fun `the marker the owner can actually see is removed`() {
        val stored = "Hello. How can I help you.\n</start_of_turn>"
        assertThat(PromptBuilder.withoutControlTokens(stored))
            .isEqualTo("Hello. How can I help you.")
    }

    @Test
    fun `garbled variants go too`() {
        listOf("<end_of_of_turn>", "<start_of_turn>", "<|im_end|>", "<|endoftext|>").forEach {
            assertThat(PromptBuilder.withoutControlTokens("Answer. $it")).isEqualTo("Answer.")
        }
    }

    @Test
    fun `ordinary text is returned untouched`() {
        val plain = "The Nile and the Amazon are two rivers."
        assertThat(PromptBuilder.withoutControlTokens(plain)).isSameInstanceAs(plain)
    }

    @Test
    fun `text with angle brackets that are not tokens survives`() {
        // Comparisons, code and maths all use these.
        val code = "Use <T> for the type, and check a < b before <div> is closed."
        assertThat(PromptBuilder.withoutControlTokens(code)).isEqualTo(code)
    }

    @Test
    fun `nothing after a stop marker is thrown away`() {
        // The difference from cleanOutput, and the reason this exists separately.
        // Truncating stored text on its way to the screen would look like data
        // loss rather than a rendering fix.
        val stored = "First part. <end_of_turn> Second part that is still the user's."
        val out = PromptBuilder.withoutControlTokens(stored)
        assertThat(out).contains("First part.")
        assertThat(out).contains("Second part that is still the user's.")
    }

    @Test
    fun `content is never emptied by cleaning`() {
        // A message that is nothing but a leaked marker becomes empty, which is
        // honest: there was never anything else in it.
        assertThat(PromptBuilder.withoutControlTokens("</start_of_turn>")).isEmpty()
    }
}
