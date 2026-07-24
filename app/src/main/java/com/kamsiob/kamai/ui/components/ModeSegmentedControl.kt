package com.kamsiob.kamai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.ModeColors
import com.kamsiob.kamai.ui.theme.expressiveSpec
import com.kamsiob.kamai.ui.theme.reducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The segmented mode control (DESIGN.md, Four-Mode Update Part 2). It is both the
 * new-chat action and the mode selector: tapping or dragging to a segment starts
 * a new conversation in that mode, so a normal conversation is exactly one tap.
 * General is the resting position.
 *
 * The thumb is meant to feel magnetic: it travels on the expressive spring with a
 * small overshoot, stretches slightly along its direction of travel, and the
 * arriving segment's dot scales up and its label brightens. It is draggable as
 * well as tappable, and letting go anywhere near a mode selects it, with a light
 * tick as the thumb crosses each detent and a heavier thump as it snaps home.
 */
@Composable
fun SegmentedModeControl(
    onSelect: (Mode) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<Mode> = ModeColors.fourModes,
) {
    val colors = KamTheme.colors
    val haptics = LocalHapticFeedback.current
    val reduced = reducedMotion()
    val scope = rememberCoroutineScope()
    val n = modes.size
    // Resolved here so the drag/tap callbacks (not composable) can reuse it.
    val thumbSpring = expressiveSpec<Float>()

    val innerPad = 3.dp
    val height = 34.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .padding(innerPad),
    ) {
        val density = LocalDensity.current
        val totalPx = with(density) { maxWidth.toPx() }
        val segPx = totalPx / n

        // Thumb position in segments (0..n-1). Animatable so a tap springs and a
        // drag follows the finger directly.
        val pos = remember { Animatable(0f) }
        // The segment the thumb is currently over, for the live active highlight.
        val activeIndex = pos.value.roundToInt().coerceIn(0, n - 1)

        // Thumb stretch along travel: brief scaleX bump while it is far from its
        // resting integer position, relaxing to 1 as it settles.
        val settleGap = abs(pos.value - pos.value.roundToInt())
        val stretch by animateFloatAsState(
            targetValue = if (reduced) 1f else 1f + (settleGap * 0.18f).coerceAtMost(0.09f),
            animationSpec = tween(90),
            label = "thumb-stretch",
        )

        fun select(index: Int) {
            val m = modes[index]
            haptics.performHapticFeedback(HapticFeedbackType.LongPress) // the thump
            scope.launch {
                if (reduced) pos.snapTo(index.toFloat())
                else pos.animateTo(index.toFloat(), thumbSpring)
            }
            onSelect(m)
        }

        // The moving thumb, the only filled element.
        Box(
            modifier = Modifier
                .width(maxWidth / n)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = pos.value * segPx
                    scaleX = stretch
                }
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.surface),
        )

        // Drag handling across the whole control. Tracks the nearest detent and
        // ticks as it changes; snaps and selects on release.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(n, segPx) {
                    var startIndex = 0
                    var lastDetent = 0
                    detectDragGestures(
                        onDragStart = { offset ->
                            startIndex = (offset.x / segPx).toInt().coerceIn(0, n - 1)
                            lastDetent = startIndex
                        },
                        onDragEnd = {
                            val nearest = pos.value.roundToInt().coerceIn(0, n - 1)
                            if (nearest == startIndex) {
                                // Returned to where it started: snap back, select nothing.
                                scope.launch { pos.animateTo(nearest.toFloat(), thumbSpring) }
                            } else {
                                select(nearest)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val raw = (change.position.x / segPx) - 0.5f
                            // Resist past the two ends rather than travelling beyond.
                            val clamped = raw.coerceIn(-0.4f, (n - 1) + 0.4f)
                            scope.launch { pos.snapTo(clamped) }
                            val detent = clamped.roundToInt().coerceIn(0, n - 1)
                            if (detent != lastDetent) {
                                lastDetent = detent
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) // tick
                            }
                        },
                    )
                }
                .pointerInput(n, segPx) {
                    detectTapGestures { offset ->
                        val index = (offset.x / segPx).toInt().coerceIn(0, n - 1)
                        select(index)
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                modes.forEachIndexed { i, mode ->
                    val active = i == activeIndex
                    val labelColor = ModeColors.of(mode, colors.isDark)
                    val dotScale by animateFloatAsState(
                        if (active && !reduced) 1.18f else 1f, tween(160), label = "dot",
                    )
                    val alpha by animateFloatAsState(
                        if (active) 1f else 0.6f, tween(160), label = "seg-alpha",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer { this.alpha = alpha }
                            .semantics {
                                role = Role.Tab
                                selected = active
                                contentDescription =
                                    "Start a ${ModeColors.name(mode)} chat" + if (active) ", selected" else ""
                            },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(5.5.dp)
                                .graphicsLayer { scaleX = dotScale; scaleY = dotScale }
                                .clip(CircleShape)
                                .background(labelColor),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            ModeColors.shortName(mode),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.W800,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
