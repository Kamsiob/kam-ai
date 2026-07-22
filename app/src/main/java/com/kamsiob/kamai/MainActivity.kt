package com.kamsiob.kamai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kamsiob.kamai.ui.KamAiApp
import com.kamsiob.kamai.ui.theme.Appearance
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The single activity. Everything else is Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        // Read the saved theme and accent before the first frame, so there is
        // no flash of the wrong appearance at launch.
        Appearance.init(this)
        super.onCreate(savedInstanceState)

        setContent {
            KamTheme {
                KamAiApp()
            }
        }
    }
}
