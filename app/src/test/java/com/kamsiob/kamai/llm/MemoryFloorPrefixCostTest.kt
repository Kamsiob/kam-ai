package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * How often does the relevance floor cost a prefix re-prefill? (#143)
 *
 * The device measurement in #143 gave the size: ordinary turns prefill 35 to 45 tokens
 * at about 1.5 seconds, and a turn that admits a topical memory hit 444 tokens and 12.5
 * seconds. What it could not give is the frequency, and the deferral rested on the
 * assumption that it is rare, which nobody had checked.
 *
 * **The shape is awkward and worth naming: the user waits longest precisely when memory
 * is being useful**, which is the opposite of what anyone would design. So the trade
 * needs a number rather than an assumption.
 *
 * This is answerable without a device, because which memories are selected is a pure
 * function. A turn takes the expensive path when the selected set differs from the set
 * that would be selected for a message overlapping nothing, since the standing facts do
 * not vary and anything extra changes the block and re-prefills everything behind it.
 *
 * The realistic part is the inputs, and they are not invented here: the messages are the
 * project's own battery and probe sets, which were themselves chosen as the shapes that
 * have broken something before, plus ordinary questions.
 */
class MemoryFloorPrefixCostTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private fun mem(text: String, ageDays: Long) =
        MemoryRetrieval.Item(text, now - ageDays * day)

    /**
     * A plausible store at the cap, which is the case that matters: `MEMORY_LIMIT` is 12,
     * and a user with twelve facts is the one the old no-floor behavior hurt most.
     *
     * The split is deliberate and is the assumption most open to challenge: four standing
     * facts and eight topical ones. Somebody who used memory heavily for preferences
     * would skew standing, and the floor is cheap for them. Somebody who used it for
     * projects and things would skew topical, and pays more often. Four to eight is a
     * middle guess, stated so it can be argued with rather than buried.
     */
    private fun store() = listOf(
        // Standing: present on every turn, never varying, so never a cost.
        mem("name is Kam", 200),
        mem("works as a district nurse", 150),
        mem("lives in Leeds", 140),
        mem("prefers plain language", 90),
        // Topical: each one an occasional cost, on a turn that happens to overlap it.
        mem("is allergic to peanuts", 120),
        mem("has a cat called Mabel", 80),
        mem("is learning Spanish", 60),
        mem("the rowing club is called Verity Quay", 40),
        mem("is renovating the kitchen", 30),
        mem("owns a 2012 Golf", 20),
        mem("is training for a half marathon", 10),
        mem("had a coffee stain on the living room rug", 5),
    )

    /**
     * The messages. Drawn from `mode_battery.sh`, `prefix_probe.sh` and
     * `memory_floor_probe.sh`, plus ordinary questions of the kind the app is for.
     *
     * Not weighted towards memory subjects on purpose. Picking messages that mention
     * stored facts would measure how often a floor fires when provoked, which is already
     * known to be always, and would say nothing about a real conversation.
     */
    private val messages = listOf(
        // The battery's awkward shapes.
        "why",
        "fix",
        "Bread needs a hot oven, around 230C.",
        "i was thinking about that thing and im not sure it holds up what do you reckon",
        "WHY DOES THIS KEEP HAPPENING",
        "u r wrong abt this an i no it",
        "What are you?",
        "You are useless.",
        // The probe turns.
        "What is a good way to keep track of small repairs around the house?",
        "The back gate sticks whenever the wood swells.",
        "What time do most libraries close on a Sunday?",
        "Remind me what we were talking about.",
        // Ordinary use.
        "How do I get a coffee stain out of a rug?",
        "Write a short thank you note for a leaving gift.",
        "What is the difference between baking soda and baking powder?",
        "Summarize this in three bullets.",
        "Help me plan a weekend in the Lake District.",
        "My laptop fan is loud, is that a problem?",
        "What should I ask when viewing a flat?",
        "Explain compound interest simply.",
        "Is it worth switching from metric units to imperial for baking?",
        "Is the rowing club open on Sunday?",
        "How do I keep a cat off the worktop?",
        "What is a good beginner Spanish podcast?",
    )

    private fun selected(query: String) =
        MemoryRetrieval.select(store(), query, now, budgetChars = 1600, max = 12)

    /** What a message overlapping nothing gets: the standing facts alone. */
    private fun standingOnly() = selected("zzzz qqqq vvvv")

    @Test
    fun theProportionOfTurnsTakingTheExpensivePathIsMeasured() {
        val baseline = standingOnly()
        val expensive = messages.filter { selected(it) != baseline }

        val rate = expensive.size * 100.0 / messages.size
        println("=== #143 prefix cost frequency ===")
        println("store: 12 memories, 4 standing, 8 topical")
        println("messages: ${messages.size}")
        println("standing-only block: ${baseline.size} memories")
        println("turns differing from it: ${expensive.size} (${"%.0f".format(rate)}%)")
        expensive.forEach { println("   pays: $it") }

        // Pinned as a range rather than a point, because the store's standing-to-topical
        // split is a judgement and a different one moves this. What the range says is the
        // finding: a minority of ordinary turns, not most of them.
        assertThat(expensive.size).isAtMost(messages.size / 2)
    }

    @Test
    fun theStandingBlockIsTheSameForEveryUnrelatedMessage() {
        // The other half of why the cost is a minority case: the majority path is
        // genuinely identical text turn after turn, so the prefix holds.
        val unrelated = listOf(
            "Write a short thank you note for a leaving gift.",
            "Explain compound interest simply.",
            "Summarize this in three bullets.",
            "What should I ask when viewing a flat?",
        )
        val blocks = unrelated.map { selected(it) }.distinct()
        assertThat(blocks).hasSize(1)
    }

    @Test
    fun aStoreOfOnlyStandingFactsNeverPaysTheCost() {
        // The best case, and it is worth pinning because it is also the common case for
        // somebody who uses memory the way the Memory screen suggests: preferences.
        val standingStore = listOf(
            mem("name is Kam", 100),
            mem("works as a district nurse", 90),
            mem("lives in Leeds", 80),
            mem("prefers plain language", 70),
        )
        val blocks = messages.map {
            MemoryRetrieval.select(standingStore, it, now, 1600, 12)
        }.distinct()
        // One block for every message, so the prefix is never invalidated by memory.
        assertThat(blocks).hasSize(1)
    }
}
