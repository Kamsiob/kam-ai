package com.kamsiob.kamai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The support entry at the top of Settings (#94, reworked in #105).
 *
 * It is the only ask on the screen now. The repeat at the bottom of Settings is
 * gone: a screen that asks at the top where it is seen and again at the bottom
 * reads as insisting, and one ask is the more persuasive of the two.
 *
 * **Why this was rebuilt rather than restyled.** The first version was a tile, a
 * paragraph and a button in a left-aligned stack, which is the arrangement you
 * get by default rather than the one you choose, and it looked it. The depth came
 * from a one pixel edge highlight, which at this size is invisible on a phone.
 * Nothing about it was wrong in the small; the composition was the problem.
 *
 * Four changes, none of which spend height:
 *
 * - **The card is the button.** No separate filled button, so the orphan sitting
 *   under the text is gone and the whole surface is one target instead of a
 *   panel containing a smaller thing to hit. The action reads as a label, which
 *   is honest, because tapping anywhere does it.
 * - **The action anchors the bottom right.** Ending the composition diagonally
 *   opposite where it starts is what makes it look arranged. Left-aligning
 *   everything is what made it look typed.
 * - **A motif, bleeding off the right edge.** One oversized heart, rotated a
 *   little, at about a tenth opacity, cropped by the card. It carries the visual
 *   interest that the edge highlight was failing to, costs no height at all, and
 *   is not a gradient.
 * - **A hairline border.** A defined edge reads as a considered object; a fill
 *   that stops reads as a colored rectangle.
 *
 * The gold ground stays a single flat color. The rule that a gradient reads as
 * decoration and draws attention to itself still holds, and is why the motif is
 * a cropped shape rather than a sheen.
 *
 * Slightly shorter than the version it replaces, which is the constraint: more
 * considered, not more prominent.
 */
@Composable
fun SupportSignpost(onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Flat, and soft enough to read as a warm tint rather than a colored
            // block. amberFill is the palette's soft gold, already reserved.
            .background(colors.amberFill)
            // Drawn over the fill and under the content, so the motif is tinted
            // by neither and cropped by the clip above.
            .border(1.dp, colors.flagAmber.copy(alpha = 0.28f), shape)
            .clickable(role = Role.Button, onClick = onSupport),
    ) {
        // The motif, in a wrapper that takes the card's size rather than adding
        // to it.
        //
        // Worth stating plainly because it caught me on the device: an oversized
        // child is still measured, so putting the icon straight in the card made
        // the card as tall as the icon. matchParentSize measures against the
        // parent without contributing to it, which is exactly what a decoration
        // should do. The card's height is set by its words, and nothing else.
        Box(Modifier.matchParentSize()) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = colors.flagAmber.copy(alpha = 0.15f),
                modifier = Modifier
                    // Pushed off the top right corner on purpose: a shape
                    // obviously continuing past the crop looks deliberate, where
                    // one that fits looks like a sticker. The bottom right is
                    // left alone because that is where the action sits, and the
                    // centerd version smudged it.
                    .align(Alignment.TopEnd)
                    .offset(x = 24.dp, y = (-46).dp)
                    .rotate(-16f)
                    .size(140.dp),
            )
        }

        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        // A circle, not a rounded square. The rounded-square icon
                        // tile is the single most generic element available, and
                        // this is the one place on the screen not to use it.
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.flagAmber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "Support this work",
                    style = KamTheme.type.bodyEmphasis,
                    color = colors.textPrimary,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                // Approved wording. Not to be rewritten: it opens on what the user
                // gets rather than on the product or the maker, names the three things
                // they are not subjected to, and frames helping as optional and as
                // preserving something that benefits them.
                "Yours to use, free, with no ads, no tracking, and nothing to subscribe " +
                    "to. If you'd like to help keep it that way, the door's open.",
                style = KamTheme.type.secondary,
                color = colors.textSecondary,
                // Enough clearance that the second line never runs under the
                // motif's lower lobe, and no more: a wider inset forced an ugly
                // two-word wrap onto the last line.
                modifier = Modifier.padding(end = 12.dp),
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Not donate, tip or give. Not the title repeated. No coffee.
                    "Fund the work",
                    style = KamTheme.type.label,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(5.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
