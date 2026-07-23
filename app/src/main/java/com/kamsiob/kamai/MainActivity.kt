package com.kamsiob.kamai

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.kamsiob.kamai.lock.AppLock
import com.kamsiob.kamai.lock.KamRoot
import com.kamsiob.kamai.ui.theme.Appearance
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The single activity. A FragmentActivity rather than a plain ComponentActivity
 * so the biometric prompt for the app lock can attach to it. Everything else is
 * Compose.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        // Read appearance and lock config before the first frame, so there is no
        // flash of the wrong theme and a locked app is gated from the start.
        Appearance.init(this)
        AppLock.init(this)
        super.onCreate(savedInstanceState)

        setContent {
            KamTheme {
                KamRoot(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.onBackgrounded(SystemClock.elapsedRealtime())
    }

    override fun onStart() {
        super.onStart()
        AppLock.onForegrounded(SystemClock.elapsedRealtime())
    }
}
