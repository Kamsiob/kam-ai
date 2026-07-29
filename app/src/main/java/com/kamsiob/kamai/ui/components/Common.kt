package com.kamsiob.kamai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.standardSpec

// The shared pieces every screen is built from. DESIGN.md sections 5 and 7.

/**
 * A card: 20 to 24dp radius, one hairline border, soft shadow per theme.
 *
 * Shadow is drawn as a border plus elevation rather than a heavy drop shadow.
 * DESIGN.md rejects heavy black shadows in dark mode outright, because they read
 * as dirty translucent boxes.
 */
@Composable
fun KamCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(KamTheme.dimens.cardRadius),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = KamTheme.colors
    val base = modifier
        .clip(shape)
        .background(colors.surface)
        .border(BorderStroke(1.dp, colors.border), shape)

    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
    ) {
        content()
    }
}

/** The primary action: a filled accent pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The one amber element on a screen, used only for Support this work. */
    amber: Boolean = false,
) {
    val colors = KamTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = standardSpec(),
        label = "press",
    )

    val background = when {
        !enabled -> colors.surfaceSecondary
        amber -> colors.flagAmber
        else -> colors.accent
    }
    val foreground = when {
        !enabled -> colors.textTertiary
        else -> colors.onAccent
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = KamTheme.dimens.minTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KamTheme.type.label, color = foreground, textAlign = TextAlign.Center)
    }
}

/** The secondary action: a bordered surface pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = KamTheme.colors
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = KamTheme.dimens.minTouchTarget)
            .clip(CircleShape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.border), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = KamTheme.type.label,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = KamTheme.dimens.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KamTheme.type.label, color = KamTheme.colors.accent)
    }
}

/** A small pill. Tonal green for positive facts, neutral for plain statements. */
@Composable
fun KamChip(
    text: String,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    mono: Boolean = false,
) {
    val colors = KamTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (tonal) colors.tonalFill else colors.surfaceSecondary)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            style = if (mono) KamTheme.type.mono else KamTheme.type.secondary,
            color = if (tonal) colors.tonalText else colors.textSecondary,
        )
    }
}

/**
 * The small mono uppercase label above a settings group or a Discover card.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = KamTheme.type.eyebrow,
        color = KamTheme.colors.textTertiary,
        modifier = modifier,
    )
}

/**
 * Rows within a settings section share one card, separated by hairline
 * dividers, with a pressed-state tint. Never a floating card per row.
 */
@Composable
fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Eyebrow(label, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        KamCard {
            Column { content() }
        }
    }
}

/**
 * What sits in a row's trailing slot (#95).
 *
 * Exactly one of these, and **never a value, a size or a count**. That single rule
 * is what keeps the right edge of every row in the app aligned, and breaking it is
 * what made an earlier attempt look broken: a chevron on one row and "5.0 GB" on
 * the next put the two edges in different places and the card read as ragged.
 *
 * Values go in the description line beneath the row name instead, where there is
 * room for them and where a figure can sit in the mono face without fighting the
 * row's alignment.
 */
sealed interface RowTrailing {
    /** The row opens something. */
    data object Navigate : RowTrailing

    /** An on or off setting. The description says what off means. */
    data class Toggle(val on: Boolean, val onChange: (Boolean) -> Unit) : RowTrailing

    /** One choice among several mutually exclusive ones. */
    data class Choice(val selected: Boolean) : RowTrailing

    /** Nothing: the row acts on tap and has nowhere to go. */
    data object None : RowTrailing
}

/**
 * The fixed width of that slot, the same on every row in the application.
 *
 * Sized to the widest thing it can hold, which is the switch at 52dp rather than
 * the chevron at 20. At 40dp a toggle row was clipped on both sides. Nothing used
 * one yet, so it had never been seen, and sizing this to the narrowest control
 * would have made the first toggle row look broken instead.
 */
private val TRAILING_SLOT = 52.dp

/** The icon column's width, which the dividers start after. */
private val ICON_COLUMN = 30.dp + 14.dp

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    /**
     * The line beneath the name. This is where values, sizes, counts and status
     * go, never the trailing slot (#95). Set [monoTail] when it ends in a figure.
     */
    subtitle: String? = null,
    /**
     * A figure appended to the subtitle in the utility mono face, so it reads as
     * data while the words around it stay in the body face.
     */
    monoTail: String? = null,
    icon: ImageVector? = null,
    /** The tile color. Chosen for separation from the rows around it. */
    tile: com.kamsiob.kamai.ui.theme.TileColor = com.kamsiob.kamai.ui.theme.TileColor.Slate,
    trailing: RowTrailing = RowTrailing.Navigate,
    /** Destructive rows carry a brick tile and a brick name. */
    destructive: Boolean = false,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = KamTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tileColor = (if (destructive) com.kamsiob.kamai.ui.theme.TileColor.Brick else tile)
        .of(colors.isDark)

    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (pressed) colors.surfaceSecondary else Color.Transparent)
                .then(
                    // A toggle row is toggleable, not clickable, and that is an
                    // accessibility fix rather than a tidy-up (#144).
                    //
                    // With `clickable` the row node carried its name and an OnClick
                    // and no state, while the Switch inside carried the state and no
                    // name, on a separate node. A screen reader therefore read "Note
                    // under each answer, off means no line saying ..." without ever
                    // saying which way it was set, then an unlabeled switch. Every
                    // toggle row in the app read that way, not just the one that
                    // found it: "Confirm before deleting a chat" had the same defect.
                    //
                    // `toggleable` puts ToggleableState and Role.Switch on the same
                    // node as the text, so the announcement is the name, the
                    // description and the state together. Caught by
                    // MemoryNoteToggleSemanticsTest, which failed on exactly this.
                    if (trailing is RowTrailing.Toggle && enabled) {
                        Modifier.toggleable(
                            value = trailing.on,
                            onValueChange = trailing.onChange,
                            role = Role.Switch,
                            interactionSource = interaction,
                            indication = null,
                        )
                        // A choice row has exactly the same defect available to it, and
                        // it is fixed here before it can happen rather than after. The
                        // dot in the trailing slot is the only thing carrying "this is
                        // the selected one", and a dot is nothing to a screen reader. So
                        // the row is `selectable` with Role.RadioButton, putting the
                        // selected state on the same node as the name.
                        //
                        // Nothing uses RowTrailing.Choice yet, which is exactly why this
                        // is worth doing now: the toggle defect existed for as long as it
                        // did because the first toggle row shipped with it, and then
                        // every later one copied it.
                    } else if (trailing is RowTrailing.Choice && enabled && onClick != null) {
                        Modifier.selectable(
                            selected = trailing.selected,
                            onClick = onClick,
                            role = Role.RadioButton,
                            interactionSource = interaction,
                            indication = null,
                        )
                    } else if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .defaultMinSize(minHeight = KamTheme.dimens.minTouchTarget)
                // The same vertical padding on every row. A row with no
                // description is simply shorter rather than padded to match its
                // neighbours.
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(tileColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        // The tile is decoration for scanning. A screen reader
                        // gets the row name and its state, never this.
                        contentDescription = null,
                        tint = com.kamsiob.kamai.ui.theme.TileGlyph,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = KamTheme.type.bodyEmphasis,
                    color = when {
                        !enabled -> colors.textTertiary
                        destructive -> com.kamsiob.kamai.ui.theme.TileColor.Brick.of(colors.isDark)
                        else -> colors.textPrimary
                    },
                )
                if (subtitle != null || monoTail != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = KamTheme.type.secondary,
                                color = colors.textTertiary,
                            )
                        }
                        if (monoTail != null) {
                            if (subtitle != null) Spacer(Modifier.width(6.dp))
                            Text(
                                monoTail,
                                style = KamTheme.type.mono,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.width(TRAILING_SLOT),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when (trailing) {
                    RowTrailing.Navigate -> Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    // `onCheckedChange = null` on purpose: the row owns the gesture
                    // and the semantics now, so the switch is the picture of the
                    // state rather than a second control beside the first. Left
                    // interactive it stayed independently focusable and a screen
                    // reader still found an unlabeled switch after the row.
                    is RowTrailing.Toggle -> androidx.compose.material3.Switch(
                        checked = trailing.on,
                        onCheckedChange = null,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = colors.onAccent,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surfaceSecondary,
                            uncheckedBorderColor = colors.border,
                        ),
                    )
                    is RowTrailing.Choice -> RadioDot(selected = trailing.selected)
                    RowTrailing.None -> Unit
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = colors.border,
                thickness = 1.dp,
                // Starts at the text column, so the icon column reads as an
                // unbroken rail down the card.
                modifier = Modifier.padding(start = 14.dp + ICON_COLUMN),
            )
        }
    }
}

/** The radio indicator for one choice among several. */
@Composable
private fun RadioDot(selected: Boolean) {
    val colors = KamTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accent else Color.Transparent)
            .then(
                if (selected) Modifier else Modifier.border(2.dp, colors.border, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * Softly fades a scrolling list at its top and bottom edges.
 *
 * Uses a destination-in blend so the fade is a real mask over whatever is
 * behind, rather than a gradient painted in the background color, which breaks
 * the moment the surface underneath is not the background.
 */
fun Modifier.edgeFade(
    top: Boolean = true,
    bottom: Boolean = true,
    height: Dp = KamTheme.dimens.edgeFade,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val px = height.toPx()
        if (top) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = px,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - px,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * The same mask, sideways, for horizontally scrolling rows of chips.
 *
 * Its own function rather than more parameters on [edgeFade], because a caller
 * wants one or the other and a four-boolean version reads as a puzzle. The
 * blend-mode reasoning above applies unchanged: this masks whatever is behind
 * rather than painting the background color over it, so it survives sitting on
 * a card as well as on the page. Issue #29.
 */
fun Modifier.edgeFadeHorizontal(
    start: Boolean = false,
    end: Boolean = true,
    width: Dp = KamTheme.dimens.edgeFade,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val px = width.toPx()
        if (start) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startX = 0f,
                    endX = px,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (end) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - px,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Every list has an empty state, written as an invitation to the obvious next
 * action. Never a blank screen.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KamMark(size = 34.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = KamTheme.type.cardTitle,
            color = KamTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = KamTheme.type.body,
            color = KamTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** Small dark pill, bottom center, one line, confirming an action. */
@Composable
fun KamToast(
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1B241E).copy(alpha = 0.94f),
            // A toast is the app's whole answer to "did that work?", and it was
            // visual only: every confirmation, every undo offer, and every
            // failure notice went unannounced. Polite, so it waits its turn
            // rather than cutting across what is being read.
            modifier = Modifier
                .padding(bottom = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.orEmpty(),
                    style = KamTheme.type.label,
                    color = Color(0xFFF2FBF4),
                    modifier = Modifier.padding(
                        start = 18.dp,
                        end = if (actionLabel == null) 18.dp else 10.dp,
                        top = 11.dp,
                        bottom = 11.dp,
                    ),
                )
                // An optional action, for the things that are worth undoing rather
                // than merely announcing. Auto-archive (#31) is the first: it moves
                // conversations without being asked each time, so the confirmation
                // has to carry the way back.
                if (actionLabel != null && onAction != null) {
                    // The same light color as the message rather than the accent.
                    // This surface is a fixed dark green whatever the theme, and
                    // the accent is one of sixteen user-chosen colors, none of
                    // which has been contrast-checked against it. Weight and the
                    // tap target carry the affordance instead, so color is not
                    // doing the work alone either way.
                    Text(
                        text = actionLabel,
                        style = KamTheme.type.label.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        ),
                        color = Color(0xFFF2FBF4),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onAction)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}

/** A settings row with a trailing switch, sharing the group card styling. */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tile: com.kamsiob.kamai.ui.theme.TileColor = com.kamsiob.kamai.ui.theme.TileColor.Slate,
    showDivider: Boolean = true,
) {
    // Delegates rather than duplicating. This was a parallel implementation of a
    // row, and it had drifted: no icon slot, padding of 16 against 14, and a
    // divider starting at the card edge instead of the text column. Three ways
    // for a toggle row to look subtly unlike every row above it (#95).
    SettingsRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        icon = icon,
        tile = tile,
        trailing = RowTrailing.Toggle(checked, onCheckedChange),
        showDivider = showDivider,
        onClick = { onCheckedChange(!checked) },
    )
}
