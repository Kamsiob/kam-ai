package com.kamsiob.kamai.lock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The optional lock on Kam AI itself, separate from the phone's own lock. PART 3.
 * Off by default.
 *
 * Two honestly-labelled strengths, chosen at setup:
 *
 * - [Mode.DEVICE] is backed by the phone's own credential (fingerprint, face, or
 *   the phone PIN or pattern). It is recoverable, because the device credential
 *   always works, and it is the more convenient choice. It is a gate on the app,
 *   on top of the always-on at-rest encryption that already makes the database
 *   meaningless if the file is copied off the phone. It is honestly the slightly
 *   weaker option against someone who knows the phone's own code.
 *
 * - [Mode.PASSPHRASE] is a separate passphrase known only for Kam AI. It is
 *   stronger, because it gates the database key itself: without it the database
 *   cannot be opened at all, not even by someone with the phone unlocked and its
 *   code. It is unrecoverable by design. A forgotten passphrase cannot be
 *   reset, only wiped, which is the honest tradeoff for that strength.
 *
 * The lock config lives in a small preferences file, readable before the
 * database is opened, since in passphrase mode the database cannot open until
 * the lock is satisfied. No passphrase is ever stored: in passphrase mode the
 * proof that it is right is that it unwraps the database key.
 */
object AppLock {

    enum class Mode { DEVICE, PASSPHRASE }

    private const val PREFS = "kamai_lock"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"
    private const val KEY_TIMEOUT = "timeout_ms"
    private const val KEY_BIOMETRIC = "biometric"

    /** Sensible default: a session stays unlocked for a couple of minutes in
     *  the background so a person is not re-authenticating constantly. */
    const val DEFAULT_TIMEOUT_MS = 120_000L

    private lateinit var prefs: android.content.SharedPreferences

    var enabled by mutableStateOf(false)
        private set
    var mode by mutableStateOf(Mode.DEVICE)
        private set
    var timeoutMs by mutableStateOf(DEFAULT_TIMEOUT_MS)
        private set
    var biometricEnabled by mutableStateOf(false)
        private set

    /** True while the app is locked and its data must not be shown. */
    var locked by mutableStateOf(false)
        private set

    /**
     * The passphrase, held only in memory while unlocked, so the database key
     * can be unwrapped. Null in device mode and while locked.
     */
    @Volatile
    var sessionSecret: CharArray? = null
        private set

    private var backgroundedAt: Long = 0L

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        mode = runCatching { Mode.valueOf(prefs.getString(KEY_MODE, null) ?: Mode.DEVICE.name) }
            .getOrDefault(Mode.DEVICE)
        timeoutMs = prefs.getLong(KEY_TIMEOUT, DEFAULT_TIMEOUT_MS)
        biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC, false)
        // Starts locked whenever the lock is on, so a cold launch is gated.
        locked = enabled
    }

    fun chooseTimeout(ms: Long) {
        timeoutMs = ms
        prefs.edit().putLong(KEY_TIMEOUT, ms).apply()
    }

    fun chooseBiometricEnabled(on: Boolean) {
        biometricEnabled = on
        prefs.edit().putBoolean(KEY_BIOMETRIC, on).apply()
    }

    /** Turns the lock on in [newMode]. In passphrase mode [secret] is required. */
    fun enable(newMode: Mode, secret: CharArray?) {
        enabled = true
        mode = newMode
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_MODE, newMode.name)
            .apply()
        // Setting it up leaves the session unlocked; the passphrase is live.
        locked = false
        sessionSecret = if (newMode == Mode.PASSPHRASE) secret else null
    }

    fun disable() {
        enabled = false
        biometricEnabled = false
        prefs.edit().putBoolean(KEY_ENABLED, false).putBoolean(KEY_BIOMETRIC, false).apply()
        locked = false
        sessionSecret = null
    }

    /** Marks the app unlocked for this session. [secret] set in passphrase mode. */
    fun markUnlocked(secret: CharArray?) {
        locked = false
        if (mode == Mode.PASSPHRASE) sessionSecret = secret
        backgroundedAt = 0L
    }

    /** Called when the app goes to the background, to start the timeout clock. */
    fun onBackgrounded(nowMs: Long) {
        if (enabled) backgroundedAt = nowMs
    }

    /**
     * Called when the app returns to the foreground. Re-locks if the timeout has
     * elapsed since it was backgrounded.
     */
    fun onForegrounded(nowMs: Long) {
        if (!enabled) return
        if (backgroundedAt != 0L && nowMs - backgroundedAt >= timeoutMs) {
            locked = true
            sessionSecret = null
        }
    }
}
