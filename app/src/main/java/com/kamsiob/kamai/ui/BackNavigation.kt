package com.kamsiob.kamai.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.kamsiob.kamai.ui.theme.reducedMotion
import kotlin.coroutines.cancellation.CancellationException

/**
 * Back handling for the whole app.
 *
 * The rule is that exactly one thing consumes a back event, and it is always
 * the innermost thing that can be dismissed. In order: an open dialog, an open
 * sheet, an open swipe row, then the navigation stack, then the bottom
 * navigation tab, and only then the app itself.
 *
 * Handlers are registered by the surfaces that own the state rather than
 * centrally, because a central handler would need to know about every piece of
 * transient state in the app and would be wrong the moment a new one appeared.
 * The dispatcher already resolves innermost-first, which is exactly the
 * behaviour wanted, so the ordering falls out of where each handler is declared
 * rather than being maintained by hand.
 */

/**
 * How far through a predictive back gesture the user is, and which way they
 * started from, so the destination can peek underneath.
 */
data class BackGesture(
    val progress: Float = 0f,
    val fromLeftEdge: Boolean = true,
    val inProgress: Boolean = false,
)

/**
 * Runs [onBack] when the gesture commits, while reporting drag progress so the
 * caller can animate the peek.
 *
 * Under reduced motion the progress is never reported as anything but zero, so
 * the surface does not move under the finger and the change lands as a plain
 * swap on commit.
 */
@Composable
fun KamPredictiveBack(
    enabled: Boolean,
    onProgress: (BackGesture) -> Unit,
    onBack: () -> Unit,
) {
    val reduced = reducedMotion()

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                if (!reduced) {
                    onProgress(
                        BackGesture(
                            progress = event.progress,
                            // SwipeEdge.Left means the gesture started at the
                            // left edge, so the destination should come in from
                            // that side.
                            fromLeftEdge = event.swipeEdge == 0,
                            inProgress = true,
                        ),
                    )
                }
            }
            onProgress(BackGesture(inProgress = false))
            onBack()
        } catch (cancelled: CancellationException) {
            // The user changed their mind mid-drag. Settle back with no change.
            onProgress(BackGesture(inProgress = false))
            throw cancelled
        }
    }
}

/**
 * The standard predictive peek: the outgoing surface shrinks slightly and
 * slides toward the edge the gesture came from, revealing what is underneath.
 *
 * Deliberately the damped spring's shape, not the expressive one. This is
 * navigation, and DESIGN.md reserves overshoot for signature moments.
 */
fun Modifier.predictiveBackPeek(gesture: BackGesture): Modifier =
    if (!gesture.inProgress || gesture.progress <= 0f) {
        this
    } else {
        this.graphicsLayer {
            val eased = gesture.progress.coerceIn(0f, 1f)
            val scale = 1f - (eased * 0.08f)
            scaleX = scale
            scaleY = scale
            translationX = if (gesture.fromLeftEdge) {
                eased * size.width * 0.12f
            } else {
                -eased * size.width * 0.12f
            }
            alpha = 1f - (eased * 0.25f)
        }
    }
