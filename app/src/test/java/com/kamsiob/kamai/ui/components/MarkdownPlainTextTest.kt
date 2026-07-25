package com.kamsiob.kamai.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What lands on the clipboard when somebody copies an answer (#39).
 *
 * Copy, share, and the plain-text export all handed over the Markdown source, so
 * an answer that read cleanly on screen arrived in somebody's notes as
 * `### Heading` and `**bold**`. The rule these tests hold to is that what is
 * copied is what was read.
 */
class MarkdownPlainTextTest {

    @Test
    fun `emphasis markers go and the words stay`() {
        assertThat(markdownToPlainText("This is **important** and *this* matters"))
            .isEqualTo("This is important and this matters")
    }

    @Test
    fun `heading markers go`() {
        assertThat(markdownToPlainText("### Three reasons")).isEqualTo("Three reasons")
    }

    @Test
    fun `inline code keeps its text without the backticks`() {
        assertThat(markdownToPlainText("Call `parseMarkdown` first"))
            .isEqualTo("Call parseMarkdown first")
    }

    @Test
    fun `bullets stay bullets`() {
        // A list without its markers stops being a list.
        val out = markdownToPlainText("- first\n- second\n- third")
        assertThat(out).isEqualTo("- first\n- second\n- third")
    }

    @Test
    fun `numbered lists keep counting from where they started`() {
        assertThat(markdownToPlainText("3. third\n4. fourth"))
            .isEqualTo("3. third\n4. fourth")
    }

    @Test
    fun `emphasis inside a list item still goes`() {
        assertThat(markdownToPlainText("- the **first** one")).isEqualTo("- the first one")
    }

    @Test
    fun `a code block keeps its contents exactly and loses its fence`() {
        // Whitespace is part of code, so nothing here may be trimmed or reflowed.
        val out = markdownToPlainText("```kotlin\nfun main() {\n    println(1)\n}\n```")
        assertThat(out).isEqualTo("fun main() {\n    println(1)\n}")
    }

    @Test
    fun `asterisks that never close are left alone`() {
        // Exactly what the screen does: an unterminated marker is literal text,
        // which is what keeps a response readable while it is still arriving.
        assertThat(markdownToPlainText("2 * 3 is 6")).isEqualTo("2 * 3 is 6")
        assertThat(markdownToPlainText("half a **bold")).isEqualTo("half a **bold")
    }

    @Test
    fun `underscores are never touched`() {
        // snake_case and file_names survive, matching the renderer's own rule.
        assertThat(markdownToPlainText("the file_name_here stays"))
            .isEqualTo("the file_name_here stays")
    }

    @Test
    fun `blocks are separated by a blank line`() {
        val out = markdownToPlainText("## Title\n\nA paragraph.\n\n- a point")
        assertThat(out).isEqualTo("Title\n\nA paragraph.\n\n- a point")
    }

    @Test
    fun `plain text with no Markdown in it comes back unchanged`() {
        val plain = "The Nile and the Amazon are two rivers."
        assertThat(markdownToPlainText(plain)).isEqualTo(plain)
    }

    @Test
    fun `nothing in comes back as nothing out`() {
        assertThat(markdownToPlainText("")).isEmpty()
    }
}
