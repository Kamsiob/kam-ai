package com.kamsiob.kamai.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.components.Eyebrow
import com.kamsiob.kamai.ui.theme.Accent
import com.kamsiob.kamai.ui.theme.AccentGroup
import com.kamsiob.kamai.ui.theme.Accents
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.ThemeMode
import com.kamsiob.kamai.ui.theme.expressiveSpec

/**
 * Appearance: theme mode and accent colour. Both apply instantly and glide
 * through the standard theme crossfade.
 */
@Composable
fun AppearanceScreen(
    themeMode: ThemeMode,
    accentId: String,
    isDark: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = KamTheme.dimens.screenPadding),
    ) {
        Text("Appearance", style = KamTheme.type.screenTitle, color = colors.textPrimary)
        Spacer(Modifier.height(18.dp))

        Eyebrow("Theme")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.surfaceSecondary)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == themeMode
                val bg by androidx.compose.animation.animateColorAsState(
                    if (selected) colors.surface else Color.Transparent,
                    label = "theme-thumb",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onThemeMode(mode) }
                        .padding(vertical = 11.dp)
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        style = KamTheme.type.label,
                        color = if (selected) colors.textPrimary else colors.textTertiary,
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Eyebrow("Accent colour")
        Spacer(Modifier.height(4.dp))
        Text(
            "The amber for bookmarks and the support button stays as it is, whichever " +
                "accent you pick.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(14.dp))

        AccentBlock("Brighter", Accents.bright, accentId, isDark, onAccent)
        Spacer(Modifier.height(18.dp))
        AccentBlock("Earthier", Accents.earthy, accentId, isDark, onAccent)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun AccentBlock(
    label: String,
    accents: List<Accent>,
    selectedId: String,
    isDark: Boolean,
    onAccent: (String) -> Unit,
) {
    Column {
        Text(label, style = KamTheme.type.mono, color = KamTheme.colors.textTertiary)
        Spacer(Modifier.height(10.dp))
        // A plain non-scrolling grid: four across, two rows per group.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            accents.chunked(4).forEach { rowAccents ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowAccents.forEach { accent ->
                        Swatch(
                            accent = accent,
                            selected = accent.id == selectedId,
                            isDark = isDark,
                            onClick = { onAccent(accent.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    accent: Accent,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    // Shows the actual shade for the current theme, so the preview is honest.
    val shade = if (isDark) accent.darkAccent else accent.lightAccent
    val onShade = if (isDark) accent.darkOnAccent else accent.lightOnAccent

    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 1f,
        animationSpec = expressiveSpec(),
        label = "swatch",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(shade)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.textPrimary else colors.border,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (selected) "${accent.name}, selected" else accent.name
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = onShade,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
