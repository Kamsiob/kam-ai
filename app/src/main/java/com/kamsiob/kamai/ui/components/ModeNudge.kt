package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.ModeColors

/**
 * The empty state of a new conversation, per mode. DESIGN.md section 7, issue
 * #29.
 *
 * A screen-owned nudge rather than a message: nothing here is ever mistaken for
 * something the model said, which is why it is drawn on the page rather than in
 * a bubble. Three parts, all in the mode's own color: a faint wash, a
 * hand-drawn double-stroke sketch, and one line of type in the voice that mode
 * speaks in.
 *
 * Not used by the assistant overlay or by Discover. Neither is a mode a user
 * picks, and the overlay is a panel over somebody else's screen where a large
 * decorative nudge would be an intrusion rather than an invitation.
 *
 * The whole thing is decorative to a screen reader. The sketch carries no
 * information the line does not, and the line is read once rather than twice.
 */
@Composable
fun ModeNudge(mode: Mode, modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    val tint = ModeColors.of(mode, colors.isDark)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // A glow radiating from behind the sketch, not a wash with edges.
            //
            // This was a vertical gradient that started solid at the top and
            // faded down. That worked while the nudge was pinned under the
            // header, and the moment it moved to the middle of the page the top
            // of the gradient became a hard horizontal line across the screen.
            //
            // Radial has no edge to notice: strongest behind the sketch, gone
            // well before the sides. The stops are deliberately close together
            // near the center and long at the tail, since a linear falloff still
            // reads as a disc. Alpha is set per theme because the same value
            // that is barely visible on the dark background is a gray smudge on
            // the light one.
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to tint.copy(alpha = if (colors.isDark) 0.16f else 0.11f),
                        0.35f to tint.copy(alpha = if (colors.isDark) 0.09f else 0.06f),
                        0.65f to tint.copy(alpha = if (colors.isDark) 0.03f else 0.02f),
                        1.0f to Color.Transparent,
                    ),
                    radius = GLOW_RADIUS_PX,
                ),
            )
            // Generous vertical padding so the glow reaches nothing well before the
            // box ends. Too little and the gradient is still faintly lit where it
            // gets clipped, which puts back the hard edge this was meant to remove.
            .padding(horizontal = 32.dp, vertical = 110.dp)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Softened deliberately. This is an invitation on an empty page, not a
        // banner: it should be the quietest thing on screen and get out of the
        // way the moment there is a conversation to read.
        Canvas(Modifier.size(96.dp).graphicsLayer { alpha = SKETCH_ALPHA }) {
            drawModeSketch(mode, tint)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = nudgeLine(mode),
            style = nudgeStyle(mode),
            color = tint.copy(alpha = LINE_ALPHA),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        // What the mode is for and how to use it, one sentence. The line above
        // sets the tone; this says what to actually do, for somebody who has met
        // the mode for the first time and has an empty box in front of them.
        Text(
            text = nudgeHelp(mode),
            style = KamTheme.type.secondary.copy(fontStyle = FontStyle.Italic),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * How wide the glow reaches, in pixels.
 *
 * Fixed rather than derived from the layout, because the nudge is only as tall
 * as its content and a radius tied to that would shrink the glow on the shortest
 * mode line. This is comfortably wider than the sketch and narrower than a phone.
 */
private const val GLOW_RADIUS_PX = 520f

/** How faint the sketch and the line are. Quiet enough to read as paper. */
private const val SKETCH_ALPHA = 0.55f
private const val LINE_ALPHA = 0.75f

/**
 * One italic line under the nudge: what the mode is for, and what to do next.
 *
 * Plain and practical rather than poetic. The line above it already carries the
 * mode's voice, so this one earns its place by being useful.
 */
private fun nudgeHelp(mode: Mode): String = when (mode) {
    Mode.LOGIC ->
        "Type a claim or an opinion you hold. It argues the other side and tells you where the " +
            "reasoning is weak."
    Mode.BRAINSTORM ->
        "Say what you are working on, however rough. It asks questions and runs exercises instead " +
            "of handing you ideas."
    Mode.BENCH ->
        "Paste text above and pick a change, or say what to do with it. The before and after both " +
            "stay here."
    else ->
        "Ask a question, paste something in, or talk it out with the microphone. Switch modes any " +
            "time from the name below."
}

/** DESIGN.md section 7, word for word. */
private fun nudgeLine(mode: Mode): String = when (mode) {
    Mode.LOGIC -> "What claim do you want tested?"
    Mode.BRAINSTORM -> "All right. What have you got?"
    Mode.BENCH -> "The result lands here."
    else -> "So. What's on your mind?"
}

@Composable
private fun nudgeStyle(mode: Mode): TextStyle = when (mode) {
    // The one italic serif in the app. Its subset contains exactly the glyphs of
    // the Brainstorm line, so this style must not be used for anything else.
    Mode.BRAINSTORM -> KamTheme.type.nudgeSerif
    // Mono, because it describes where machine output will appear.
    Mode.BENCH -> KamTheme.type.nudgeMono
    else -> KamTheme.type.nudge
}

// The sketches. Drawn rather than shipped as assets so they take the mode color
// directly and cost nothing in the APK.
//
// "Double stroke" is the hand-drawn part: every shape is drawn twice, the second
// pass slightly offset and lighter, the way a pen goes round a line again. The
// offset is deliberately not symmetric, because a uniform one reads as a printing
// error rather than as a hand.

private fun DrawScope.drawModeSketch(mode: Mode, tint: Color) {
    val build: (Path.() -> Unit) = when (mode) {
        Mode.LOGIC -> ({ scales(this@drawModeSketch.size) })
        Mode.BRAINSTORM -> ({ brain(this@drawModeSketch.size) })
        Mode.BENCH -> ({ anvil(this@drawModeSketch.size) })
        else -> ({ bubble(this@drawModeSketch.size) })
    }
    val path = Path().apply(build)
    val w = size.minDimension

    // The under-stroke: offset down and right, lighter, drawn first so the main
    // line sits on top of it.
    translate(w * 0.018f, w * 0.026f) {
        drawPath(
            path = path,
            color = tint.copy(alpha = 0.34f),
            style = Stroke(
                width = w * 0.030f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
    drawPath(
        path = path,
        color = tint.copy(alpha = 0.85f),
        style = Stroke(
            width = w * 0.034f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** General: a chat bubble with a tail, corners deliberately uneven. */
private fun Path.bubble(size: Size) {
    val w = size.width
    val h = size.height
    moveTo(w * 0.20f, h * 0.26f)
    cubicTo(w * 0.19f, h * 0.19f, w * 0.26f, h * 0.17f, w * 0.34f, h * 0.17f)
    lineTo(w * 0.68f, h * 0.18f)
    cubicTo(w * 0.79f, h * 0.18f, w * 0.83f, h * 0.24f, w * 0.82f, h * 0.34f)
    lineTo(w * 0.82f, h * 0.55f)
    cubicTo(w * 0.83f, h * 0.65f, w * 0.76f, h * 0.68f, w * 0.66f, h * 0.68f)
    lineTo(w * 0.42f, h * 0.68f)
    lineTo(w * 0.29f, h * 0.82f)
    lineTo(w * 0.31f, h * 0.67f)
    cubicTo(w * 0.22f, h * 0.66f, w * 0.19f, h * 0.60f, w * 0.20f, h * 0.50f)
    close()
}

/** Logic Partner: balance scales, the beam very slightly off level. */
private fun Path.scales(size: Size) {
    val w = size.width
    val h = size.height
    // Upright and base.
    moveTo(w * 0.50f, h * 0.20f)
    lineTo(w * 0.50f, h * 0.74f)
    moveTo(w * 0.34f, h * 0.78f)
    lineTo(w * 0.66f, h * 0.78f)
    // The beam, tipped a little so it reads as weighing rather than as settled.
    moveTo(w * 0.20f, h * 0.30f)
    lineTo(w * 0.80f, h * 0.25f)
    // Left pan and its hanger.
    moveTo(w * 0.21f, h * 0.30f)
    lineTo(w * 0.21f, h * 0.44f)
    moveTo(w * 0.09f, h * 0.44f)
    cubicTo(w * 0.11f, h * 0.58f, w * 0.31f, h * 0.58f, w * 0.33f, h * 0.44f)
    close()
    // Right pan, hanging higher.
    moveTo(w * 0.79f, h * 0.25f)
    lineTo(w * 0.79f, h * 0.38f)
    moveTo(w * 0.67f, h * 0.38f)
    cubicTo(w * 0.69f, h * 0.52f, w * 0.89f, h * 0.52f, w * 0.91f, h * 0.38f)
    close()
}

/** Brainstorm: a brain, drawn as two lobed halves and a dividing fold. */
private fun Path.brain(size: Size) {
    val w = size.width
    val h = size.height
    // Outer shape.
    moveTo(w * 0.50f, h * 0.19f)
    cubicTo(w * 0.34f, h * 0.13f, w * 0.19f, h * 0.24f, w * 0.22f, h * 0.36f)
    cubicTo(w * 0.10f, h * 0.44f, w * 0.14f, h * 0.60f, w * 0.26f, h * 0.64f)
    cubicTo(w * 0.27f, h * 0.77f, w * 0.41f, h * 0.83f, w * 0.50f, h * 0.76f)
    cubicTo(w * 0.59f, h * 0.83f, w * 0.73f, h * 0.77f, w * 0.74f, h * 0.64f)
    cubicTo(w * 0.86f, h * 0.60f, w * 0.90f, h * 0.44f, w * 0.78f, h * 0.36f)
    cubicTo(w * 0.81f, h * 0.24f, w * 0.66f, h * 0.13f, w * 0.50f, h * 0.19f)
    close()
    // The fold down the middle, and one convolution either side.
    moveTo(w * 0.50f, h * 0.19f)
    lineTo(w * 0.50f, h * 0.76f)
    moveTo(w * 0.34f, h * 0.33f)
    cubicTo(w * 0.42f, h * 0.38f, w * 0.40f, h * 0.48f, w * 0.32f, h * 0.52f)
    moveTo(w * 0.66f, h * 0.33f)
    cubicTo(w * 0.58f, h * 0.38f, w * 0.60f, h * 0.48f, w * 0.68f, h * 0.52f)
}

/** Workbench: an anvil with a hammer resting across it. */
private fun Path.anvil(size: Size) {
    val w = size.width
    val h = size.height
    // Anvil body: horn on the left, stepped base.
    moveTo(w * 0.16f, h * 0.56f)
    cubicTo(w * 0.26f, h * 0.50f, w * 0.32f, h * 0.50f, w * 0.38f, h * 0.51f)
    lineTo(w * 0.78f, h * 0.51f)
    lineTo(w * 0.78f, h * 0.60f)
    lineTo(w * 0.60f, h * 0.62f)
    lineTo(w * 0.62f, h * 0.76f)
    lineTo(w * 0.80f, h * 0.80f)
    lineTo(w * 0.28f, h * 0.80f)
    lineTo(w * 0.44f, h * 0.76f)
    lineTo(w * 0.46f, h * 0.62f)
    cubicTo(w * 0.32f, h * 0.62f, w * 0.22f, h * 0.60f, w * 0.16f, h * 0.56f)
    close()
    // Hammer: head and haft, angled as though just set down.
    moveTo(w * 0.34f, h * 0.30f)
    lineTo(w * 0.52f, h * 0.22f)
    moveTo(w * 0.30f, h * 0.24f)
    lineTo(w * 0.40f, h * 0.38f)
    moveTo(w * 0.35f, h * 0.21f)
    lineTo(w * 0.24f, h * 0.28f)
    lineTo(w * 0.31f, h * 0.39f)
    lineTo(w * 0.42f, h * 0.32f)
    close()
}
