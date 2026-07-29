package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Whether the memory block is stable between turns of one conversation.
 *
 * It has to be. Memories are injected into the system block, and the system block
 * is the KV cache prefix that this application's time to first token depends on
 * being byte identical between turns: 35 tokens of prefill against 863. Anything
 * that reorders that block re-prefills the whole thing on every turn.
 *
 * Written as a test rather than left as a note because it is a pure function and
 * the answer is knowable without a device.
 */
class MemoryPrefixStabilityTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun stored() = listOf(
        MemoryRetrieval.Item("The user always works in metric units.", now - day),
        MemoryRetrieval.Item("The user's rowing club is called Verity Quay.", now - 2 * day),
        MemoryRetrieval.Item("The user prefers tea to coffee.", now - 3 * day),
    )

    @Test
    fun theSameMemoriesAreChosenWhateverTheMessageIs() {
        // No relevance floor: everything fits, so everything is included whether
        // or not it relates to the message. This is the current behavior, pinned
        // so a change to it is deliberate.
        val a = MemoryRetrieval.select(stored(), "how do I fix the back gate", now, 4000, 12)
        val b = MemoryRetrieval.select(stored(), "what time does the library close", now, 4000, 12)
        assertThat(a).hasSize(3)
        assertThat(b).hasSize(3)
        assertThat(a.toSet()).isEqualTo(b.toSet())
    }

    @Test
    fun theOrderDoesNotChangeWithTheMessage() {
        // This is the fix, and it was written first as its own opposite: the
        // order used to follow the ranking, so two turns of one conversation
        // presented the same memories as different text and missed the cache.
        //
        // Ranking still decides which memories go in. It no longer decides the
        // order they are written in.
        val aboutRowing = MemoryRetrieval.select(
            stored(), "is the rowing club open on Sunday", now, 4000, 12,
        )
        val aboutTea = MemoryRetrieval.select(
            stored(), "I have run out of tea", now, 4000, 12,
        )
        assertThat(aboutRowing).isEqualTo(aboutTea)
    }

    @Test
    fun theOrderIsNewestFirst() {
        val chosen = MemoryRetrieval.select(stored(), "anything at all", now, 4000, 12)
        assertThat(chosen.first()).isEqualTo("The user always works in metric units.")
        assertThat(chosen.last()).isEqualTo("The user prefers tea to coffee.")
    }

    @Test
    fun anIrrelevantMemoryIsStillIncluded() {
        // The other half: relevance is asked of the model in the prompt and never
        // applied here, so a personal fact unconnected to the message is put in
        // front of the model anyway.
        val chosen = MemoryRetrieval.select(
            stored(), "what is the capital of Peru", now, 4000, 12,
        )
        assertThat(chosen).contains("The user's rowing club is called Verity Quay.")
    }
}
