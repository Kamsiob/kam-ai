package com.kamsiob.kamai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.MarkCoreEdge
import com.kamsiob.kamai.ui.theme.MarkCoreHighlight
import com.kamsiob.kamai.ui.theme.MarkCoreMid
import com.kamsiob.kamai.ui.theme.reducedMotion

/**
 * The Kam AI mark: a lit core held inside a broken ring, with a smaller inner
 * arc as a counterweight. DESIGN.md section 2.
 *
 * Geometry on a 48 by 48 viewbox:
 *   outer ring   radius 16,   stroke 3.4, rounded caps, 270 degree arc so a
 *                90 degree gap sits at the top right
 *   inner accent radius 10.5, stroke 2.2, about 55 degrees, rotated 150 degrees,
 *                55 percent opacity
 *   core         radius 6.6, radial gradient from a pale mint highlight at the
 *                upper left through mid green to a deep green edge
 *
 * The ring and inner arc take the theme accent. The core gradient never changes.
 *
 * @param breathing when true the mark slowly scales to 1.07 over 4.2 seconds,
 *   used where it acts as a status indicator. Collapses to still under reduced
 *   motion.
 */
@Composable
fun KamMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    breathing: Boolean = false,
    tint: Color = KamTheme.colors.accent,
) {
    val reduced = reducedMotion()
    val scale = if (breathing && !reduced) {
        val transition = rememberInfiniteTransition(label = "mark-breathe")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mark-scale",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            // The mark is decorative wherever it appears next to the wordmark,
            // so it is cleared from the accessibility tree rather than read out
            // on every screen.
            .clearAndSetSemantics { },
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            // Everything below is expressed against the 48 unit viewbox and
            // scaled, so the published geometry stays readable.
            val unit = this.size.minDimension / 48f
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)

            // Outer ring: 270 degrees, gap at the top right. Angles run
            // clockwise from three o'clock, so sweeping 0 to 270 leaves the
            // opening between straight up and three o'clock, which is the
            // top-right quadrant.
            val outerRadius = 16f * unit
            val outerStroke = 3.4f * unit
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(centre.x - outerRadius, centre.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round),
            )

            // Inner accent arc, the counterweight.
            val innerRadius = 10.5f * unit
            val innerStroke = 2.2f * unit
            drawArc(
                color = tint,
                alpha = 0.55f,
                startAngle = 150f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(centre.x - innerRadius, centre.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = innerStroke, cap = StrokeCap.Round),
            )

            // The lit core. This gradient is fixed and never takes the theme.
            val coreRadius = 6.6f * unit
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to MarkCoreHighlight,
                        0.55f to MarkCoreMid,
                        1.0f to MarkCoreEdge,
                    ),
                    // Highlight sits at the upper left of the core.
                    center = Offset(
                        centre.x - coreRadius * 0.4f,
                        centre.y - coreRadius * 0.4f,
                    ),
                    radius = coreRadius * 1.5f,
                ),
                radius = coreRadius,
                center = centre,
            )
        }
    }
}
