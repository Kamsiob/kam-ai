package com.kamsiob.kamai.assist

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Carries a conversation id from the overlay's "Open Kam AI" handoff to the main
 * app. The overlay creates the conversation from the exchange, then the main app
 * opens it once it is on screen (after the app lock, if any). A process-level
 * holder rather than an intent round-trip so it survives the lock gate cleanly.
 */
object Handoff {
    val pending = MutableStateFlow<String?>(null)

    fun request(conversationId: String) { pending.value = conversationId }

    fun consume(): String? {
        val v = pending.value
        pending.value = null
        return v
    }
}
