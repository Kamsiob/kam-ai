package com.kamsiob.kamai.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// DESIGN.md section 5. Cards 20 to 24dp, buttons full pill, chips small pills.
val KamShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object KamDimens {
    /**
     * Screen side padding. Tuned by hand so lists breathe wide without touching
     * the edges. DESIGN.md says explicitly: do not widen it back.
     */
    val screenPadding = 14.dp

    /** The soft mask at the top and bottom of a scrolling list. */
    val edgeFade = 14.dp

    val cardRadius = 22.dp
    val cardPadding = 16.dp
    val minTouchTarget = 48.dp
    val markSize = 22.dp
}

val LocalKamColors: ProvidableCompositionLocal<KamColors> =
    staticCompositionLocalOf { LightKamColors }

/** `KamTheme.colors` reads better at a call site than a composition local. */
object KamTheme {
    val colors: KamColors
        @Composable @ReadOnlyComposable get() = LocalKamColors.current
    val type = KamType
    val dimens = KamDimens
}

/**
 * Maps Kam AI's own roles onto a Material scheme so stock Material components
 * land in the right place without being restyled one at a time.
 *
 * Dynamic colour is deliberately not used. The app has one green accent and a
 * reserved amber, and letting the wallpaper repaint it would break the amber
 * discipline that the whole colour system rests on.
 */
private fun KamColors.toMaterialScheme() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = tonalFill, onPrimaryContainer = tonalText,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = tonalFill, onSecondaryContainer = tonalText,
        tertiary = accent, onTertiary = onAccent,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceSecondary, onSurfaceVariant = textSecondary,
        surfaceContainer = surface, surfaceContainerHigh = surfaceSecondary,
        surfaceContainerLow = background, surfaceContainerLowest = background,
        surfaceContainerHighest = surfaceSecondary,
        outline = textTertiary, outlineVariant = border,
        error = flagAmber, onError = onAccent,
        errorContainer = amberFill, onErrorContainer = flagAmber,
        scrim = Color.Black,
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = tonalFill, onPrimaryContainer = tonalText,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = tonalFill, onSecondaryContainer = tonalText,
        tertiary = accent, onTertiary = onAccent,
        background = background, onBackground = textPrimary,
        surface = surface, onSurface = textPrimary,
        surfaceVariant = surfaceSecondary, onSurfaceVariant = textSecondary,
        surfaceContainer = surface, surfaceContainerHigh = surfaceSecondary,
        surfaceContainerLow = background, surfaceContainerLowest = background,
        surfaceContainerHighest = surfaceSecondary,
        outline = textTertiary, outlineVariant = border,
        error = flagAmber, onError = onAccent,
        errorContainer = amberFill, onErrorContainer = flagAmber,
        scrim = Color.Black,
    )
}

/**
 * Every surface colour crossfades together over the slow duration when the
 * theme changes. DESIGN.md: nothing snaps.
 */
@Composable
private fun KamColors.animated(reduced: Boolean): KamColors {
    val spec = if (reduced) {
        androidx.compose.animation.core.snap<Color>()
    } else {
        KamMotion.fade<Color>(KamMotion.SLOW_MS)
    }

    @Composable
    fun c(target: Color, label: String) =
        animateColorAsState(target, animationSpec = spec, label = label).value

    return copy(
        background = c(background, "background"),
        surface = c(surface, "surface"),
        surfaceSecondary = c(surfaceSecondary, "surfaceSecondary"),
        tonalFill = c(tonalFill, "tonalFill"),
        tonalText = c(tonalText, "tonalText"),
        textPrimary = c(textPrimary, "textPrimary"),
        textSecondary = c(textSecondary, "textSecondary"),
        textTertiary = c(textTertiary, "textTertiary"),
        accent = c(accent, "accent"),
        onAccent = c(onAccent, "onAccent"),
        flagAmber = c(flagAmber, "flagAmber"),
        amberFill = c(amberFill, "amberFill"),
        border = c(border, "border"),
    )
}

@Composable
fun KamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Overridden in tests and previews. Defaults to the system setting. */
    reducedMotion: Boolean = systemReducedMotion(),
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) DarkKamColors else LightKamColors
    val colors = target.animated(reducedMotion)

    CompositionLocalProvider(
        LocalKamColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = KamTypography,
            shapes = KamShapes,
            content = content,
        )
    }
}
