package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Whether the memory block is stable between turns of one conversation.
 *
 * It has to be, as far as it can be. Memories are injected into the system block,
 * and the system block is the KV cache prefix that this application's time to
 * first token depends on being byte identical between turns: 35 tokens of prefill
 * against 863. Anything that reorders that block re-prefills the whole thing on
 * every turn.
 *
 * Written as a test rather than left as a note because it is a pure function and
 * the answer is knowable without a device.
 *
 * **This class used to pin the no-floor behavior, and #133 changed it deliberately.**
 * The old pin said "everything is included whether or not it relates to the
 * message", which was perfectly stable and was also the defect. A floor buys back
 * honesty and context and costs some of that stability, because the *set* now
 * depends on the message's words. What is still guaranteed is pinned below; what is
 * no longer guaranteed is named in the last test rather than left for somebody to
 * discover from a slow first token.
 */
class MemoryPrefixStabilityTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    // Three real memories from the #133 probes, and the mix matters: one standing
    // fact, one topical fact whose wording is a trap, one ordinary preference.
    private fun stored() = listOf(
        MemoryRetrieval.Item("The user always works in metric units.", now - day),
        MemoryRetrieval.Item("The user's rowing club is called Verity Quay.", now - 2 * day),
        MemoryRetrieval.Item("The user prefers tea to coffee.", now - 3 * day),
    )

    @Test
    fun aStandingFactIsPresentWhateverTheMessageIs() {
        // This is what keeps the prefix stable on the ordinary turn: the standing
        // half of the block does not depend on the message at all.
        val a = MemoryRetrieval.select(stored(), "how do I fix the back gate", now, 4000, 12)
        val b = MemoryRetrieval.select(stored(), "what time does the library close", now, 4000, 12)
        assertThat(a).containsExactly("The user always works in metric units.")
        assertThat(b).isEqualTo(a)
    }

    @Test
    fun theOrderDoesNotChangeWithTheMessage() {
        // The original fix, still load bearing. Ranking decides which memories go
        // in. It must never decide the order they are written in, because the order
        // following the ranking is what made two turns of one conversation present
        // the same memories as different text and miss the cache: measured at 275
        // and 357 tokens of prefill and 10.1 seconds to first token.
        val teaFirst = MemoryRetrieval.select(stored(), "tea and rowing", now, 4000, 12)
        val rowingFirst = MemoryRetrieval.select(stored(), "rowing and tea", now, 4000, 12)
        assertThat(teaFirst).isEqualTo(rowingFirst)
    }

    @Test
    fun theOrderIsNewestFirst() {
        val chosen = MemoryRetrieval.select(
            stored(), "tea at the rowing club", now, 4000, 12,
        )
        assertThat(chosen.first()).isEqualTo("The user always works in metric units.")
        assertThat(chosen.last()).isEqualTo("The user prefers tea to coffee.")
    }

    @Test
    fun anIrrelevantMemoryIsNoLongerIncluded() {
        // The half #133 fixed. Relevance was asked of the model in the prompt and
        // never applied here, so a personal fact unconnected to the message went in
        // front of the model anyway and the app then said it had used it.
        val chosen = MemoryRetrieval.select(
            stored(), "what is the capital of Peru", now, 4000, 12,
        )
        assertThat(chosen).doesNotContain("The user's rowing club is called Verity Quay.")
        assertThat(chosen).doesNotContain("The user prefers tea to coffee.")
    }

    @Test
    fun theSetVariesWithTheMessageAndThatCostsPrefill() {
        // Not an assertion that this is desirable. It is the price of the floor,
        // pinned so it is a known cost rather than a mystery in a device
        // measurement: on a turn that overlaps a stored fact the memory block gains
        // an entry, the block is different text, and everything after it in the
        // prompt re-prefills, which in a long conversation is the history.
        //
        // Sized on the device with tools/prefix_probe.sh rather than guessed. The
        // architectural fix, if the cost turns out to matter, is to keep only
        // standing facts in the cached prefix and place topical ones next to the
        // current turn, and that is tracked separately rather than smuggled in here.
        val ordinary = MemoryRetrieval.select(stored(), "how do I fix the back gate", now, 4000, 12)
        val overlapping = MemoryRetrieval.select(stored(), "is the rowing club open", now, 4000, 12)
        assertThat(overlapping).isNotEqualTo(ordinary)
        assertThat(overlapping).contains("The user's rowing club is called Verity Quay.")
    }
}
