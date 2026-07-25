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
import androidx.compose.ui.text.TextStyle
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
 * a bubble. Three parts, all in the mode's own colour: a faint wash, a
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
            // The wash. Faint enough to read as paper rather than as a panel, and
            // fading to nothing well before the composer so it never looks like a
            // surface the input is sitting on.
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = if (colors.isDark) 0.10f else 0.07f),
                        Color.Transparent,
                    ),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 44.dp)
            .clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(96.dp)) { drawModeSketch(mode, tint) }
        Spacer(Modifier.height(22.dp))
        Text(
            text = nudgeLine(mode),
            style = nudgeStyle(mode),
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
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

// The sketches. Drawn rather than shipped as assets so they take the mode colour
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
