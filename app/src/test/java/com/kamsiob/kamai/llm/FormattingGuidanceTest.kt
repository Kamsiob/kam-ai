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

    private val marker = "Match the format to the content"
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
