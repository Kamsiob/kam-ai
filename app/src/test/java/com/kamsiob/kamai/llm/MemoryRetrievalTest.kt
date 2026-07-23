package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards item 16's retrieval: memory is selected by relevance to the message and
 * recency within a budget, never dumped wholesale. A directly relevant fact must
 * win over an irrelevant recent one, and the budget must be respected.
 */
class MemoryRetrievalTest {

    private val now = 1_000_000_000_000L
    private fun item(text: String, ageDays: Long) =
        MemoryRetrieval.Item(text, now - ageDays * 86_400_000L)

    @Test
    fun `a relevant older fact beats an irrelevant recent one`() {
        val items = listOf(
            item("is allergic to peanuts", ageDays = 40),   // relevant, old
            item("likes the colour blue", ageDays = 0),      // irrelevant, fresh
        )
        val chosen = MemoryRetrieval.select(items, "can I eat this peanut snack?", now, budgetChars = 30, max = 1)
        assertThat(chosen).containsExactly("is allergic to peanuts")
    }

    @Test
    fun `the character budget is respected`() {
        val items = listOf(
            item("aaaaaaaaaa", 1), item("bbbbbbbbbb", 2), item("cccccccccc", 3),
        )
        // Budget fits about two 10-char entries plus separators.
        val chosen = MemoryRetrieval.select(items, "x", now, budgetChars = 22, max = 10)
        assertThat(chosen.size).isAtMost(2)
    }

    @Test
    fun `the max count is respected`() {
        val items = (1..10).map { item("fact number $it", it.toLong()) }
        val chosen = MemoryRetrieval.select(items, "fact", now, budgetChars = 10_000, max = 3)
        assertThat(chosen).hasSize(3)
    }

    @Test
    fun `empty inputs are safe`() {
        assertThat(MemoryRetrieval.select(emptyList(), "q", now, 100, 5)).isEmpty()
        assertThat(MemoryRetrieval.select(listOf(item("x", 1)), "q", now, 0, 5)).isEmpty()
    }

    @Test
    fun `normalise collapses punctuation and spacing for dedup`() {
        assertThat(MemoryExtractor.normalise("Likes  tea.")).isEqualTo(MemoryExtractor.normalise("likes tea"))
    }

    @Test
    fun `auto-reply parsing strips chat template tokens and drops NONE`() {
        // The bug this guards: "NONE</start_of_turn>" and a bare template token
        // were being stored as junk memories.
        assertThat(MemoryExtractor.parseAutoReply("NONE</start_of_turn>")).isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("</start_of_turn>")).isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("likes tea<end_of_turn>")).containsExactly("likes tea")
    }
}
