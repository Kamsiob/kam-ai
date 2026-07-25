@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.kamsiob.kamai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kamsiob.kamai.R

// DESIGN.md section 4.
//   Sora, 600 and 700, for screen titles, card titles, tier names, mode names,
//     and the wordmark. Slightly negative tracking at large sizes.
//   Manrope, 400 to 800, for everything else.
//   JetBrains Mono for specs and metadata only: RAM figures, download sizes,
//     versions, timestamps, source chips, section eyebrow labels. Mono signals a
//     fact about the machine.
// Inter, Roboto, and Open Sans are never used as display faces.
//
// All three ship as variable fonts, so each weight is one axis setting on one
// file rather than a separate binary. That keeps roughly 460 KB of fonts in the
// APK instead of about two megabytes of static cuts.

private fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val SoraFamily = FontFamily(
    Font(R.font.sora_variable, FontWeight.W600, variationSettings = weight(600)),
    Font(R.font.sora_variable, FontWeight.W700, variationSettings = weight(700)),
)

val ManropeFamily = FontFamily(
    Font(R.font.manrope_variable, FontWeight.W400, variationSettings = weight(400)),
    Font(R.font.manrope_variable, FontWeight.W500, variationSettings = weight(500)),
    Font(R.font.manrope_variable, FontWeight.W600, variationSettings = weight(600)),
    Font(R.font.manrope_variable, FontWeight.W700, variationSettings = weight(700)),
    Font(R.font.manrope_variable, FontWeight.W800, variationSettings = weight(800)),
)

/**
 * The one serif in the app, italic, for the single Brainstorm nudge line and
 * nothing else (DESIGN.md sections 4 and 7, issue #29).
 *
 * A static cut rather than the variable original: it renders one fixed line at
 * one size in one place, so the four axes Fraunces ships with are unreachable
 * weight. Pinned at opsz 24, wght 400, SOFT 0, WONK 1, then subset to only the
 * glyphs that line needs. 415 KB down to 5.8 KB, 18 glyphs. Regenerate with
 * `tools/subset_fraunces.py` if the line ever changes, because a character that
 * is not in the subset will not render at all.
 */
val SerifItalicFamily = FontFamily(
    Font(R.font.fraunces_brainstorm_subset, FontWeight.W400),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.W400, variationSettings = weight(400)),
    Font(R.font.jetbrains_mono_variable, FontWeight.W500, variationSettings = weight(500)),
    Font(R.font.jetbrains_mono_variable, FontWeight.W600, variationSettings = weight(600)),
)

/**
 * Kam AI's own text roles. Named for what they are used for rather than for
 * Material's size ladder, so a screen reads as a description of itself.
 */
object KamType {
    val screenTitle = TextStyle(
        fontFamily = SoraFamily, fontWeight = FontWeight.W700,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em,
    )
    val sectionTitle = TextStyle(
        fontFamily = SoraFamily, fontWeight = FontWeight.W700,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.02).em,
    )
    val cardTitle = TextStyle(
        fontFamily = SoraFamily, fontWeight = FontWeight.W600,
        fontSize = 17.sp, lineHeight = 23.sp, letterSpacing = (-0.02).em,
    )
    val wordmark = TextStyle(
        fontFamily = SoraFamily, fontWeight = FontWeight.W600,
        fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.02).em,
    )

    val body = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W400,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    val bodyEmphasis = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W600,
        fontSize = 15.sp, lineHeight = 22.sp,
    )
    val bodyLarge = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W400,
        fontSize = 16.sp, lineHeight = 25.sp,
    )
    val label = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W600,
        fontSize = 14.sp, lineHeight = 19.sp,
    )
    val secondary = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W400,
        fontSize = 13.sp, lineHeight = 18.sp,
    )

    /** Facts about the machine: sizes, versions, timestamps, source chips. */
    /**
     * The Brainstorm nudge, and only that. Italic is genuine here rather than a
     * synthetic slant, which is the whole reason a fourth face was bundled.
     */
    val nudgeSerif = TextStyle(
        fontFamily = SerifItalicFamily, fontWeight = FontWeight.W400,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 20.sp, lineHeight = 27.sp,
    )

    /** The nudge line for every other mode, in the app's ordinary voice. */
    val nudge = TextStyle(
        fontFamily = ManropeFamily, fontWeight = FontWeight.W500,
        fontSize = 19.sp, lineHeight = 26.sp,
    )

    /** Workbench's nudge, in mono, because it describes where output lands. */
    val nudgeMono = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.W500,
        fontSize = 17.sp, lineHeight = 24.sp,
    )

    val mono = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.W500,
        fontSize = 12.sp, lineHeight = 16.sp,
    )

    /** Small uppercase mono label above a settings group or a Discover card. */
    val eyebrow = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.W600,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em,
    )
}

private val Int.em get() = this.toFloat().em
private val Double.em get() = this.toFloat().em
private val Float.em get() = androidx.compose.ui.unit.TextUnit(
    this, androidx.compose.ui.unit.TextUnitType.Em,
)

/** Material's typography, mapped onto the same faces for stock components. */
val KamTypography = Typography(
    displayLarge = KamType.screenTitle,
    displayMedium = KamType.sectionTitle,
    headlineLarge = KamType.sectionTitle,
    headlineMedium = KamType.cardTitle,
    titleLarge = KamType.cardTitle,
    titleMedium = KamType.bodyEmphasis,
    titleSmall = KamType.label,
    bodyLarge = KamType.bodyLarge,
    bodyMedium = KamType.body,
    bodySmall = KamType.secondary,
    labelLarge = KamType.label,
    labelMedium = KamType.secondary,
    labelSmall = KamType.mono,
)
