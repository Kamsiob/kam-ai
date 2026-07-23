package com.kamsiob.kamai.assist

import android.service.voice.VoiceInteractionService

/**
 * Registers Kam AI as the phone's digital assistant, the same mechanism a
 * long-press of the power button uses. It does nothing itself: the actual overlay
 * lives in [KamAssistSessionService], and this class exists so the system has a
 * VoiceInteractionService to bind. See res/xml/kam_assist.xml for the metadata
 * that ties the two together.
 */
class KamAssistService : VoiceInteractionService()
