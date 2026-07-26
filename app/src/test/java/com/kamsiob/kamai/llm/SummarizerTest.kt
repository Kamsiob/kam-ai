package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What gets summarized, and what gets refused (#86).
 *
 * The two cases the spec asks to be handled rather than ignored are both here: a
 * conversation too short to be worth summarizing, and one too long to fit, which
 * has to be covered honestly rather than silently truncated to whatever happened
 * to fit in the window.
 */
class SummarizerTest {

    private fun longMessage(chars: Int) = "x".repeat(chars)

    private val realConversation = listOf(
        "What is a roux?",
        "Flour and fat cooked together, used to thicken a sauce. " + longMessage(300),
        "How long do I cook it?",
        "Until it smells nutty. " + longMessage(300),
    )

    @Test
    fun `a two-exchange conversation is short enough to read`() {
        val plan = Summarizer.plan(listOf("hi", "hello"), budgetChars = 10_000)
        assertThat(plan).isInstanceOf(Summarizer.Plan.TooShort::class.java)
        // Says why, rather than refusing. Nobody should wonder if it broke.
        assertThat((plan as Summarizer.Plan.TooShort).message).contains("short enough to read")
    }

    @Test
    fun `many but tiny messages are still short enough to read`() {
        // Ten one-word messages are not a conversation worth summarizing.
        val plan = Summarizer.plan(List(10) { "ok" }, budgetChars = 10_000)
        assertThat(plan).isInstanceOf(Summarizer.Plan.TooShort::class.java)
    }

    @Test
    fun `a real conversation that fits is summarized in one pass`() {
        val plan = Summarizer.plan(realConversation, budgetChars = 10_000)
        assertThat(plan).isInstanceOf(Summarizer.Plan.Whole::class.java)
        assertThat((plan as Summarizer.Plan.Whole).text).contains("What is a roux?")
        assertThat(plan.text).contains("Until it smells nutty")
    }

    @Test
    fun `a conversation longer than the window is sectioned, not truncated`() {
        // The failure this exists to prevent: summarizing only the part that fit
        // and presenting it as a summary of the whole thing.
        val long = List(20) { "Message $it. " + longMessage(400) }
        val plan = Summarizer.plan(long, budgetChars = 1_000)
        assertThat(plan).isInstanceOf(Summarizer.Plan.Sectioned::class.java)
        val sections = (plan as Summarizer.Plan.Sectioned).sections
        assertThat(sections.size).isAtLeast(2)
        // Nothing is dropped: the first and last messages both survive somewhere.
        assertThat(sections.first()).contains("Message 0")
        assertThat(sections.last()).contains("Message 19")
    }

    @Test
    fun `sections stay within the budget`() {
        val long = List(20) { "Message $it. " + longMessage(400) }
        val sections = (Summarizer.plan(long, budgetChars = 1_000)
            as Summarizer.Plan.Sectioned).sections
        sections.forEach { assertThat(it.length).isAtMost(1_000) }
    }

    @Test
    fun `sections never split a message in half`() {
        // Half an answer summarized alone is a section about nothing.
        // Four, because three is below the "short enough to read" floor and
        // would be refused before any sectioning happened.
        val messages = listOf(
            "A" + longMessage(300), "B" + longMessage(300),
            "C" + longMessage(300), "D" + longMessage(300),
        )
        val plan = Summarizer.plan(messages, budgetChars = 700)
        val sections = (plan as Summarizer.Plan.Sectioned).sections

        // Every piece of every section is one of the original messages, whole.
        val pieces = sections.flatMap { it.split("\n\n") }
        pieces.forEach { assertThat(messages).contains(it) }
        // And every message appears exactly once across all of them.
        assertThat(pieces).containsExactlyElementsIn(messages)
    }

    @Test
    fun `a single message larger than the whole budget is capped rather than dropped`() {
        val plan = Summarizer.plan(
            listOf(longMessage(5000), "a", "b", "c", longMessage(700)),
            budgetChars = 1_000,
        )
        assertThat(plan).isInstanceOf(Summarizer.Plan.Sectioned::class.java)
        val sections = (plan as Summarizer.Plan.Sectioned).sections
        assertThat(sections).isNotEmpty()
        sections.forEach { assertThat(it.length).isAtMost(1_000) }
    }

    @Test
    fun `blank messages are ignored rather than counted`() {
        // System notes and empty rows should not make a short chat look long.
        val plan = Summarizer.plan(listOf("hi", "", "   ", "hello"), budgetChars = 10_000)
        assertThat(plan).isInstanceOf(Summarizer.Plan.TooShort::class.java)
    }

    @Test
    fun `a whole-pass summary says it is a reading, not a record`() {
        val line = Summarizer.provenance(Summarizer.Plan.Whole("x"))
        assertThat(line).contains("reading of this conversation")
        assertThat(line).contains("not a record")
    }

    @Test
    fun `a sectioned summary says how it was made`() {
        // Summarizing in pieces and combining loses more than one pass would, and
        // the person reading it should be told rather than given a clean story.
        val line = Summarizer.provenance(Summarizer.Plan.Sectioned(listOf("a", "b")), sections = 2)
        assertThat(line).contains("longer than the model can hold")
        assertThat(line).contains("2 parts")
    }

    @Test
    fun `the instructions never ask for advice or a closing question`() {
        val flat = { s: String -> s.replace(Regex("\\s+"), " ") }
        listOf(Summarizer.WHOLE_INSTRUCTION, Summarizer.COMBINE_INSTRUCTION).forEach {
            assertThat(flat(it)).contains("not add anything")
        }
        assertThat(flat(Summarizer.WHOLE_INSTRUCTION)).contains("do not end with a question")
    }

    @Test
    fun `the instruction states the length the token cap enforces`() {
        // A cap the model does not know about produces a summary cut off mid
        // sentence, which is worse than a long one. 200 tokens is roughly 150
        // words, so the two numbers have to move together (#90).
        assertThat(Summarizer.WHOLE_INSTRUCTION).contains("150 words")
    }
}
