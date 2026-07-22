package com.kamsiob.kamai.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * DESIGN.md is the binding source of truth for colour. This test pins every
 * value in section 3 so a stray edit to the palette fails the build rather than
 * quietly shipping.
 */
class DesignTokensTest {

    private fun Color.hex(): String =
        "#%06X".format(toArgb() and 0xFFFFFF)

    @Test
    fun lightThemeMatchesTheSpecifiedPalette() {
        with(LightKamColors) {
            assertThat(background.hex()).isEqualTo("#F6F4EC")
            assertThat(surface.hex()).isEqualTo("#FFFFFF")
            assertThat(surfaceSecondary.hex()).isEqualTo("#EDEDE2")
            assertThat(tonalFill.hex()).isEqualTo("#E2EDE0")
            assertThat(tonalText.hex()).isEqualTo("#2A5C42")
            assertThat(textPrimary.hex()).isEqualTo("#1B241E")
            assertThat(textSecondary.hex()).isEqualTo("#61705F")
            assertThat(textTertiary.hex()).isEqualTo("#95A093")
            assertThat(accent.hex()).isEqualTo("#2E7A52")
            assertThat(onAccent.hex()).isEqualTo("#F2FBF4")
            assertThat(flagAmber.hex()).isEqualTo("#C98A22")
            assertThat(amberFill.hex()).isEqualTo("#F7EBD3")
        }
    }

    @Test
    fun darkThemeMatchesTheSpecifiedPalette() {
        with(DarkKamColors) {
            assertThat(background.hex()).isEqualTo("#0F1512")
            assertThat(surface.hex()).isEqualTo("#182019")
            assertThat(surfaceSecondary.hex()).isEqualTo("#131A15")
            assertThat(tonalFill.hex()).isEqualTo("#1D2E23")
            assertThat(tonalText.hex()).isEqualTo("#9FDDBA")
            assertThat(textPrimary.hex()).isEqualTo("#EDF2EA")
            assertThat(textSecondary.hex()).isEqualTo("#9AA69B")
            assertThat(textTertiary.hex()).isEqualTo("#5E6A60")
            assertThat(accent.hex()).isEqualTo("#6FD19E")
            assertThat(onAccent.hex()).isEqualTo("#0A1B11")
            assertThat(flagAmber.hex()).isEqualTo("#E4B05A")
            assertThat(amberFill.hex()).isEqualTo("#2E2515")
        }
    }

    @Test
    fun backgroundsAreNeverPureBlackOrPureWhite() {
        // DESIGN.md section 3, stated outright.
        assertThat(LightKamColors.background).isNotEqualTo(Color.White)
        assertThat(DarkKamColors.background).isNotEqualTo(Color.Black)
    }

    @Test
    fun theCoreGradientIsTheSameInBothThemes() {
        // DESIGN.md section 2: the core gradient never changes.
        assertThat(MarkCoreHighlight.hex()).isEqualTo("#C9F5DB")
        assertThat(MarkCoreMid.hex()).isEqualTo("#4FBF85")
        assertThat(MarkCoreEdge.hex()).isEqualTo("#1F6B44")
    }

    @Test
    fun amberDiffersFromTheAccentInBothThemes() {
        // Amber is reserved for flags, locked tiers, and the support button. If
        // it ever collides with the accent, that discipline stops being visible.
        assertThat(LightKamColors.flagAmber).isNotEqualTo(LightKamColors.accent)
        assertThat(DarkKamColors.flagAmber).isNotEqualTo(DarkKamColors.accent)
    }

    @Test
    fun motionDurationsMatchTheSpec() {
        assertThat(KamMotion.FAST_MS).isEqualTo(180)
        assertThat(KamMotion.MEDIUM_MS).isEqualTo(340)
        assertThat(KamMotion.SLOW_MS).isEqualTo(560)
        assertThat(KamMotion.STAGGER_MS).isEqualTo(40)
    }

    @Test
    fun screenPaddingStaysAsTuned() {
        // DESIGN.md: tuned by hand, do not widen it back.
        assertThat(KamDimens.screenPadding.value).isEqualTo(14f)
        assertThat(KamDimens.minTouchTarget.value).isAtLeast(48f)
    }
}
