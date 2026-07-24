package com.kamsiob.kamai.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kamsiob.kamai.data.ConversationSummary
import com.kamsiob.kamai.data.ProjectEntity
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.components.IconAction
import com.kamsiob.kamai.ui.components.PrimaryButton
import com.kamsiob.kamai.ui.components.SecondaryButton
import com.kamsiob.kamai.ui.theme.KamTheme

/**
 * The Projects tab (item 2). A project is a named container with its own standing
 * instructions and its own set of conversations. This screen lists them and makes
 * new ones.
 */
@Composable
fun ProjectsScreen(
    projects: List<ProjectEntity>,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pad = KamTheme.dimens.screenPadding
    var creating by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = pad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Projects", style = KamTheme.type.screenTitle, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            Text(
                "New project", style = KamTheme.type.label, color = colors.accent,
                modifier = Modifier.clip(CircleShape).clickable { creating = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A project keeps its own instructions and its own chats together. Every chat inside " +
                "a project follows that project's instructions.",
            style = KamTheme.type.body, color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        if (projects.isEmpty()) {
            EmptyState(
                title = "No projects yet",
                body = "Make one to group chats around a topic and give them shared instructions, " +
                    "like a project in a notebook.",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
                            .clickable { onOpen(project.id) }
                            .padding(15.dp),
                    ) {
                        Text(project.name, style = KamTheme.type.cardTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = project.instructions.trim().ifBlank { "No instructions yet" }
                        Spacer(Modifier.height(4.dp))
                        Text(sub, style = KamTheme.type.secondary, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New project",
            initial = "",
            onConfirm = { creating = false; onCreate(it) },
            onDismiss = { creating = false },
        )
    }
}

/**
 * A single project: rename and delete, its instructions, and its chats. Moving a
 * chat in or out applies from that point on, never retroactively.
 */
@Composable
fun ProjectScreen(
    project: ProjectEntity?,
    conversations: List<ConversationSummary>,
    instructionsMax: Int,
    onSaveInstructions: (String) -> Unit,
    onRename: (String) -> Unit,
    onNewChatHere: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRemoveFromProject: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pad = KamTheme.dimens.screenPadding
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var instructions by remember(project?.id, project?.instructions) {
        mutableStateOf(TextFieldValue(project?.instructions.orEmpty()))
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = pad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                project?.name ?: "Project",
                style = KamTheme.type.screenTitle, color = colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box {
                IconAction(icon = Icons.Rounded.MoreHoriz, description = "Project options", onClick = { menuOpen = true })
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.surface,
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Rename", style = KamTheme.type.body, color = colors.textPrimary) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete project", style = KamTheme.type.body, color = colors.goldText) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Instructions", style = KamTheme.type.label, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Applied to every chat in this project, and only to this project.",
            style = KamTheme.type.secondary, color = colors.textTertiary,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)).padding(14.dp),
        ) {
            if (instructions.text.isEmpty()) {
                Text("For example: you are helping me write a mystery novel set in 1920s Cairo.",
                    style = KamTheme.type.body, color = colors.textTertiary)
            }
            BasicTextField(
                value = instructions,
                onValueChange = { if (it.text.length <= instructionsMax) instructions = it },
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Save instructions", onClick = { onSaveInstructions(instructions.text.trim()) }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))
        SecondaryButton("New chat in this project", onClick = onNewChatHere, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))
        Text("Chats in this project", style = KamTheme.type.label, color = colors.textSecondary)
        Spacer(Modifier.height(8.dp))

        if (conversations.isEmpty()) {
            Text(
                "No chats here yet. Start one above, or move an existing chat into this project " +
                    "from its options.",
                style = KamTheme.type.secondary, color = colors.textTertiary,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(conversations, key = { it.id }) { row ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(KamTheme.dimens.cardRadius))
                            .clickable { onOpenConversation(row.id) }
                            .padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            row.title ?: row.snippet?.take(40) ?: "Untitled chat",
                            style = KamTheme.type.cardTitle, color = colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Remove", style = KamTheme.type.label, color = colors.accent,
                            modifier = Modifier.clip(CircleShape).clickable { onRemoveFromProject(row.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rename project",
            initial = project?.name.orEmpty(),
            onConfirm = { renaming = false; onRename(it) },
            onDismiss = { renaming = false },
        )
    }
}

/** A small name dialog used for creating and renaming projects. */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    var text by remember { mutableStateOf(TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)).padding(22.dp),
        ) {
            Text(title, style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surfaceSecondary)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.text.isEmpty()) {
                    Text("Project name", style = KamTheme.type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("Cancel", style = KamTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Save", style = KamTheme.type.label,
                    color = if (text.text.isNotBlank()) colors.accent else colors.textTertiary,
                    modifier = Modifier.clip(CircleShape)
                        .then(if (text.text.isNotBlank()) Modifier.clickable { onConfirm(text.text.trim()) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
