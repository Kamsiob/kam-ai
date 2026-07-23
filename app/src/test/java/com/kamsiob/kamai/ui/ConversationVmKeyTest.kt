package com.kamsiob.kamai.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the fix for the bug where tapping "new chat" reopened the most recent
 * conversation. Every new chat shared one view-model key ("chat-"), so the cached
 * view model kept the previous conversation's id. New chats must get unique keys;
 * an existing conversation must key by its stable id so reopening reuses state.
 */
class ConversationVmKeyTest {

    @Test
    fun `an existing conversation keys by its id so state is reused`() {
        assertThat(conversationVmKey("conv-123")).isEqualTo("conv-123")
        // Deterministic: the same id always yields the same key.
        assertThat(conversationVmKey("conv-123")).isEqualTo(conversationVmKey("conv-123"))
    }

    @Test
    fun `two new chats never share a view-model key`() {
        val first = conversationVmKey("")
        val second = conversationVmKey("")
        assertThat(first).isNotEqualTo(second)
        assertThat(first).startsWith("new-")
        assertThat(second).startsWith("new-")
    }

    @Test
    fun `the new-chat token comes from the nonce, not a shared constant`() {
        assertThat(conversationVmKey("", nonce = { "abc" })).isEqualTo("new-abc")
    }
}
