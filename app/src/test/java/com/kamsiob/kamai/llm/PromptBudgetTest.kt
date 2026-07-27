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
        // Measured ~450 after the #38 trim and ~689 after #91 added four worked
        // formatting examples in place of a described rule. Fail if it drifts past
        // 700, which is the deliberate ceiling recorded in the budget map below.
        assertTrue("General system prompt bloated to ~$tokens tokens", tokens < 700)
    }

    @Test
    fun everyModePromptIsBounded() {
        // Logic and Brainstorm carry a method on top of the hard rules, so they get
        // more room, but none may run away. Overlay and Discover stay small.
        // Budgets are the estimator's chars/3.6, which overshoots the real
        // tokenizer by ~15 percent, set just above the trimmed sizes so creeping
        // bloat fails but the current prompts pass.
        // Every budget here rose for #91, in one deliberate step, and the
        // reasoning is the same for all of them because the cost lands in the
        // shared hard rules. Measured after the change: GENERAL 689, LOGIC 1247,
        // BRAINSTORM 1808, BENCH 727, OVERLAY 666, DISCOVER 801. Each budget sits
        // just above its measurement, per this file's own convention.
        //
        // Responses used no headings or lists at all. The diagnosis was precise: a
        // tested answer produced "User Experience and Interface Design." as an
        // ordinary sentence, which is a heading written as prose. The model was
        // organizing its thinking and not emitting the syntax, because the rules
        // described the shape in words instead of showing it. Four worked
        // examples replaced that description and cost about seventy estimated
        // tokens net in GENERAL, after the old described paragraph came out.
        //
        // What pays for it: the system prompt is prefilled once per conversation
        // and then reused from the KV cache (#52), so this is eighty tokens at the
        // start of a session rather than eighty per turn. It is also the single
        // most visible quality problem in the app, which no amount of token
        // discipline elsewhere makes up for.
        val budgets = mapOf(
            Mode.GENERAL to 700,
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
            // Raised from 1260 to 1340, deliberately, after a device failure that
            // made the mode useless. Asked to argue against "we should skip
            // automated tests to ship faster", Logic Partner replied with exactly
            // five words: "I disagree with the warrant."
            //
            // That string was in the prompt. It sat there as a negative example,
            // in quotes, under an instruction never to do it: `"I disagree with the
            // warrant" is a label, not an objection`. The model copied it. A small
            // model treats a quoted phrase as a template no matter what the
            // sentence around it says, which is the same finding as #91 in reverse:
            // worked examples beat described rules, so a bad worked example beats
            // the rule forbidding it. The jargon leaked for the same reason, since
            // warrant, grounds and qualifier were all in the text the model reads.
            //
            // The rewrite removes every quotable bad line, bans the jargon words in
            // output explicitly, states three ordered moves, and shows one worked
            // example of a good reply instead. That example is most of the eighty
            // tokens. Everything trimmable was trimmed first: 1548 down to 1328.
            //
            // The cost is about two seconds of prefill on the first turn of a Logic
            // conversation, amortised by prefix reuse afterwards. The alternative
            // was a flagship mode that answers in five words of jargon, which is
            // not a trade worth protecting time to first token for.
            Mode.LOGIC to 1340,
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
            Mode.BRAINSTORM to 1820,
            Mode.BENCH to 740,
            Mode.OVERLAY to 680,
            Mode.DISCOVER to 820,
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
        // changes behavior rather than only a label.
        val prompts = Mode.entries.map { SystemPrompts.forMode(it) }
        assertEquals(prompts.size, prompts.distinct().size)
    }
}
