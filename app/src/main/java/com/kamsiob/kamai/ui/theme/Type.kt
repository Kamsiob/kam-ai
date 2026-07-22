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
