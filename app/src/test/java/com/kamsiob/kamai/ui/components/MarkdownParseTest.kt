package com.kamsiob.kamai.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Guards the Markdown block parser used to render assistant responses (item 14). */
class MarkdownParseTest {

    @Test
    fun `plain text is one paragraph`() {
        val b = parseMarkdown("The capital of Japan is Tokyo.")
        assertThat(b).hasSize(1)
        assertThat(b[0]).isInstanceOf(MdBlock.Paragraph::class.java)
        assertThat((b[0] as MdBlock.Paragraph).text).isEqualTo("The capital of Japan is Tokyo.")
    }

    @Test
    fun `headings capture level and text`() {
        val b = parseMarkdown("## Overview\nbody")
        assertThat(b[0]).isEqualTo(MdBlock.Heading(2, "Overview"))
        assertThat(b[1]).isEqualTo(MdBlock.Paragraph("body"))
    }

    @Test
    fun `consecutive bullets group into one list`() {
        val b = parseMarkdown("- one\n- two\n- three")
        assertThat(b).hasSize(1)
        assertThat(b[0]).isEqualTo(MdBlock.Bullet(listOf("one", "two", "three")))
    }

    @Test
    fun `numbered list keeps its start`() {
        val b = parseMarkdown("1. first\n2. second")
        assertThat(b[0]).isEqualTo(MdBlock.Numbered(listOf("first", "second"), 1))
    }

    @Test
    fun `fenced code keeps content and language`() {
        val b = parseMarkdown("```kotlin\nval x = 1\n```")
        assertThat(b[0]).isEqualTo(MdBlock.Code("val x = 1", "kotlin"))
    }

    @Test
    fun `an unterminated code fence mid-stream still renders as a code block`() {
        // As tokens arrive, the closing fence may not exist yet. It must not break.
        val b = parseMarkdown("```\nstill streaming")
        assertThat(b).hasSize(1)
        assertThat(b[0]).isInstanceOf(MdBlock.Code::class.java)
        assertThat((b[0] as MdBlock.Code).code).isEqualTo("still streaming")
    }

    @Test
    fun `blank line separates paragraphs`() {
        val b = parseMarkdown("First para.\n\nSecond para.")
        assertThat(b).containsExactly(
            MdBlock.Paragraph("First para."),
            MdBlock.Paragraph("Second para."),
        ).inOrder()
    }

    @Test
    fun `a quote is captured`() {
        val b = parseMarkdown("> to be or not to be")
        assertThat(b[0]).isEqualTo(MdBlock.Quote("to be or not to be"))
    }
}
