package com.kamsiob.kamai.ui.chat

import com.kamsiob.kamai.data.Mode

/**
 * Whether to explain a mode at the top of a conversation started in it (#28).
 *
 * Switching mode mid-conversation writes a note saying what the new mode does.
 * Starting a chat *in* a mode wrote nothing, because that note is only written
 * when there is already something to mark. So somebody whose first Brainstorm
 * conversation began from the Chats control was never told that Brainstorm will
 * not hand them ideas; it just started asking questions.
 *
 * Pulled out of the view model so the three conditions can be tested. The one
 * that matters most is [historyIsEmpty]: without it an explainer could appear in
 * the middle of a conversation, which is the switch note's job and would read as
 * the app repeating itself.
 */
object ModeExplainer {

    fun shouldExplain(mode: Mode, historyIsEmpty: Boolean, alreadyExplained: Boolean): Boolean {
        // General is the resting position and explains itself by being ordinary.
        if (mode == Mode.GENERAL) return false
        if (!historyIsEmpty) return false
        return !alreadyExplained
    }
}
