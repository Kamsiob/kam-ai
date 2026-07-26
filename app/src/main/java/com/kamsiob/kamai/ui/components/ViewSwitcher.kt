package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.AppViewModel.ChatsView
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The three list densities, as a small segmented control of icons.
 *
 * Shared by Chats and Projects. It lived inside `ChatsScreen` as a private
 * composable, which is why Projects had no view control at all: the thing to
 * reuse was not reachable (#50). The enum is still called `ChatsView` because
 * renaming it touches a lot for no behavior, but it now means "how a list of
 * things is drawn" on both screens.
 */
@Composable
fun ViewSwitcher(current: ChatsView, onChange: (ChatsView) -> Unit) {
    val colors = KamTheme.colors
    val options = listOf(
        ChatsView.COMFORTABLE to (Icons.Rounded.ViewAgenda to "Comfortable list"),
        ChatsView.COMPACT to (Icons.Rounded.ViewList to "Compact list"),
        ChatsView.GRID to (Icons.Rounded.GridView to "Grid"),
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .padding(2.dp),
    ) {
        options.forEach { (option, iconAndLabel) ->
            val (icon, label) = iconAndLabel
            val selected = option == current
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onChange(option) }
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
