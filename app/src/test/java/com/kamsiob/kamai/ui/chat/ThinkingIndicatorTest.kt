package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * Guards item 5: the thinking indicator must appear the instant a message is sent
 * and stay up through model load and prompt ingestion, not only once the empty
 * answer bubble exists. Before the fix it keyed on the last message being empty,
 * so during load (last message = the user's turn) nothing showed and the app
 * looked frozen.
 */
class ThinkingIndicatorTest {

    @Test
    fun `shows on a brand-new chat the instant work starts`() {
        // Streaming set, list momentarily empty (user turn not yet written).
        assertThat(showThinkingIndicator(streaming = true, lastRole = null, lastContent = null)).isTrue()
    }

    @Test
    fun `shows while the model loads with the user's turn last`() {
        assertThat(showThinkingIndicator(true, Role.USER, "how tall is Everest?")).isTrue()
    }

    @Test
    fun `shows for the empty answer placeholder`() {
        assertThat(showThinkingIndicator(true, Role.ASSISTANT, "")).isTrue()
    }

    @Test
    fun `hides once the answer has produced text`() {
        assertThat(showThinkingIndicator(true, Role.ASSISTANT, "Everest is")).isFalse()
    }

    @Test
    fun `hides when not streaming`() {
        assertThat(showThinkingIndicator(false, Role.USER, "hi")).isFalse()
    }
}
