package com.kamsiob.kamai.assist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Helpers for the digital-assistant role: whether Kam AI is the current assistant,
 * and opening the system screen where the user sets it. There is no API to request
 * the assistant role directly, so the honest path is to take the user to the right
 * settings screen and explain what to pick.
 */
object AssistantRole {

    /** True when Kam AI is the phone's selected voice-interaction assistant. */
    fun isDefault(context: Context): Boolean {
        val current = Settings.Secure.getString(
            context.contentResolver, "voice_interaction_service",
        ) ?: return false
        val component = ComponentName.unflattenFromString(current) ?: return false
        return component.packageName == context.packageName
    }

    /**
     * Opens the system Digital assistant screen if the phone exposes it, else the
     * general default-apps screen. Returns false if neither could be opened, so the
     * caller can fall back to plain instructions.
     */
    fun openSettings(context: Context): Boolean {
        val targets = listOf(
            Intent("android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in targets) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return true
        }
        return false
    }
}
