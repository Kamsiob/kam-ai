package com.kamsiob.kamai.assist

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Creates the assistant session. When the assistant is summoned (long-press
 * power, or the assist gesture), the session opens the Kam AI overlay and then
 * finishes itself, so the overlay is a normal, focusable window the user can type
 * and talk into. Rendering the overlay as its own activity rather than inside the
 * voice-interaction window keeps the Compose UI, the keyboard, and the microphone
 * behaving exactly as they do everywhere else in the app.
 */
class KamAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                val intent = Intent(context, OverlayActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startAssistantActivity(intent)
                hide()
            }
        }
}
