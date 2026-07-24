package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.ModeColors

/**
 * Shared UI for the four modes: the identity icon and the small colour dot. The
 * icons are simple line glyphs, deliberately not the overused lightbulb (for
 * Brainstorm) or wrench (for Workbench), and no sparkles anywhere. See DESIGN.md,
 * Four-Mode Update Part 2.
 *
 * - General: a speech bubble.
 * - Logic: a balance scale.
 * - Brainstorm: a hub with spokes (the hub-and-spoke method), not a lightbulb.
 * - Workbench: lines of text, standing in for text being reworked, not a wrench.
 */
fun modeIcon(mode: Mode): ImageVector = when (mode) {
    Mode.GENERAL -> Icons.Rounded.ChatBubbleOutline
    Mode.LOGIC -> Icons.Rounded.Balance
    Mode.BRAINSTORM -> Icons.Rounded.Hub
    Mode.BENCH -> Icons.AutoMirrored.Rounded.Notes
    Mode.DISCOVER, Mode.OVERLAY -> Icons.Rounded.Explore
}

/** The tiny mode-identity dot used on chat rows, the segmented control, and the
 *  mode picker. Colour is never the only carrier of meaning: it always sits with
 *  a name or icon and carries a text label for accessibility at the call site. */
@Composable
fun ModeDot(mode: Mode, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ModeColors.of(mode, KamTheme.colors.isDark)),
    )
}
