package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import org.junit.Test

/**
 * Guards item 12: each mode applies its own system instructions, a switch carries
 * the existing conversation forward while changing behaviour, and the inline note
 * copy matches the mode being entered.
 */
class ModeSwitchTest {

    @Test
    fun `chat and logic apply different system instructions`() {
        val chat = SystemPrompts.forMode(Mode.CHAT)
        val logic = SystemPrompts.forMode(Mode.LOGIC)
        assertThat(chat).contains("This is Chat")
        assertThat(logic).contains("Logic Partner")
        assertThat(logic).contains("test the user's thinking")
        assertThat(logic).isNotEqualTo(chat)
    }

    @Test
    fun `the inline notice names the mode being entered`() {
        assertThat(SystemPrompts.modeSwitchNotice(Mode.LOGIC)).contains("Logic Partner is on")
        assertThat(SystemPrompts.modeSwitchNotice(Mode.CHAT)).contains("Back to Chat")
    }

    @Test
    fun `a mid-conversation switch carries the real turns forward and drops the marker`() {
        // The prompt builder must keep the user and assistant turns across a switch
        // (context carried) while the SYSTEM marker never becomes a model turn.
        val history = listOf(
            PromptBuilder.Turn(Role.USER, "my plan is X"),
            PromptBuilder.Turn(Role.ASSISTANT, "here is a thought"),
            PromptBuilder.Turn(Role.SYSTEM, "Logic Partner is on."),
            PromptBuilder.Turn(Role.USER, "defend it"),
        )
        val forModel = history.filter { it.role != Role.SYSTEM }
        assertThat(forModel.map { it.role }).containsExactly(Role.USER, Role.ASSISTANT, Role.USER).inOrder()
        assertThat(forModel.map { it.content }).contains("my plan is X")
    }
}
