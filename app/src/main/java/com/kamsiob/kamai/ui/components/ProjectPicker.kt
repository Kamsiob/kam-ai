package com.kamsiob.kamai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * Picks which project a conversation belongs to. Moving applies from here on, not
 * retroactively, which the dialog states plainly.
 */
@Composable
fun ProjectPickerDialog(
    options: List<Pair<String, String>>,
    currentProjectId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    /** Heading, so the bulk version can say how many it is about to move. */
    title: String = "Move to project",
    /** Offers "Chats, no project" as a destination. The chat header has its own
     *  Remove from project menu item; a bulk move has nowhere else to put it. */
    allowNone: Boolean = false,
) {
    val colors = KamTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)).padding(20.dp),
        ) {
            Text(title, style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "The project's instructions apply from now on, not to messages already sent.",
                style = KamTheme.type.secondary, color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            if (options.isEmpty()) {
                Text(
                    "No projects yet. Make one in the Projects tab first.",
                    style = KamTheme.type.body, color = colors.textSecondary,
                )
            } else {
                options.forEach { (id, name) ->
                    val selected = id == currentProjectId
                    Text(
                        name,
                        style = KamTheme.type.body,
                        color = if (selected) colors.accent else colors.textPrimary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(id) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
            }
            if (allowNone) {
                Text(
                    "Chats, no project",
                    style = KamTheme.type.body,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(null) }.padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancel", style = KamTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
