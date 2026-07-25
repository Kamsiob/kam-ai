package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * The plain-text rendering behind sharing and exporting a whole thread. PART 5B.
 * The output has to read cleanly, with each turn attributed, and no control
 * tokens or clutter.
 */
class ShareRenderTest {

    private fun msg(role: Role, content: String, at: Long) =
        MessageEntity("m$at", "c1", role, content, at)

    private val thread = listOf(
        msg(Role.USER, "why do lighthouses exist", 1),
        msg(Role.ASSISTANT, "To guide ships safely past dangerous coasts.", 2),
        msg(Role.USER, "when did they start", 3),
        msg(Role.ASSISTANT, "The idea is ancient, going back to signal fires.", 4),
    )

    @Test
    fun aThreadRendersAsAttributedReadableText() {
        val text = Share.renderThread("Lighthouses", thread)

        assertThat(text).startsWith("Lighthouses")
        assertThat(text).contains("You: why do lighthouses exist")
        assertThat(text).contains("Kam AI: To guide ships safely past dangerous coasts.")
        // Every turn is present and attributed.
        assertThat(text.split("You:").size - 1).isEqualTo(2)
        assertThat(text.split("Kam AI:").size - 1).isEqualTo(2)
        // No trailing whitespace pile-up.
        assertThat(text).isEqualTo(text.trim())
    }

    @Test
    fun thePlainTextExportIsActuallyPlainText() {
        // Choosing plain text over Markdown used to change the file extension and
        // nothing else: the Markdown source went out either way (#39).
        val formatted = listOf(
            msg(Role.USER, "summarize it", 1),
            msg(Role.ASSISTANT, "## Two reasons\n\n- it is **cheap**\n- it is `fast`", 2),
        )
        val text = Share.renderThread("Reasons", formatted)

        assertThat(text).contains("Kam AI: Two reasons")
        assertThat(text).contains("- it is cheap")
        assertThat(text).contains("- it is fast")
        assertThat(text).doesNotContain("##")
        assertThat(text).doesNotContain("**")
        assertThat(text).doesNotContain("`")
    }

    @Test
    fun theMarkdownExportKeepsTheMarkdown() {
        // The other half of the same choice. This one is supposed to be source.
        val formatted = listOf(msg(Role.ASSISTANT, "it is **cheap**", 1))
        assertThat(Share.renderThreadMarkdown("Reasons", formatted)).contains("it is **cheap**")
    }

    @Test
    fun aUsersOwnAsterisksAreNeverEaten() {
        // Only the assistant writes Markdown. Somebody typing 2 * 3 gets 2 * 3.
        val typed = listOf(msg(Role.USER, "is 2 * 3 * 4 the same as 4 * 3 * 2", 1))
        assertThat(Share.renderThread(null, typed)).contains("You: is 2 * 3 * 4 the same as 4 * 3 * 2")
    }

    @Test
    fun anUntitledThreadStillGetsAHeading() {
        val text = Share.renderThread(null, thread.take(2))
        assertThat(text).startsWith("Kam AI conversation")
    }

    @Test
    fun contentIsTrimmedSoAStreamedResponseDoesNotCarryStrayWhitespace() {
        val messy = listOf(msg(Role.ASSISTANT, "  a padded answer  \n", 1))
        val text = Share.renderThread("t", messy)
        assertThat(text).contains("Kam AI: a padded answer")
        assertThat(text).doesNotContain("a padded answer  ")
    }

    // Issue #41. A SYSTEM entry is a mode-change notice or the Discover
    // continue-in-open-chat note: something that happened to the conversation,
    // not something anybody said.

    private val noticeText = "Logic Partner is on. Kam AI will argue the other side."

    private val threadWithNotice = listOf(
        msg(Role.USER, "is this plan sound", 1),
        msg(Role.ASSISTANT, "Mostly, with one gap.", 2),
        msg(Role.SYSTEM, noticeText, 3),
        msg(Role.USER, "go on then", 4),
    )

    @Test
    fun aModeNoticeIsNeverAttributedToTheAssistant() {
        val text = Share.renderThread("Plan", threadWithNotice)
        assertThat(text).doesNotContain("Kam AI: $noticeText")
        // Only the one real answer is attributed to it.
        assertThat(text.split("Kam AI:").size - 1).isEqualTo(1)
    }

    @Test
    fun aModeNoticeIsStillExportedRatherThanDropped() {
        // The four-mode update requires an export to show where the mode changed.
        val text = Share.renderThread("Plan", threadWithNotice)
        assertThat(text).contains(noticeText)
        assertThat(text).contains("[ $noticeText ]")
    }

    @Test
    fun aModeNoticeIsAnAsideInMarkdownToo() {
        val md = Share.renderThreadMarkdown("Plan", threadWithNotice)
        assertThat(md).contains("_${noticeText}_")
        assertThat(md).doesNotContain("**Kam AI**\n\n$noticeText")
        assertThat(md.split("**Kam AI**").size - 1).isEqualTo(1)
    }

    @Test
    fun aSharedThreadKeepsItsOwnTitle() {
        // It used to head "Kam AI conversation" whatever the conversation was
        // called, because the share path passed null.
        val text = Share.renderThread("Lighthouses", thread)
        assertThat(text).startsWith("Lighthouses")
        assertThat(text).doesNotContain("Kam AI conversation")
    }

    @Test
    fun theExportNameIsTheTitleWhenThereIsOne() {
        assertThat(Share.exportName("Lighthouses", threadWithNotice)).isEqualTo("Lighthouses")
    }

    @Test
    fun anUntitledExportIsNamedAfterTheFirstRealMessageNotANotice() {
        // A conversation whose mode was switched at the very top used to export
        // as a file named after the mode-change notice.
        val noticeFirst = listOf(
            msg(Role.SYSTEM, noticeText, 1),
            msg(Role.USER, "is this plan sound", 2),
        )
        assertThat(Share.exportName(null, noticeFirst)).isEqualTo("is this plan sound")
    }

    @Test
    fun aBlankTitleFallsBackRatherThanNamingTheFileNothing() {
        assertThat(Share.exportName("   ", threadWithNotice)).isEqualTo("is this plan sound")
    }

    @Test
    fun anEmptyThreadHasNoExportNameToOffer() {
        assertThat(Share.exportName(null, emptyList())).isNull()
    }
}
