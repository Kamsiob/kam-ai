package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import org.junit.Test

/**
 * Guards item 14 part two: every mode's system instructions must carry the
 * formatting guidance (match structure to content, and do not over-format), since
 * a small model will not shape its answers well without being told to.
 */
class FormattingGuidanceTest {

    // The wording has moved twice. #38 shortened "match the format to the content"
    // to "match the length to the question", and #91 replaced the whole described
    // rule with four worked examples, because a described shape is exactly what
    // the model was failing to follow. The guard is on the guidance being present
    // in every mode, not on any one sentence, so the markers move with it.
    private val marker = "match the shape of the answer to what was asked"

    // The anti-over-formatting half matters as much as the rest: swinging from
    // walls of text to bullets everywhere would be a worse outcome than the
    // original bug.
    // Moved when the rules became a shape rather than a list of "never" clauses
    // (#126). The guard is on the guidance being present, not on any one
    // sentence, so the marker moves with it.
    private val antiOverFormat = "no heading and no list"

    @Test
    fun `every mode prompt carries the formatting guidance`() {
        for (mode in Mode.entries) {
            val prompt = SystemPrompts.forMode(mode)
            assertThat(prompt).contains(marker)
            assertThat(prompt).contains(antiOverFormat)
        }
    }

    @Test
    fun `the grounded Discover prompt carries it too`() {
        val prompt = SystemPrompts.grounded("some passage text")
        assertThat(prompt).contains(marker)
        assertThat(prompt).contains(antiOverFormat)
    }
}

/**
 * The formatting guidance shows the shape rather than describing it (#91).
 *
 * The bug was precise: a tested answer produced "User Experience and Interface
 * Design." as an ordinary sentence, which is a heading written as prose. The
 * model was organizing its thinking and not emitting the syntax, because the
 * rules described the shape in words. A small model follows a demonstrated shape
 * far more reliably than a described rule.
 *
 * These pin the demonstration, because "describe it more clearly" is the natural
 * thing for a future edit to do and is exactly what did not work.
 */
class FormattingExamplesTest {

    private val prompt = SystemPrompts.forMode(com.kamsiob.kamai.data.Mode.GENERAL)

    /**
     * The prompt with its line wrapping flattened.
     *
     * Assertions are about what the model reads, and the model reads one string.
     * Matching the source's line breaks would make a reflow look like a deleted
     * rule, which is a test failing for a reason nobody cares about.
     */
    private val flat = prompt.replace(Regex("\\s+"), " ")

    @Test
    fun `a one-line question is shown answered in one line, with no structure`() {
        // The answer shape is what #91 needed the model to see. The example used
        // to quote a question as well, and that quoted question was found on the
        // device to be generatable: given a user message that was not a question,
        // the model produced a new question in the same style and then answered it
        // on the following turn. The shape is kept; the quoted user turn is not.
        assertThat(prompt).contains("A plain fact:")
        assertThat(prompt).contains("It was finished in 1889")
    }

    @Test
    fun `steps are shown as a numbered list`() {
        assertThat(prompt).contains("Steps in a required order:")
        assertThat(prompt).contains("1. Hold the side button")
    }

    @Test
    fun `options are shown as bullets, not numbers`() {
        // A model will otherwise number unordered things and imply a sequence.
        assertThat(prompt).contains("Alternatives with no order:")
        assertThat(prompt).contains("- An external drive")
    }

    @Test
    fun `a multi-part question is shown with real heading syntax`() {
        // The whole point: the markdown appears in the example, so the model has
        // seen the characters rather than a description of them.
        assertThat(prompt).contains("## Cost")
        assertThat(prompt).contains("## What to watch")
    }

    @Test
    fun `the triggers are conditions, never the word appropriate`() {
        assertThat(prompt).contains("Numbers only when order matters")
        assertThat(prompt).contains("bullets otherwise")
        assertThat(prompt).contains("long enough to scan")
        // "Appropriate" is the word that makes a rule unfollowable.
        assertThat(prompt.lowercase()).doesNotContain("as appropriate")
        assertThat(prompt.lowercase()).doesNotContain("where appropriate")
    }

    @Test
    fun `the anti-over-formatting rules survived the change`() {
        // Swinging from walls of text to bullets everywhere would be worse than
        // the original bug, and bullets everywhere is the clearest sign of
        // generic machine output.
        // Restated as a shape for #126: what an answer starts with, what it ends
        // with, and what a short one looks like. Same four prohibitions, in the
        // form this model has repeatedly been shown to follow.
        listOf(
            "An answer starts with the answer",
            "no version of the question repeated back",
            "no summary of what was just said",
            "A short answer is one or two sentences",
            "no heading and no list",
        ).forEach { assertThat(flat).contains(it) }
    }
}
