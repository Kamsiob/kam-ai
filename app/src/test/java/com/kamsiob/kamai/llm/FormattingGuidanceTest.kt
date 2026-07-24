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

    // The #38 trim reworded the first half of this guidance from "match the format
    // to the content" to "match the length to the question", which says the same
    // thing in fewer tokens. The guard is on the guidance being present in every
    // mode, not on the exact sentence, so the marker moved with it.
    private val marker = "match the length to the question"
    private val antiOverFormat = "Do not over-format"

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
