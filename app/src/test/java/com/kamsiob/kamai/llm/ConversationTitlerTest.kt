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

    // When a titling pass may run (#38). Each pass is a multi-second model run
    // that overwrites the KV cache, so this rule decides a real cost.

    @Test
    fun `a conversation with nothing to go on is not titled`() {
        assertThat(ConversationTitler.shouldTitle(hasTitle = false, messageCount = 0, allowRefresh = true)).isFalse()
        assertThat(ConversationTitler.shouldTitle(hasTitle = false, messageCount = 1, allowRefresh = true)).isFalse()
    }

    @Test
    fun `an untitled conversation is titled as soon as it has an exchange`() {
        assertThat(ConversationTitler.shouldTitle(hasTitle = false, messageCount = 2, allowRefresh = true)).isTrue()
        // Even from the open-a-conversation safety net, which fills gaps.
        assertThat(ConversationTitler.shouldTitle(hasTitle = false, messageCount = 2, allowRefresh = false)).isTrue()
    }

    @Test
    fun `an already titled conversation is left alone between milestones`() {
        listOf(2, 4, 6, 10, 20).forEach { n ->
            assertThat(ConversationTitler.shouldTitle(hasTitle = true, messageCount = n, allowRefresh = true))
                .isFalse()
        }
    }

    @Test
    fun `the refresh milestone replaces an existing title once`() {
        assertThat(
            ConversationTitler.shouldTitle(
                hasTitle = true,
                messageCount = ConversationTitler.TITLE_REFRESH_AT,
                allowRefresh = true,
            ),
        ).isTrue()
    }

    @Test
    fun `merely opening a conversation never re-titles it`() {
        // The regression this exists for. Opening does not change the length, so a
        // conversation sitting exactly at the milestone used to re-title on every
        // single open: the title changed under the user, and each pass overwrote
        // the KV cache and cost the next real turn a full re-prefill.
        assertThat(
            ConversationTitler.shouldTitle(
                hasTitle = true,
                messageCount = ConversationTitler.TITLE_REFRESH_AT,
                allowRefresh = false,
            ),
        ).isFalse()
    }
}
