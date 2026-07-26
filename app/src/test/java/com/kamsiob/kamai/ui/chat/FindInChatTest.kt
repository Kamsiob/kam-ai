package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Finding something said earlier in an open conversation (#85).
 *
 * The matching, the ordering and the wrap-around are the parts that go wrong,
 * and none of them needs a screen to be wrong on.
 */
class FindInChatTest {

    private val transcript = listOf(
        "What is a roux?",
        "Flour and fat cooked together. A roux thickens a sauce.",
        "Can I make a roux with butter?",
        "Yes. Butter is the usual fat for a roux.",
    )

    @Test
    fun `every occurrence is found, in reading order`() {
        val hits = FindInChat.matches(transcript, "roux")
        assertThat(hits.map { it.messageIndex }).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `a word twice in one message is two matches`() {
        val hits = FindInChat.matches(listOf("a roux is a roux"), "roux")
        assertThat(hits).hasSize(2)
        assertThat(hits[0].start).isLessThan(hits[1].start)
    }

    @Test
    fun `case does not matter, because nobody searching their own chat thinks about it`() {
        assertThat(FindInChat.matches(listOf("A Roux"), "roux")).hasSize(1)
        assertThat(FindInChat.matches(listOf("a roux"), "ROUX")).hasSize(1)
    }

    @Test
    fun `an empty or blank query matches nothing rather than everything`() {
        assertThat(FindInChat.matches(transcript, "")).isEmpty()
        assertThat(FindInChat.matches(transcript, "   ")).isEmpty()
    }

    @Test
    fun `surrounding spaces in the query are ignored`() {
        assertThat(FindInChat.matches(transcript, "  roux ")).hasSize(4)
    }

    @Test
    fun `matches carry where in the message they are, for the highlight`() {
        val hits = FindInChat.matches(listOf("what is a roux"), "roux")
        assertThat(hits.single().start).isEqualTo(10)
        assertThat(hits.single().end).isEqualTo(14)
    }

    @Test
    fun `stepping forward wraps to the first rather than going dead at the end`() {
        // Otherwise the last match makes somebody scroll to the top by hand.
        assertThat(FindInChat.step(current = 3, total = 4, forward = true)).isEqualTo(0)
        assertThat(FindInChat.step(current = 0, total = 4, forward = true)).isEqualTo(1)
    }

    @Test
    fun `stepping back from the first wraps to the last`() {
        assertThat(FindInChat.step(current = 0, total = 4, forward = false)).isEqualTo(3)
    }

    @Test
    fun `stepping with nothing found stays put instead of dividing by zero`() {
        assertThat(FindInChat.step(current = 0, total = 0, forward = true)).isEqualTo(0)
        assertThat(FindInChat.step(current = 0, total = 0, forward = false)).isEqualTo(0)
    }

    @Test
    fun `the count reads as a position, and says so plainly when there is none`() {
        assertThat(FindInChat.countLabel(current = 0, total = 4)).isEqualTo("1 of 4")
        assertThat(FindInChat.countLabel(current = 3, total = 4)).isEqualTo("4 of 4")
        assertThat(FindInChat.countLabel(current = 0, total = 0)).isEqualTo("No matches")
    }

    @Test
    fun `a query longer than the text finds nothing and does not overrun`() {
        assertThat(FindInChat.matches(listOf("hi"), "hello there")).isEmpty()
    }

    @Test
    fun `a match at the very end of a message is found`() {
        val hits = FindInChat.matches(listOf("the fat for a roux"), "roux")
        assertThat(hits).hasSize(1)
        assertThat(hits.single().end).isEqualTo(18)
    }
}
