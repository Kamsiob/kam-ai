package com.kamsiob.kamai.discover

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Whether a moment has anything behind its preview (#13).
 *
 * The quiz prompt says "The quiz is drawn from the full passage, not just the
 * preview. Reading it first gives you a fair shot." Measured on the shipped
 * history pack, 635 of 2000 moments have a passage byte-identical to their
 * preview, so for a third of the pack that sentence promises an advantage that
 * does not exist and "Read it first" leads to the same words.
 *
 * The rule is a trimmed comparison, which is the same one the view model makes,
 * kept here so it is stated once and tested.
 */
class MoreToReadTest {

    private fun hasMoreToRead(preview: String, passage: String) =
        passage.trim() != preview.trim()

    @Test
    fun `an identical passage has nothing more to read`() {
        val text = "The 1931 China floods were a series of floods that occurred in China."
        assertThat(hasMoreToRead(text, text)).isFalse()
    }

    @Test
    fun `trailing whitespace does not count as more to read`() {
        assertThat(hasMoreToRead("A passage.", "A passage.\n")).isFalse()
        assertThat(hasMoreToRead("  A passage.  ", "A passage.")).isFalse()
    }

    @Test
    fun `a longer passage does have more to read`() {
        assertThat(hasMoreToRead("The lead section.", "The lead section. And then much more.")).isTrue()
    }

    @Test
    fun `a genuinely different passage counts even at the same length`() {
        assertThat(hasMoreToRead("aaaa", "bbbb")).isTrue()
    }
}
