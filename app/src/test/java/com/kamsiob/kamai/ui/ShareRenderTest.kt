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
}
