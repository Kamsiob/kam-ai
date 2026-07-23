package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * Guards item 17's title-quality rules: generic filler is rejected in favour of an
 * honest excerpt of the first question, and cleaning strips quotes, markdown, and
 * trailing punctuation.
 */
class ConversationTitlerTest {

    private fun msg(role: Role, content: String) =
        MessageEntity(id = "m", conversationId = "c", role = role, content = content, createdAt = 0)

    @Test
    fun `generic one-word echoes are not usable titles`() {
        for (bad in listOf("Title", "title", "Conversation", "New conversation", "chat", "Untitled", "")) {
            assertThat(ConversationTitler.isUsable(bad)).isFalse()
        }
    }

    @Test
    fun `a specific title is usable`() {
        assertThat(ConversationTitler.isUsable("How tall the Eiffel Tower is")).isTrue()
    }

    @Test
    fun `clean strips quotes, markdown, and trailing punctuation`() {
        assertThat(ConversationTitler.clean("\"Eiffel Tower height\".")).isEqualTo("Eiffel Tower height")
        assertThat(ConversationTitler.clean("# Photosynthesis basics")).isEqualTo("Photosynthesis basics")
        assertThat(ConversationTitler.clean("Making tea\nsecond line")).isEqualTo("Making tea")
    }

    @Test
    fun `fallback uses the first user message, not a placeholder`() {
        val history = listOf(
            msg(Role.USER, "How tall is the Eiffel Tower?"),
            msg(Role.ASSISTANT, "It is 330 metres tall."),
        )
        assertThat(ConversationTitler.fallback(history)).isEqualTo("How tall is the Eiffel Tower")
    }

    @Test
    fun `fallback caps at eight words`() {
        val history = listOf(msg(Role.USER, "one two three four five six seven eight nine ten"))
        assertThat(ConversationTitler.fallback(history)).isEqualTo("one two three four five six seven eight")
    }
}
