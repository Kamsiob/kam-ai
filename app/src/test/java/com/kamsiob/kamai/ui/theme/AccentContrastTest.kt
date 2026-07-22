package com.kamsiob.kamai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every accent has to clear the same contrast bar in BOTH themes, or it does not
 * ship. This is the mechanical version of the promise: no colour that only looks
 * right in one theme.
 *
 * The thresholds are WCAG: on-accent text on the filled accent at 4.5:1 for
 * normal text, and the accent against the theme background at 3:1 so it reads as
 * the accent rather than melting into ivory or pine.
 */
class AccentContrastTest {

    private val lightBg = Color(0xFFF6F4EC)
    private val darkBg = Color(0xFF0F1512)

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun everyAccentIsReadableInLightTheme() {
        Accents.all.forEach { accent ->
            val onAccent = contrast(accent.lightAccent, accent.lightOnAccent)
            val onBg = contrast(accent.lightAccent, lightBg)
            assertThat(accent.name to onAccent).isNotNull()
            assertThat(onAccent).isAtLeast(4.5)
            assertThat(onBg).isAtLeast(3.0)
        }
    }

    @Test
    fun everyAccentIsReadableInDarkTheme() {
        Accents.all.forEach { accent ->
            val onAccent = contrast(accent.darkAccent, accent.darkOnAccent)
            val onBg = contrast(accent.darkAccent, darkBg)
            assertThat(onAccent).isAtLeast(4.5)
            assertThat(onBg).isAtLeast(3.0)
        }
    }

    @Test
    fun thereAreSixteenAccentsSplitEightAndEight() {
        assertThat(Accents.all).hasSize(16)
        assertThat(Accents.bright).hasSize(8)
        assertThat(Accents.earthy).hasSize(8)
    }

    @Test
    fun greenIsTheDefaultAndKeepsTheBrandValues() {
        assertThat(Accents.default.id).isEqualTo("green")
        // The exact DESIGN.md section 3 values, unchanged by the accent system.
        assertThat(Accents.default.lightAccent).isEqualTo(Color(0xFF2E7A52))
        assertThat(Accents.default.darkAccent).isEqualTo(Color(0xFF6FD19E))
        assertThat(Accents.default.lightTonalFill).isEqualTo(Color(0xFFE2EDE0))
        assertThat(Accents.default.darkTonalText).isEqualTo(Color(0xFF9FDDBA))
    }

    @Test
    fun accentIdsAreUnique() {
        assertThat(Accents.all.map { it.id }.toSet()).hasSize(16)
    }
}
