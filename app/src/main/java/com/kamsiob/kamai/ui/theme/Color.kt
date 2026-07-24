package com.kamsiob.kamai.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// DESIGN.md section 3. These values are the source of truth for colour in the
// app. One user-chosen accent. The reserved gold (a brighter, more saturated
// former amber, moved further from Workbench's mustard) appears in exactly three
// places: saved items and the bookmark, locked model tiers, and the Support this
// work button, plus destructive-action labels. Nowhere else. No purple. No
// gradients except the core of the mark. The four mode identity hues live in
// ModeColors below and are an identity signal only, never general UI state.

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
// Reserved gold. Bright gold for fills, icons, dots, and the support button;
// a deeper gold wherever it is text, because the bright one is too light on ivory.
private val LightGold             = Color(0xFFEFA913)
// Deeper gold for text and glyphs on ivory. Spec value 96690F measured 4.41 on
// ivory, just under AA; nudged to 8A5F0D (5.12 on ivory, 5.64 on white) so gold
// text and icons stay legible. Recorded in DECISIONS.md.
private val LightGoldText         = Color(0xFF8A5F0D)
private val LightGoldFill         = Color(0xFFFCEFC6)
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
// Reserved gold, dark theme. One luminous gold works for fills, icons, and text.
private val DarkGold              = Color(0xFFFFD166)
private val DarkGoldText          = Color(0xFFFFD166)
private val DarkGoldFill          = Color(0xFF332812)
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
    /** Reserved gold for fills, icons, dots, and the support button. */
    val flagAmber: Color,
    /** Reserved gold at a weight safe as text on the background (deeper on light). */
    val goldText: Color,
    /** Soft gold tint for backgrounds behind gold content. */
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
    flagAmber = LightGold,
    goldText = LightGoldText,
    amberFill = LightGoldFill,
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
    flagAmber = DarkGold,
    goldText = DarkGoldText,
    amberFill = DarkGoldFill,
    border = DarkBorder,
    isDark = true,
)

// The default green, for previews and any caller that does not care about the
// chosen accent.
val LightKamColors = lightKamColors(Accents.default)
val DarkKamColors = darkKamColors(Accents.default)

/**
 * The four mode identity hues (DESIGN.md, Four-Mode Update Part 2). Chosen so the
 * four are instantly distinguishable, harmonise with the ivory and pine grounds,
 * and stay clear of the reserved gold. Discover, which is a source rather than one
 * of the four modes, gets its own identity here too so its conversations are
 * legible in the chat list (Part 11B). Mode colour is an identity signal only: it
 * is never used for general UI state (buttons, links, selection, focus), which
 * stays on the app accent. Every use is paired with the mode name or an icon, so
 * colour is never the only carrier of meaning.
 */
object ModeColors {
    // General
    private val GeneralLight    = Color(0xFF2E7A52)
    private val GeneralDark     = Color(0xFF6FD19E)
    // Logic
    private val LogicLight      = Color(0xFF2F5D8C)
    private val LogicDark       = Color(0xFF7FB3E0)
    // Brainstorm, a deep maroon red (not pink or rose)
    private val BrainstormLight = Color(0xFF9A3B33)
    private val BrainstormDark  = Color(0xFFE2705F)
    // Workbench, a deep mustard
    private val WorkbenchLight  = Color(0xFFB0851C)
    private val WorkbenchDark   = Color(0xFFC9A44E)
    // Discover, its own identity, distinct from the four modes and from the gold
    private val DiscoverLight   = Color(0xFF6A4A9C)
    private val DiscoverDark    = Color(0xFFB79CE6)

    fun of(mode: com.kamsiob.kamai.data.Mode, isDark: Boolean): Color = when (mode) {
        com.kamsiob.kamai.data.Mode.GENERAL -> if (isDark) GeneralDark else GeneralLight
        com.kamsiob.kamai.data.Mode.LOGIC -> if (isDark) LogicDark else LogicLight
        com.kamsiob.kamai.data.Mode.BRAINSTORM -> if (isDark) BrainstormDark else BrainstormLight
        com.kamsiob.kamai.data.Mode.BENCH -> if (isDark) WorkbenchDark else WorkbenchLight
        // Discover and Overlay share the Discover identity in list contexts.
        com.kamsiob.kamai.data.Mode.DISCOVER,
        com.kamsiob.kamai.data.Mode.OVERLAY -> if (isDark) DiscoverDark else DiscoverLight
    }

    /** The four user-facing modes, in the order the segmented control shows them. */
    val fourModes = listOf(
        com.kamsiob.kamai.data.Mode.GENERAL,
        com.kamsiob.kamai.data.Mode.LOGIC,
        com.kamsiob.kamai.data.Mode.BRAINSTORM,
        com.kamsiob.kamai.data.Mode.BENCH,
    )

    /** Full display name for a mode. */
    fun name(mode: com.kamsiob.kamai.data.Mode): String = when (mode) {
        com.kamsiob.kamai.data.Mode.GENERAL -> "General"
        com.kamsiob.kamai.data.Mode.LOGIC -> "Logic"
        com.kamsiob.kamai.data.Mode.BRAINSTORM -> "Brainstorm"
        com.kamsiob.kamai.data.Mode.BENCH -> "Workbench"
        com.kamsiob.kamai.data.Mode.DISCOVER -> "Discover"
        com.kamsiob.kamai.data.Mode.OVERLAY -> "Quick ask"
    }

    /** Short label for the segmented control, where four must fit at phone width. */
    fun shortName(mode: com.kamsiob.kamai.data.Mode): String = when (mode) {
        com.kamsiob.kamai.data.Mode.GENERAL -> "General"
        com.kamsiob.kamai.data.Mode.LOGIC -> "Logic"
        com.kamsiob.kamai.data.Mode.BRAINSTORM -> "Storm"
        com.kamsiob.kamai.data.Mode.BENCH -> "Bench"
        com.kamsiob.kamai.data.Mode.DISCOVER -> "Discover"
        com.kamsiob.kamai.data.Mode.OVERLAY -> "Ask"
    }
}
