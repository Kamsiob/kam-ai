package com.kamsiob.kamai.integrations

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Carries text and intent from the system integrations (the text-selection "Ask
 * Kam AI" action, the share sheet, the widget, the tile) into the app once it is
 * on screen. A process-level holder rather than intent extras threaded through
 * every screen, so it survives the app lock gate cleanly like the assistant
 * handoff does.
 */
object Intake {

    /** What to do with incoming text. */
    enum class Target { CHAT, WORKBENCH }

    data class Request(val text: String, val target: Target)

    /** Text handed in to prefill a new chat's composer or the Workbench input. */
    val pending = MutableStateFlow<Request?>(null)

    /** A bare request to start a new chat (widget, tile), no text. */
    val newChat = MutableStateFlow(false)

    /** A bare request to start voice capture in a new chat (widget). */
    val voiceChat = MutableStateFlow(false)

    fun request(text: String, target: Target) { pending.value = Request(text, target) }
    fun consume(): Request? { val v = pending.value; pending.value = null; return v }

    fun requestNewChat() { newChat.value = true }
    fun consumeNewChat(): Boolean { val v = newChat.value; newChat.value = false; return v }

    fun requestVoiceChat() { voiceChat.value = true }
    fun consumeVoiceChat(): Boolean { val v = voiceChat.value; voiceChat.value = false; return v }
}
