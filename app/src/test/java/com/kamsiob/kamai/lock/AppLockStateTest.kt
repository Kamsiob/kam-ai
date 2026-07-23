package com.kamsiob.kamai.lock

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app lock's timeout and state machine. The lock has to be off by default,
 * gate a cold launch, survive a brief background trip without re-prompting, and
 * re-lock once the timeout passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLockStateTest {

    @Before
    fun setUp() {
        AppLock.init(ApplicationProvider.getApplicationContext())
        if (AppLock.enabled) AppLock.disable()
    }

    @Test
    fun theLockIsOffByDefault() {
        assertThat(AppLock.enabled).isFalse()
        assertThat(AppLock.locked).isFalse()
    }

    @Test
    fun enablingDeviceModeDoesNotHoldAPassphrase() {
        AppLock.enable(AppLock.Mode.DEVICE, null)
        assertThat(AppLock.enabled).isTrue()
        assertThat(AppLock.sessionSecret).isNull()
    }

    @Test
    fun enablingPassphraseModeHoldsTheSecretForTheSession() {
        val secret = "open sesame".toCharArray()
        AppLock.enable(AppLock.Mode.PASSPHRASE, secret)
        assertThat(AppLock.sessionSecret).isEqualTo(secret)
    }

    @Test
    fun aBriefBackgroundTripDoesNotRelock() {
        AppLock.enable(AppLock.Mode.DEVICE, null)
        AppLock.markUnlocked(null)

        AppLock.onBackgrounded(1_000)
        // Back within the timeout window.
        AppLock.onForegrounded(1_000 + AppLock.timeoutMs - 1)
        assertThat(AppLock.locked).isFalse()
    }

    @Test
    fun crossingTheTimeoutRelocks() {
        AppLock.enable(AppLock.Mode.PASSPHRASE, "s".toCharArray())
        AppLock.markUnlocked("s".toCharArray())

        AppLock.onBackgrounded(1_000)
        AppLock.onForegrounded(1_000 + AppLock.timeoutMs + 1)
        assertThat(AppLock.locked).isTrue()
        // The passphrase is dropped from memory when it re-locks.
        assertThat(AppLock.sessionSecret).isNull()
    }

    @Test
    fun disablingClearsEverything() {
        AppLock.enable(AppLock.Mode.PASSPHRASE, "s".toCharArray())
        AppLock.disable()
        assertThat(AppLock.enabled).isFalse()
        assertThat(AppLock.locked).isFalse()
        assertThat(AppLock.sessionSecret).isNull()
    }

    @Test
    fun aDisabledLockNeverRelocksOnTimeout() {
        AppLock.onBackgrounded(1_000)
        AppLock.onForegrounded(1_000 + 10 * AppLock.timeoutMs)
        assertThat(AppLock.locked).isFalse()
    }
}
