package com.kamsiob.kamai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DESIGN.md section 3. These values are the source of truth for colour in the
// app. One green accent. Amber appears in exactly three places: follow-up flags,
// locked model tiers, and the Support this work button. Nowhere else. No purple.
// No gradients except the core of the mark.

// Light theme, warm ivory.
private val LightBackground       = Color(0xFFF6F4EC)
private val LightSurface          = Color(0xFFFFFFFF)
private val LightSurfaceSecondary = Color(0xFFEDEDE2)
private val LightTonalFill        = Color(0xFFE2EDE0)
private val LightTonalText        = Color(0xFF2A5C42)
private val LightTextPrimary      = Color(0xFF1B241E)
private val LightTextSecondary    = Color(0xFF61705F)
private val LightTextTertiary     = Color(0xFF95A093)
private val LightAccent           = Color(0xFF2E7A52)
private val LightOnAccent         = Color(0xFFF2FBF4)
private val LightFlagAmber        = Color(0xFFC98A22)
private val LightAmberFill        = Color(0xFFF7EBD3)
private val LightBorder           = Color(0x141B241E) // rgba(27,36,30,0.08)

// Dark theme, deep pine.
private val DarkBackground        = Color(0xFF0F1512)
private val DarkSurface           = Color(0xFF182019)
private val DarkSurfaceSecondary  = Color(0xFF131A15)
private val DarkTonalFill         = Color(0xFF1D2E23)
private val DarkTonalText         = Color(0xFF9FDDBA)
private val DarkTextPrimary       = Color(0xFFEDF2EA)
private val DarkTextSecondary     = Color(0xFF9AA69B)
private val DarkTextTertiary      = Color(0xFF5E6A60)
private val DarkAccent            = Color(0xFF6FD19E)
private val DarkOnAccent          = Color(0xFF0A1B11)
private val DarkFlagAmber         = Color(0xFFE4B05A)
private val DarkAmberFill         = Color(0xFF2E2515)
private val DarkBorder            = Color(0x12EDF2EA) // rgba(237,242,234,0.07)

// The mark's core gradient never changes with the theme. DESIGN.md section 2.
val MarkCoreHighlight = Color(0xFFC9F5DB)
val MarkCoreMid       = Color(0xFF4FBF85)
val MarkCoreEdge      = Color(0xFF1F6B44)

/**
 * Kam AI's colour roles. Material 3's own scheme does not have somewhere honest
 * to put a tertiary text colour or a reserved amber, so the app carries its own
 * set and maps a Material scheme alongside it for the stock components.
 */
@Immutable
data class KamColors(
    val background: Color,
    val surface: Color,
    val surfaceSecondary: Color,
    val tonalFill: Color,
    val tonalText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val flagAmber: Color,
    val amberFill: Color,
    val border: Color,
    val isDark: Boolean,
)

// The neutral palettes are fixed. The accent-driven fields (accent, on-accent,
// and the two tonal shades) come from whichever Accent the user has chosen, so
// building a theme means combining a fixed neutral base with a chosen accent.
// The amber fields never move, whatever the accent.

fun lightKamColors(accent: Accent) = KamColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceSecondary = LightSurfaceSecondary,
    tonalFill = accent.lightTonalFill,
    tonalText = accent.lightTonalText,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    accent = accent.lightAccent,
    onAccent = accent.lightOnAccent,
    flagAmber = LightFlagAmber,
    amberFill = LightAmberFill,
    border = LightBorder,
    isDark = false,
)

fun darkKamColors(accent: Accent) = KamColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceSecondary = DarkSurfaceSecondary,
    tonalFill = accent.darkTonalFill,
    tonalText = accent.darkTonalText,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    accent = accent.darkAccent,
    onAccent = accent.darkOnAccent,
    flagAmber = DarkFlagAmber,
    amberFill = DarkAmberFill,
    border = DarkBorder,
    isDark = true,
)

// The default green, for previews and any caller that does not care about the
// chosen accent.
val LightKamColors = lightKamColors(Accents.default)
val DarkKamColors = darkKamColors(Accents.default)
