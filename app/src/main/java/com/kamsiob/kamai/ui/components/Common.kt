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

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    /** Destructive rows carry the amber label. */
    destructive: Boolean = false,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = KamTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (pressed) colors.surfaceSecondary else Color.Transparent)
                .then(
                    if (onClick != null && enabled) {
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (destructive) colors.goldText else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = KamTheme.type.bodyEmphasis,
                    color = when {
                        !enabled -> colors.textTertiary
                        destructive -> colors.goldText
                        else -> colors.textPrimary
                    },
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = KamTheme.type.secondary, color = colors.textSecondary)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Text(trailing, style = KamTheme.type.mono, color = colors.textTertiary)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = colors.border,
                thickness = 1.dp,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/**
 * Softly fades a scrolling list at its top and bottom edges.
 *
 * Uses a destination-in blend so the fade is a real mask over whatever is
 * behind, rather than a gradient painted in the background colour, which breaks
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

/** Small dark pill, bottom centre, one line, confirming an action. */
@Composable
fun KamToast(
    message: String?,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            Text(
                text = message.orEmpty(),
                style = KamTheme.type.label,
                color = Color(0xFFF2FBF4),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            )
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
    showDivider: Boolean = true,
) {
    val colors = KamTheme.colors
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .defaultMinSize(minHeight = KamTheme.dimens.minTouchTarget)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = KamTheme.type.bodyEmphasis, color = colors.textPrimary)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = KamTheme.type.secondary, color = colors.textSecondary)
                }
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    uncheckedThumbColor = colors.textTertiary,
                    uncheckedTrackColor = colors.surfaceSecondary,
                    uncheckedBorderColor = colors.border,
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
