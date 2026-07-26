package com.kamsiob.kamai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The quiet support entry at the top of Settings (#94).
 *
 * The full ask lives at the bottom of the screen and stays exactly as it is. This
 * is a signpost, so it has to be findable without competing with the settings it
 * sits above. Smaller padding, tighter gaps, a smaller tile than a settings row
 * icon, a title one step down from a section title, and less button height than a
 * primary action anywhere else. A compact note, not a panel.
 *
 * **The treatment is the part that goes wrong.** The gold ground is a single flat
 * colour, never a gradient. Depth comes from light on the edges instead: a one
 * pixel white highlight along the top inner edge and a barely visible darker line
 * along the bottom, which is how a slightly raised surface behaves. The tile and
 * the button carry the same treatment at a smaller scale.
 *
 * A gradient reads as decoration and draws attention to itself. Flat colour with
 * light on the edges reads as a solid object catching light, which is quieter and
 * looks considered. No glow, no sheen bands, nothing else.
 *
 * The button is the only element at full gold saturation, so it is the one thing
 * that draws the eye. If the ground ever competes with the groups below, it is too
 * strong.
 */
@Composable
fun SupportSignpost(onSupport: () -> Unit, modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // Flat, and soft enough to read as a warm tint rather than a coloured
            // block. amberFill is the palette's soft gold, already reserved.
            .background(colors.amberFill)
            .edgeLit(shape)
            // Tighter than a standard card, deliberately.
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    // Smaller than a settings row's icon tile.
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.flagAmber)
                    .edgeLit(RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Support this work",
                // One step down from a section title, per the size rule.
                style = KamTheme.type.bodyEmphasis,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            // Approved wording. Not to be rewritten: it opens on what the user
            // gets rather than on the product or the maker, names the three things
            // they are not subjected to, and frames helping as optional and as
            // preserving something that benefits them.
            "Yours to use, free, with no ads, no tracking, and nothing to subscribe " +
                "to. If you'd like to help keep it that way, the door's open.",
            style = KamTheme.type.secondary,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                // Not full width. A full-width saturated button is what turns this
                // from a note into a panel, and the panel is at the bottom of the
                // screen already.
                .clip(RoundedCornerShape(14.dp))
                .background(colors.flagAmber)
                .edgeLit(RoundedCornerShape(14.dp))
                .clickable(onClick = onSupport)
                // Less vertical padding than a primary action elsewhere.
                .padding(horizontal = 18.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                // Not donate, tip or give. Not the title repeated. No coffee.
                "Fund the work",
                style = KamTheme.type.label,
                color = colors.onAccent,
            )
        }
    }
}

/**
 * A one pixel highlight along the top inner edge and a barely visible darker line
 * along the bottom.
 *
 * Drawn as two very short vertical gradients pinned to the edges rather than one
 * gradient across the whole surface, which is the distinction the design turns on:
 * the fill stays flat and only the edges catch light.
 */
private fun Modifier.edgeLit(shape: androidx.compose.ui.graphics.Shape): Modifier = this
    .background(
        brush = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = 0.22f),
            0.02f to Color.Transparent,
            0.98f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.07f),
        ),
        shape = shape,
    )
