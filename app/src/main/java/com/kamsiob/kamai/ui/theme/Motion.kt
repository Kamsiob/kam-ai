package com.kamsiob.kamai.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// DESIGN.md section 6. Exactly two spring personalities and three durations.
// Motion restraint is the brand: calm by default, one small celebration at
// meaningful moments.

object KamMotion {

    /** fast */
    const val FAST_MS = 180

    /** medium */
    const val MEDIUM_MS = 340

    /** slow, for theme crossfades and large surfaces */
    const val SLOW_MS = 560

    /** Screens slide this far in the direction of travel. */
    val SLIDE_DISTANCE = 26.dp

    /** Items stagger in after a screen change at this increment. */
    const val STAGGER_MS = 40

    /**
     * Damped, no visible bounce. Reference cubic-bezier(0.25, 0.9, 0.3, 1).
     * Navigation transitions, theme changes, list and layout changes, sheet
     * dismissal, and everything else by default.
     */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * Slight overshoot. Reference cubic-bezier(0.34, 1.45, 0.5, 1). Reserved for
     * signature moments only: the flag pop, the follow-up check filling in, the
     * Discover card landing, the nav pill activating, the sheet arriving.
     */
    fun <T> expressive(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> fade(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMs)
}

/**
 * True when the system reduced-motion setting is on. DESIGN.md requires all
 * animation to collapse to instant or near-instant when it is.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean = LocalReducedMotion.current

/** [KamMotion.standard], or an instant snap when reduced motion is on. */
@Composable
fun <T> standardSpec(): FiniteAnimationSpec<T> =
    if (reducedMotion()) snap() else KamMotion.standard()

/** [KamMotion.expressive], or an instant snap when reduced motion is on. */
@Composable
fun <T> expressiveSpec(): FiniteAnimationSpec<T> =
    if (reducedMotion()) snap() else KamMotion.expressive()

@Composable
fun <T> fadeSpec(durationMs: Int = KamMotion.MEDIUM_MS): FiniteAnimationSpec<T> =
    if (reducedMotion()) snap() else KamMotion.fade(durationMs)

/** Reads the platform's animator duration scale. Zero means reduced motion. */
@Composable
internal fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
