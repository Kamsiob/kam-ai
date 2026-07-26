package com.kamsiob.kamai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.text.style.TextOverflow
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
    /** Appended to each segment's spoken label, for the copies of this control
     *  that start a chat somewhere more specific than the Chats list. */
    labelSuffix: String = "",
) {
    val colors = KamTheme.colors
    val haptics = LocalHapticFeedback.current
    val reduced = reducedMotion()
    val scope = rememberCoroutineScope()
    val n = modes.size
    // Resolved here so the drag/tap callbacks (not composable) can reuse it.
    val thumbSpring = expressiveSpec<Float>()

    val innerPad = 3.dp
    // Scales with the user's text size rather than sitting at a fixed 34dp.
    //
    // At the largest accessibility font the fixed height cropped every label top
    // and bottom, which breaks the accessibility floor in DESIGN.md section 11:
    // dynamic type is meant to be respected without breaking layouts. The control
    // has to keep a fixed height, because the sliding thumb is positioned against
    // it, so the height follows the font instead of ignoring it.
    //
    // The label is 14sp at scale 1, so the base leaves comfortable room; the
    // ratio is kept and the result floored at the base so ordinary text sizes are
    // unchanged and only larger ones grow.
    //
    // 36.5dp rather than the original 34: seven percent taller, asked for because
    // the control read as cramped against the navigation bar below it (#69).
    val fontScale = LocalDensity.current.fontScale
    val height = (BASE_HEIGHT * fontScale).coerceAtLeast(BASE_HEIGHT)

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
                // Its own color, not `surface`. On dark, surface and
                // surfaceSecondary are five points apart and the thumb all but
                // vanished; light mode is unchanged (#69).
                .background(colors.controlThumb),
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
                                    "Start a ${ModeColors.name(mode)} chat$labelSuffix" +
                                        if (active) ", selected" else ""
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
                        // Full mode names, shrunk to fit rather than abbreviated.
                        //
                        // These used to read General, Logic, Storm, Bench.
                        // "Storm" is a word the user is never taught: onboarding,
                        // the picker, the banner, the switch note, the Q&A and
                        // the store listing all say Brainstorm, and the screen
                        // reader already said Brainstorm here too, so a sighted
                        // user and a screen reader user were told different names
                        // for the same mode (#63).
                        //
                        // Full names do not fit four across at large font scales,
                        // which is the real constraint the abbreviations existed
                        // for, so the type shrinks instead of the word. The floor
                        // is 8sp: below that it is not a label any more, and if a
                        // font scale ever pushes it there the ellipsis is the
                        // honest failure rather than an invented nickname.
                        BasicText(
                            ModeColors.name(mode),
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.W800,
                                color = labelColor,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 8.sp,
                                maxFontSize = 12.5.sp,
                                stepSize = 0.5.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The control's height at the default text size.
 *
 * 34dp originally, raised seven percent to 36.5dp because it read as cramped
 * against the navigation bar under it (#69). Everything else in the control is
 * positioned against this, including the sliding thumb, so it is one number.
 */
private val BASE_HEIGHT = 36.5.dp
