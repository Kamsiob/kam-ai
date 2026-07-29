package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards the bug class that broke three modes in one evening: concrete text
 * inside a prompt is text the model may emit.
 *
 * Logic Partner replied "I disagree with the warrant." because that string sat in
 * its prompt as a negative example. Brainstorm announced "I'll use STARBURSTING"
 * because the selection rule and the capitalised label were both in front of it.
 * General answered "What's the best way to clean a microwave?" to a message that
 * was not a question, because the formatting examples quoted user questions and it
 * completed the nearest pattern it could see.
 *
 * The lesson is not that examples are bad. #91 established that a worked example
 * beats a described rule for a small model, and removing the examples made things
 * worse every time it was tried. The lesson is that an example teaches its content
 * as well as its shape, so every quoted line in a prompt has to be a line we would
 * be content to see in a reply.
 *
 * So this does not ban quoted text. It pins the quoted text to a list, so adding a
 * new one is a deliberate act with a reason, rather than something that slips in
 * while rewording a paragraph and is discovered on a phone three weeks later.
 */
class PromptQuotableTextTest {

    /**
     * Every quoted string currently allowed inside a prompt body, with why.
     *
     * Each of these was verified on the device to produce sensible output on an
     * input unrelated to the example, which is the only test that means anything.
     * Testing a mode against its own example only proves the example is reachable.
     */
    private val allowed = mapOf(
        "Remember that I always take the stairs." to
            "The input half of the acknowledgement example. Fixes #116, where a " +
                "statement made the model invent a question and answer it.",
        "The install failed again, third time today." to
            "The input half of the respond-to-substance example, same fix.",
        "fix" to
            "The input half of the too-vague example. Safe to copy verbatim by " +
                "design: its answer is a clarifying question, which is the right " +
                "reply to any message too short to act on.",
        "Fix what? Tell me what is broken and I will start there." to
            "The answer half of the same example, and the reason this one is safe " +
                "where the others needed care. If the model emits it word for word " +
                "at an ambiguous message, the output is still correct.",
        "we should skip automated tests to ship faster" to
            "The topic of Logic Partner's worked argument. Fixes #114.",
    )

    private fun promptSource(): String =
        File("../app/src/main/java/com/kamsiob/kamai/llm/SystemPrompts.kt")
            .takeIf { it.exists() }
            ?.readText()
            ?: File("app/src/main/java/com/kamsiob/kamai/llm/SystemPrompts.kt").readText()

    @Test
    fun everyQuotedLineInAPromptIsOneWeChose() {
        val source = promptSource()
        val bodies = Regex(
            """(?:private )?val (HARD_RULES|GENERAL|LOGIC|BRAINSTORM|BENCH|OVERLAY|DISCOVER_GROUNDED) = \"\"\"(.*?)\"\"\"\.trimIndent\(\)""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(source)

        val found = mutableListOf<String>()
        bodies.forEach { m ->
            // Newlines are allowed inside the quotes on purpose. This used to be
            // [^"\n], which cannot match a quoted sentence that wraps across two
            // source lines, and that is precisely how the worst instance got in:
            // a paragraph warning against supplying a quotable identity sentence,
            // which quoted the sentence, wrapped, and so was invisible here. The
            // model copied it and answered "I am Kam AI." to a bereavement
            // message (#119).
            // Every quoted span is paired first and filtered by length after,
            // rather than requiring a length inside the pattern. With a minimum
            // in the pattern, a short quote like "fix" cannot match, so the
            // engine pairs its closing quote with the next opening one and
            // reports a span of the prose in between as though it were quoted.
            Regex("\"([^\"]*)\"").findAll(m.groupValues[2])
                .map { it.groupValues[1].replace(Regex("\\s+"), " ").trim() }
                .filter { it.length >= 10 }
                .forEach { found += it }
        }

        val unexpected = found.filterNot { it in allowed.keys }
        assertThat(unexpected).isEmpty()
    }

    @Test
    fun theAcknowledgementExamplesAreStillThere() {
        // The fix for #116 is these examples existing. A later trim that removed
        // them to save tokens would reintroduce the defect, and the token budget
        // is the exact pressure that would motivate it.
        val source = promptSource()
        assertThat(source).contains("Noted, I will assume the stairs rather than the lift.")
        assertThat(source).contains("Always respond to the message the user actually sent")
    }

    @Test
    fun noPromptNamesABrainstormMethodInCapitals() {
        // Brainstorm announced its method because eleven capitalised labels sat in
        // its prompt. A prompt cannot forbid a shape it demonstrates.
        val source = promptSource()
        listOf("STARBURSTING", "SCAMPER", "CRAZY EIGHTS", "SIX THINKING HATS",
               "BRAIN DUMP", "HUB AND SPOKE", "REVERSE BRAINSTORMING").forEach {
            assertThat(source).doesNotContain(it)
        }
    }
}
