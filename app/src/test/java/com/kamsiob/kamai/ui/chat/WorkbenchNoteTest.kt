package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.SystemPrompts
import org.junit.Test

/**
 * Opening a linked Workbench from a chat leaves a note behind (#39).
 *
 * This existed as copy, with a doc comment describing precisely when it would
 * appear, and was never once shown: choosing Workbench in the mode picker went
 * straight to navigation and skipped the mode-switch path that writes notes. A
 * chat that had spawned a Workbench looked exactly like one that had not.
 */
class WorkbenchNoteTest {

    private var n = 0

    private fun msg(role: Role, content: String) = MessageEntity(
        id = "m${n++}",
        conversationId = "c1",
        role = role,
        content = content,
        createdAt = n.toLong(),
    )

    private val realConversation = listOf(
        msg(Role.USER, "name two rivers"),
        msg(Role.ASSISTANT, "The Nile and the Amazon are two rivers."),
    )

    @Test
    fun `a conversation with real turns gets the note`() {
        assertThat(WorkbenchNote.shouldMark(realConversation)).isTrue()
    }

    @Test
    fun `an empty conversation does not`() {
        // Nothing to mark yet, and the user is on their way to the Workbench.
        assertThat(WorkbenchNote.shouldMark(emptyList())).isFalse()
    }

    @Test
    fun `a conversation of nothing but system notes does not`() {
        val onlySystem = listOf(msg(Role.SYSTEM, SystemPrompts.modeSwitchNotice(Mode.LOGIC)))
        assertThat(WorkbenchNote.shouldMark(onlySystem)).isFalse()
    }

    @Test
    fun `walking back and forth does not stack up copies`() {
        val marked = realConversation + msg(Role.SYSTEM, WorkbenchNote.text)
        assertThat(WorkbenchNote.shouldMark(marked)).isFalse()
    }

    @Test
    fun `a mode switch after the note means the next visit is marked again`() {
        // The user went to the Workbench, came back, switched to Logic Partner,
        // then went to the Workbench again. The second visit is a real second
        // crossing and the transcript should say so.
        val later = realConversation +
            msg(Role.SYSTEM, WorkbenchNote.text) +
            msg(Role.SYSTEM, SystemPrompts.modeSwitchNotice(Mode.LOGIC))
        assertThat(WorkbenchNote.shouldMark(later)).isTrue()
    }

    @Test
    fun `the note says the conversation stays where it is`() {
        // Choosing Workbench does not convert this conversation, and the note is
        // the only thing that tells the user so.
        assertThat(WorkbenchNote.text).contains("linked session")
        assertThat(WorkbenchNote.text).contains("This conversation stays here")
    }
}
