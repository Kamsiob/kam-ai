package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A grounded prompt has to fit the smallest context, whatever a pack contains
 * (#13).
 *
 * The prompt injects the passage whole. Packs are capped by the builder, and a
 * pack is data the app does not control: one built by an older builder, or by
 * anything else, can be any size. An oversized passage does not degrade the
 * answer, it makes the prompt impossible to run.
 */
class GroundedPassageTest {

    @Test
    fun anOrdinaryPassageIsUntouched() {
        // The builder caps at 1,400 words, so the app's backstop must never fire
        // on a pack built correctly, or it would be quietly cutting good packs.
        val passage = List(1400) { "word" }.joinToString(" ")
        assertThat(SystemPrompts.grounded(passage)).contains(passage)
    }

    @Test
    fun anOversizedPassageIsCut() {
        val huge = List(20_000) { "word" }.joinToString(" ")
        val prompt = SystemPrompts.grounded(huge)
        assertThat(prompt.length).isLessThan(huge.length)
    }

    @Test
    fun theCutLandsOnASentenceEnd() {
        val sentences = List(4000) { "This is a sentence about the subject." }.joinToString(" ")
        val prompt = SystemPrompts.grounded(sentences)
        val passagePart = prompt.substringAfter("The passage:\n\n")
        assertThat(passagePart.trimEnd()).endsWith(".")
    }

    @Test
    fun theCutIsNotExplainedToTheModel() {
        // It should discuss what it was given, not report that it was given less
        // than existed, which would read as the app apologising for its own data.
        val huge = List(20_000) { "word" }.joinToString(" ")
        val prompt = SystemPrompts.grounded(huge).lowercase()
        listOf("truncated", "shortened", "cut off", "rest of the article").forEach {
            assertThat(prompt).doesNotContain(it)
        }
    }
}
