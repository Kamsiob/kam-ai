package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Performance regression guard for issue #38. Time to first token is dominated by
 * prefill, and prefill is linear in the prompt's token count, so the fixed system
 * prompts must stay tight. These budgets are deliberately a little above the
 * current sizes; a change that blows past one is either real bloat to reconsider
 * or a budget to raise on purpose, not something to slip in silently.
 *
 * Pure JVM (no Robolectric), so it actually runs on the build machine.
 */
class PromptBudgetTest {

    // ~3.6 chars per token, matching the app's own estimate.
    private fun approxTokens(s: String) = (s.length / 3.6).toInt()

    @Test
    fun generalPromptStaysTight() {
        val tokens = approxTokens(SystemPrompts.forMode(Mode.GENERAL))
        // Measured ~450 tokens after the #38 trim; fail if it drifts past 600.
        assertTrue("General system prompt bloated to ~$tokens tokens", tokens < 600)
    }

    @Test
    fun everyModePromptIsBounded() {
        // Logic and Brainstorm carry a method on top of the hard rules, so they get
        // more room, but none may run away. Overlay and Discover stay small.
        // Budgets are the estimator's chars/3.6, which overshoots the real
        // tokenizer by ~15 percent, set just above the trimmed sizes so creeping
        // bloat fails but the current prompts pass.
        val budgets = mapOf(
            Mode.GENERAL to 620,
            Mode.LOGIC to 1000,
            Mode.BRAINSTORM to 1600,
            Mode.BENCH to 660,
            Mode.OVERLAY to 600,
            Mode.DISCOVER to 750,
        )
        budgets.forEach { (mode, budget) ->
            val tokens = approxTokens(SystemPrompts.forMode(mode))
            assertTrue("$mode system prompt is ~$tokens tokens, over budget $budget", tokens < budget)
        }
    }

    @Test
    fun injectedDateHasNoTimeComponent() {
        // A minute-precise date would change every turn and, sitting before the
        // history, would break KV-cache prefix reuse. The wording must not ask for
        // a time. Guard the phrasing that composes the date line.
        val line = SystemPrompts.withDate("BASE", "Friday, 24 July 2026")
        assertTrue(line.contains("Today is Friday, 24 July 2026"))
        assertTrue("date instruction should not mention time", !line.contains("time"))
    }

    @Test
    fun everyModeHasDistinctInstructions() {
        // A quick sanity check that the four modes really differ, so a mode switch
        // changes behaviour rather than only a label.
        val prompts = Mode.entries.map { SystemPrompts.forMode(it) }
        assertEquals(prompts.size, prompts.distinct().size)
    }
}
