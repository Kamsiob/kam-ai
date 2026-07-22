package com.kamsiob.kamai.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How the app looks: theme mode and accent colour.
 *
 * These live in a small SharedPreferences file rather than in the encrypted
 * database, and read synchronously at process start, so the very first frame is
 * already the right theme and accent with no flash. They are pure device-local
 * display preferences, not user content, and the backup includes them by reading
 * this store at export time.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

object Appearance {

    private const val PREFS = "kamai_appearance"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_id"

    private lateinit var prefs: android.content.SharedPreferences

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var accentId by mutableStateOf(Accents.default.id)
        private set

    val accent: Accent get() = Accents.byId(accentId)

    /** Called once, synchronously, before the first frame. */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        accentId = prefs.getString(KEY_ACCENT, null) ?: Accents.default.id
    }

    fun chooseThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun chooseAccent(id: String) {
        accentId = id
        prefs.edit().putString(KEY_ACCENT, id).apply()
    }
}
