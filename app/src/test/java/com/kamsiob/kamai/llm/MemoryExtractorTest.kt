package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The memory logic. PART 7. Manual capture must be predictable, and the auto
 * parse must keep only high-signal facts and never a refusal or a NONE.
 */
class MemoryExtractorTest {

    @Test
    fun aPlainRememberRequestIsCaptured() {
        assertThat(MemoryExtractor.manualFact("remember that I take my coffee black"))
            .isEqualTo("I take my coffee black")
        assertThat(MemoryExtractor.manualFact("Remember: I live in Cairo"))
            .isEqualTo("I live in Cairo")
        assertThat(MemoryExtractor.manualFact("please remember I prefer short answers"))
            .isEqualTo("I prefer short answers")
    }

    @Test
    fun anOrdinaryMessageIsNotAMemory() {
        assertThat(MemoryExtractor.manualFact("what is the capital of France")).isNull()
        assertThat(MemoryExtractor.manualFact("can you remember things?")).isNull()
        assertThat(MemoryExtractor.manualFact("")).isNull()
    }

    @Test
    fun theAutoParseKeepsRealFactsOnePerLine() {
        val reply = "prefers plain language\nis learning Spanish"
        assertThat(MemoryExtractor.parseAutoReply(reply))
            .containsExactly("prefers plain language", "is learning Spanish")
    }

    @Test
    fun theAutoParseTreatsNoneAsNothing() {
        assertThat(MemoryExtractor.parseAutoReply("NONE")).isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("none")).isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("  NONE  ")).isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("")).isEmpty()
    }

    @Test
    fun theAutoParseDropsRefusalsAndStripsBullets() {
        assertThat(MemoryExtractor.parseAutoReply("- likes tea\n* works nights"))
            .containsExactly("likes tea", "works nights")
        assertThat(MemoryExtractor.parseAutoReply("There is nothing worth keeping here."))
            .isEmpty()
        assertThat(MemoryExtractor.parseAutoReply("no durable facts in this exchange"))
            .isEmpty()
    }

    @Test
    fun theAutoParseIsBoundedSoOneExchangeCannotFloodTheStore() {
        val many = (1..10).joinToString("\n") { "fact number $it" }
        assertThat(MemoryExtractor.parseAutoReply(many)).hasSize(2)
    }

    @Test
    fun modesAreOrderedManualDefaultFirst() {
        // Manual is the safe, predictable default named in the spec.
        assertThat(MemoryMode.entries.first()).isEqualTo(MemoryMode.MANUAL)
    }
}
