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
            // Raised from 1000 to 1080 for #57, deliberately and not quietly. The
            // argument-analysis method (claim, grounds, warrant, qualifier, claim
            // kind, then the crux and the kind of disagreement) cannot be added for
            // free. It was written as compactly as it can be said and still came to
            // ~1052, down from ~1156 on the first draft.
            //
            // What paid for it: the titling fix measured the same day took the warm
            // turn from re-prefilling 1068 tokens to 36. Fifty-odd estimated tokens
            // of system prompt, once per conversation and amortised by prefix reuse
            // after that, against a thousand saved on every ongoing turn. The budget
            // exists to protect time to first token, and that trade improves it by
            // a wide margin. See DECISIONS.md, "Issue #57".
            Mode.LOGIC to 1080,
            // Raised from 1600 to 1660 for #58, deliberately and not quietly, and
            // after trimming everything that could be trimmed. Two device-found
            // failures paid for it. The mode announced its own method ("Only a
            // topic, no idea yet, we will use STARBURSTING"), which is the model
            // reading its instructions to the user; and it answered somebody
            // calling their own ideas stupid by making their embarrassment the
            // subject rather than running the method for exactly that case.
            //
            // What pays for it: the system prompt is prefilled once per
            // conversation and then reused from the KV cache, so this is sixty
            // estimated tokens at the start of a session, not sixty per turn. The
            // same trade recorded for LOGIC above, and the budget exists to
            // protect time to first token, which prefix reuse protects far more.
            Mode.BRAINSTORM to 1660,
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
