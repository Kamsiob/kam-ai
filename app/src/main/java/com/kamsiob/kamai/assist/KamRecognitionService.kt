package com.kamsiob.kamai.assist

import android.speech.RecognitionService

/**
 * A required piece of the digital-assistant registration: a VoiceInteractionService
 * must name a RecognitionService in its metadata or the system rejects it. Kam AI
 * does its own on-device speech-to-text with whisper.cpp inside the overlay, not
 * through this system path, so this implementation is a deliberate no-op: every
 * request ends with a plain "not supported" error rather than pretending to
 * listen. It exists only to make the assistant role valid.
 */
class KamRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: android.content.Intent?, listener: Callback?) {
        runCatching { listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
