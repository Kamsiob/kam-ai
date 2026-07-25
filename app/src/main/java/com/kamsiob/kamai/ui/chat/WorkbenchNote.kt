package com.kamsiob.kamai.ui.chat

import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.SystemPrompts

/**
 * Whether opening a linked Workbench should leave a note in the conversation.
 *
 * Pulled out of the view model so the two rules can be tested directly. They are
 * easy to state and easy to get wrong in a way nobody notices: the first defect
 * here was that the note was never written at all, and the obvious fix would have
 * written one every single time the user walked between the two screens.
 */
object WorkbenchNote {

    /** The note itself, written for this moment and shown nowhere else. */
    val text: String get() = SystemPrompts.modeSwitchNotice(Mode.BENCH)

    /**
     * True when [history] should gain the note.
     *
     * Not in an empty conversation: there is nothing yet to mark, and the user is
     * on their way to the Workbench anyway. Not when the last note in the
     * transcript is already this one, so going back and forth between the chat
     * and its Workbench does not stack up copies.
     */
    fun shouldMark(history: List<MessageEntity>): Boolean {
        if (history.none { it.role == Role.USER || it.role == Role.ASSISTANT }) return false
        return history.lastOrNull { it.role == Role.SYSTEM }?.content != text
    }
}
