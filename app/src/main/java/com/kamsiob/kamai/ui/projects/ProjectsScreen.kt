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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items as gridItems
import com.kamsiob.kamai.ui.AppViewModel.ChatsView
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    /** Live chat count per project id, shown on each folder. */
    counts: Map<String, Int> = emptyMap(),
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
            "A project keeps its instructions, its background notes, and its chats together. " +
                "Every chat inside one follows those instructions and already knows those notes.",
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
            // Folders, not rows.
            //
            // Projects used to be drawn as a list that looked like the Chats log,
            // which said the wrong thing about what a project is: a chat is one
            // conversation and a project is a container holding several, with its
            // own instructions. Reported as reading like the chat log; it now
            // reads as a shelf of folders, two across, each showing its name, how
            // many chats are inside, and whether it has instructions yet.
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                gridItems(projects, key = { it.id }) { project ->
                    ProjectFolder(
                        project = project,
                        chatCount = counts[project.id] ?: 0,
                        onOpen = onOpen,
                    )
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
 * A single project: rename and delete, what it tells the model, and its chats.
 * Moving a chat in or out applies from that point on, never retroactively.
 *
 * One `LazyColumn` for the whole screen rather than a `Column` with a list at
 * the bottom. It used to be the latter, which meant the chats list had whatever
 * height was left after the editors above it, and every field added to the top
 * took room away from it. Adding notes (#2) would have left a project with two
 * long fields showing its chats through a slot two rows tall.
 */
@Composable
fun ProjectScreen(
    project: ProjectEntity?,
    conversations: List<ConversationSummary>,
    instructionsMax: Int,
    notesMax: Int,
    onSave: (instructions: String, notes: String) -> Unit,
    onRename: (String) -> Unit,
    onNewChatHere: (com.kamsiob.kamai.data.Mode) -> Unit,
    onOpenConversation: (String) -> Unit,
    onRemoveFromProject: (String) -> Unit,
    /** Chats not in any project, offered by "Add an existing chat" (item 2). */
    unassigned: List<ConversationSummary> = emptyList(),
    onAddExisting: (String) -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val pad = KamTheme.dimens.screenPadding
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var instructions by remember(project?.id, project?.instructions) {
        mutableStateOf(TextFieldValue(project?.instructions.orEmpty()))
    }
    var notes by remember(project?.id, project?.notes) {
        mutableStateOf(TextFieldValue(project?.notes.orEmpty()))
    }
    // One button for both fields, so saving one never silently discards an edit
    // to the other that had not been saved yet.
    val dirty = instructions.text.trim() != project?.instructions.orEmpty() ||
        notes.text.trim() != project?.notes.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = pad),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item("header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project?.name ?: "Project",
                    style = KamTheme.type.screenTitle, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Box {
                    IconAction(
                        icon = Icons.Rounded.MoreHoriz,
                        description = "Project options",
                        onClick = { menuOpen = true },
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = colors.surface,
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Rename", style = KamTheme.type.body, color = colors.textPrimary) },
                            onClick = { menuOpen = false; renaming = true },
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete project",
                                    style = KamTheme.type.body,
                                    color = colors.goldText,
                                )
                            },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Two fields, deliberately apart, because they are two different things
        // and one box invited people to write background under a heading that
        // said instructions.
        item("instructions") {
            ProjectField(
                label = "Instructions",
                explanation = "How Kam AI should behave in this project's chats, and nowhere else.",
                placeholder = "For example: you are helping me write a mystery novel " +
                    "set in 1920s Cairo.",
                value = instructions,
                max = instructionsMax,
                onValueChange = { instructions = it },
            )
            Spacer(Modifier.height(18.dp))
        }

        item("notes") {
            ProjectField(
                label = "Notes",
                explanation = "Background it should already know. Facts, not orders.",
                placeholder = "For example: the detective is Nadia Rashid, and the " +
                    "story ends at the Egyptian Museum.",
                value = notes,
                max = notesMax,
                onValueChange = { notes = it },
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                if (dirty) "Save" else "Saved",
                onClick = { onSave(instructions.text.trim(), notes.text.trim()) },
                enabled = dirty,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }

        // The same control as Chats, rather than a button. Everywhere else in the
        // app, starting a chat and choosing its mode are one act; this screen used
        // to start a General chat with no say in it, which quietly dropped a choice
        // the app otherwise insists on (#39).
        item("new-chat") {
            com.kamsiob.kamai.ui.components.Eyebrow("New chat in this project")
            Spacer(Modifier.height(8.dp))
            com.kamsiob.kamai.ui.components.SegmentedModeControl(
                onSelect = onNewChatHere,
                labelSuffix = " in this project",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }

        item("chats-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Chats in this project", style = KamTheme.type.label, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                // The other half of Remove, which has been sitting on every row here
                // with no matching way in (item 2). Hidden when there is nothing to
                // add, rather than offering an empty picker.
                if (unassigned.isNotEmpty()) {
                    Text(
                        "Add an existing chat",
                        style = KamTheme.type.label,
                        color = colors.accent,
                        modifier = Modifier.clip(CircleShape).clickable { adding = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (conversations.isEmpty()) {
            item("empty") {
                Text(
                    if (unassigned.isEmpty()) {
                        "No chats here yet. Start one above."
                    } else {
                        "No chats here yet. Start one above, or add an existing chat."
                    },
                    style = KamTheme.type.secondary, color = colors.textTertiary,
                )
            }
        } else {
            items(conversations, key = { it.id }) { row ->
                Row(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 8.dp)
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

    if (adding) {
        ExistingChatPicker(
            options = unassigned,
            onPick = { id -> adding = false; onAddExisting(id) },
            onDismiss = { adding = false },
        )
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


/**
 * Picks a chat to pull into this project (item 2).
 *
 * Only chats that are not already in a project are offered. Moving one out of
 * another project is deliberately not offered here, because it would take a
 * conversation out of somewhere the user put it without saying so; that move
 * belongs to the chat's own options, or to bulk move on the Chats list.
 */
@Composable
private fun ExistingChatPicker(
    options: List<ConversationSummary>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)).padding(20.dp),
        ) {
            Text("Add an existing chat", style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "The project's instructions apply from now on, not to messages already sent.",
                style = KamTheme.type.secondary, color = colors.textTertiary,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(options, key = { it.id }) { row ->
                    Text(
                        row.title ?: row.snippet?.take(40) ?: "Untitled chat",
                        style = KamTheme.type.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(row.id) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
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

/**
 * One project, drawn as a folder rather than a list row.
 *
 * A chat row and a project row used to look the same, which said the wrong thing
 * about what each one is: a chat is a single conversation, a project is a
 * container holding several of them plus the instructions they all follow
 * (owner feedback). A folder tile with a count says that at a glance.
 *
 * The tab shape is drawn rather than shipped as an icon so it takes the accent
 * directly and costs nothing in the APK, the same reasoning as the mode
 * sketches.
 */
@Composable
private fun ProjectFolder(
    project: ProjectEntity,
    chatCount: Int,
    onOpen: (String) -> Unit,
) {
    val colors = KamTheme.colors
    val shape = RoundedCornerShape(KamTheme.dimens.cardRadius)
    val hasInstructions = project.instructions.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .clickable { onOpen(project.id) }
            .padding(14.dp)
            .semantics {
                contentDescription = buildString {
                    append(project.name)
                    append(", ")
                    append(if (chatCount == 1) "1 chat" else "$chatCount chats")
                    if (!hasInstructions) append(", no instructions yet")
                }
            },
    ) {
        // The folder itself: a tab sitting on a body, in the accent's tonal fill.
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 36.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(width = 20.dp, height = 8.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(colors.tonalFill),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 46.dp, height = 30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.tonalFill),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$chatCount",
                    style = KamTheme.type.cardTitle,
                    color = colors.tonalText,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            project.name,
            style = KamTheme.type.cardTitle,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                chatCount == 0 && !hasInstructions -> "Empty. Add instructions and start a chat."
                chatCount == 0 -> "No chats yet"
                chatCount == 1 -> "1 chat"
                else -> "$chatCount chats"
            },
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One of the project's two text fields: a label, a line saying what it is for,
 * and a bordered box that shows a worked example while it is empty.
 *
 * Shared so the two cannot drift apart. They are the same control saying
 * different things, and the difference between them is entirely in the words.
 */
@Composable
private fun ProjectField(
    label: String,
    explanation: String,
    placeholder: String,
    value: TextFieldValue,
    max: Int,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val colors = KamTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = KamTheme.type.label, color = colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(explanation, style = KamTheme.type.secondary, color = colors.textTertiary)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth()
                // Room for a few lines before it grows, so an empty field looks
                // like somewhere to write rather than a single-line box.
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            if (value.text.isEmpty()) {
                Text(placeholder, style = KamTheme.type.body, color = colors.textTertiary)
            }
            BasicTextField(
                value = value,
                // Capped rather than truncated on save: typing past the limit
                // simply stops, which is visible, instead of losing the tail
                // silently at the far end of a save.
                onValueChange = { if (it.text.length <= max) onValueChange(it) },
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
