package com.kamsiob.kamai.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.ui.components.IconAction
import com.kamsiob.kamai.ui.components.EmptyState
import com.kamsiob.kamai.ui.components.edgeFade
import com.kamsiob.kamai.ui.theme.KamMotion
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.expressiveSpec
import com.kamsiob.kamai.ui.theme.reducedMotion
import com.kamsiob.kamai.ui.theme.standardSpec

@Composable
fun ChatScreen(
    messages: List<MessageEntity>,
    mode: Mode,
    streaming: Boolean,
    notice: String?,
    modelLabel: String?,
    flaggedMessageIds: Set<String>,
    ttsAvailable: Boolean,
    speakingMessageId: String? = null,
    conversationTitle: String? = null,
    projectOptions: List<Pair<String, String>> = emptyList(),
    conversationProjectId: String? = null,
    grounded: Boolean = false,
    onContinueOpen: () -> Unit = {},
    onMoveToProject: (String?) -> Unit = {},
    onRenameConversation: (String) -> Unit = {},
    onArchiveConversation: () -> Unit = {},
    onDeleteConversation: () -> Unit = {},
    onModeChange: (Mode) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onFlag: (MessageEntity) -> Unit,
    onRegenerate: () -> Unit,
    onReport: (MessageEntity) -> Unit,
    onShareResponse: (MessageEntity) -> Unit,
    onShareThread: () -> Unit,
    onExportThread: (Boolean) -> Unit,
    onShareText: (String) -> Unit,
    onFollowUpSelection: (MessageEntity, String) -> Unit,
    onPlay: (MessageEntity) -> Unit,
    onEdit: (MessageEntity, String) -> Unit,
    onDismissNotice: () -> Unit,
    initialComposerText: String? = null,
    attachedName: String? = null,
    onAttach: () -> Unit = {},
    onRemoveAttachment: () -> Unit = {},
    voiceAvailable: Boolean = false,
    recording: Boolean = false,
    transcribing: Boolean = false,
    transcribed: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onMicStart: () -> Unit = {},
    onMicStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val listState = rememberLazyListState()

    // Follow the stream as it writes rather than leaving the user scrolling.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // The open conversation's title sits at the top so it is always clear
        // which chat this is, with rename, archive, and delete beside it. Shown
        // once the conversation exists (has content), not on a blank new chat.
        if (messages.isNotEmpty()) {
            ConversationHeader(
                title = conversationTitle,
                projectOptions = projectOptions,
                currentProjectId = conversationProjectId,
                onMoveToProject = onMoveToProject,
                onRename = onRenameConversation,
                onArchive = onArchiveConversation,
                onDelete = onDeleteConversation,
                modifier = Modifier.padding(horizontal = KamTheme.dimens.screenPadding),
            )
        }

        ModeSwitcher(
            current = mode,
            modelLabel = modelLabel,
            onSelect = onModeChange,
            modifier = Modifier.padding(horizontal = KamTheme.dimens.screenPadding),
        )

        // While Logic Partner is active it stays visually unmistakable.
        if (mode == Mode.LOGIC) {
            Box(Modifier.padding(horizontal = KamTheme.dimens.screenPadding, vertical = 4.dp)) {
                LogicBanner()
            }
        }

        // A grounded Discover discussion states its scope up front, and offers the
        // one-tap way out so an out-of-scope question never dead-ends (item 21).
        if (grounded) {
            Box(Modifier.padding(horizontal = KamTheme.dimens.screenPadding, vertical = 4.dp)) {
                GroundedBanner(onContinueOpen = onContinueOpen)
            }
        }

        // A hairline separates the header zone (title and mode) from the
        // conversation, so the title clearly belongs to the bar above, not the
        // messages below.
        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KamTheme.dimens.screenPadding)
                    .height(1.dp)
                    .background(colors.border),
            )
        }

        Box(Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(
                    title = whenEmptyTitle(mode),
                    body = whenEmptyBody(mode),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .edgeFade(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = KamTheme.dimens.screenPadding,
                        vertical = 10.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        if (message.role == Role.SYSTEM) {
                            ModeSwitchNote(message.content)
                            return@items
                        }
                        MessageRow(
                            message = message,
                            flagged = message.id in flaggedMessageIds,
                            ttsAvailable = ttsAvailable,
                            speaking = message.id == speakingMessageId,
                            isLast = message.id == messages.lastOrNull()?.id,
                            onFlag = { onFlag(message) },
                            onRegenerate = onRegenerate,
                            onReport = { onReport(message) },
                            onShareResponse = { onShareResponse(message) },
                            onShareThread = onShareThread,
                            onExportThread = onExportThread,
                            onShareText = onShareText,
                            onFollowUpSelection = { text -> onFollowUpSelection(message, text) },
                            onPlay = { onPlay(message) },
                            onEdit = { onEdit(message, it) },
                        )
                    }
                    // Show the thinking indicator the instant work starts, through
                    // model load and prompt ingestion, not only once the empty answer
                    // bubble exists.
                    val lastMsg = messages.lastOrNull()
                    if (showThinkingIndicator(streaming, lastMsg?.role, lastMsg?.content)) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        if (notice != null) {
            NoticeBar(notice, onDismissNotice)
        }

        Composer(
            enabled = true,
            streaming = streaming,
            onSend = onSend,
            onStop = onStop,
            initialText = initialComposerText,
            attachedName = attachedName,
            onAttach = onAttach,
            onRemoveAttachment = onRemoveAttachment,
            voiceAvailable = voiceAvailable,
            recording = recording,
            transcribing = transcribing,
            transcribed = transcribed,
            onMicStart = onMicStart,
            onMicStop = onMicStop,
        )
    }
}

private fun whenEmptyTitle(mode: Mode) = when (mode) {
    Mode.LOGIC -> "Bring an idea to test"
    Mode.BENCH -> "Paste something in"
    else -> "Ask it something"
}

private fun whenEmptyBody(mode: Mode) = when (mode) {
    Mode.LOGIC -> "Say what you think and why. It will restate your position, then go " +
        "looking for the weak parts."
    Mode.BENCH -> "Drop in text you want tightened, rewritten, or reorganized."
    else -> "Anything on your mind. It runs on this phone, so nothing you type leaves it."
}

/**
 * The open conversation's title and its actions. The title is always visible so
 * the user knows which chat they are in; the overflow holds Rename, Archive, and
 * Delete, matching the chat list's behaviour and confirmation tiers. A manual
 * rename here stops auto-titling, the same rule as the list.
 */
@Composable
private fun ConversationHeader(
    title: String?,
    projectOptions: List<Pair<String, String>>,
    currentProjectId: String?,
    onMoveToProject: (String?) -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A short accent bar marks this as the conversation's title, so it reads
        // as a heading rather than as the first line of the chat.
        Box(
            Modifier
                .height(18.dp)
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title ?: "New chat",
                style = KamTheme.type.cardTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Which project this chat belongs to, so its context is never ambiguous.
            val projectName = projectOptions.firstOrNull { it.first == currentProjectId }?.second
            if (projectName != null) {
                Text(
                    "In $projectName",
                    style = KamTheme.type.secondary,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box {
            IconAction(
                icon = Icons.Rounded.MoreHoriz,
                description = "Chat options",
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
                            if (currentProjectId != null) "Move to another project" else "Move to project",
                            style = KamTheme.type.body, color = colors.textPrimary,
                        )
                    },
                    onClick = { menuOpen = false; picking = true },
                )
                if (currentProjectId != null) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Remove from project", style = KamTheme.type.body, color = colors.textPrimary) },
                        onClick = { menuOpen = false; onMoveToProject(null) },
                    )
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Archive", style = KamTheme.type.body, color = colors.textPrimary) },
                    onClick = { menuOpen = false; onArchive() },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Delete", style = KamTheme.type.body, color = colors.flagAmber) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }

    if (renaming) {
        ConversationRenameDialog(
            initial = title.orEmpty(),
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    if (picking) {
        ProjectPickerDialog(
            options = projectOptions,
            currentProjectId = currentProjectId,
            onPick = { picking = false; onMoveToProject(it) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Picks which project a conversation belongs to. Moving applies from here on, not
 * retroactively, which the dialog states plainly.
 */
@Composable
private fun ProjectPickerDialog(
    options: List<Pair<String, String>>,
    currentProjectId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp)).padding(20.dp),
        ) {
            Text("Move to project", style = KamTheme.type.cardTitle, color = colors.textPrimary)
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

/** A plain rename dialog for the open conversation, matching the list's style. */
@Composable
private fun ConversationRenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    var text by remember {
        mutableStateOf(
            androidx.compose.ui.text.input.TextFieldValue(
                initial, androidx.compose.ui.text.TextRange(initial.length),
            ),
        )
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(22.dp),
        ) {
            Text("Rename chat", style = KamTheme.type.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceSecondary)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancel", style = KamTheme.type.label, color = colors.textSecondary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
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
 * The in-chat mode control: one little bubble that flips between Chat and Logic
 * Partner, not a three-way segmented control. Workbench is its own surface and
 * is deliberately never part of this. PART 4. Switching mid-conversation is
 * allowed and the existing context carries forward.
 */
@Composable
private fun ModeSwitcher(
    current: Mode,
    modelLabel: String?,
    onSelect: (Mode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    // Only Chat and Logic live here; anything else shows as Chat for the flip.
    val isLogic = current == Mode.LOGIC
    val next = if (isLogic) Mode.CHAT else Mode.LOGIC
    val label = if (isLogic) "Logic Partner" else "Chat"
    val bg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isLogic) colors.tonalFill else colors.surfaceSecondary,
        animationSpec = expressiveSpec(),
        label = "mode-bubble",
    )

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .clickable { onSelect(next) }
                .padding(horizontal = 15.dp, vertical = 9.dp)
                .semantics {
                    contentDescription = "Mode: $label. Tap to switch to " +
                        (if (isLogic) "Chat." else "Logic Partner.")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = if (isLogic) colors.tonalText else colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = KamTheme.type.label,
                color = if (isLogic) colors.tonalText else colors.textPrimary,
            )
        }

        Spacer(Modifier.weight(1f))

        if (modelLabel != null) {
            Text(modelLabel, style = KamTheme.type.mono, color = colors.textTertiary)
        }
    }
}

/** Bubbles animate in from below with slight scale. */
@Composable
private fun MessageRow(
    message: MessageEntity,
    flagged: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    isLast: Boolean,
    onFlag: () -> Unit,
    onRegenerate: () -> Unit,
    onReport: () -> Unit,
    onShareResponse: () -> Unit,
    onShareThread: () -> Unit,
    onExportThread: (Boolean) -> Unit,
    onShareText: (String) -> Unit,
    onFollowUpSelection: (String) -> Unit,
    onPlay: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // An answer that has not produced any text yet is represented by the thinking
    // indicator, so its empty bubble is not drawn (it would show as a bare pill).
    if (message.role == Role.ASSISTANT && message.content.isEmpty() && message.incomplete) return

    val colors = KamTheme.colors
    val reduced = reducedMotion()
    var appeared by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { appeared = true }

    var editing by remember { mutableStateOf(false) }

    // Roughly three-quarters of the screen. Wide enough that a long answer reads
    // like prose rather than a thin column, while the messaging shape and the
    // left/right asymmetry from DESIGN.md stay intact.
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.80f).dp

    AnimatedVisibility(
        visible = appeared,
        enter = if (reduced) fadeIn(tween(0)) else {
            fadeIn(tween(KamMotion.FAST_MS)) +
                slideInVertically(standardSpec<IntOffset>()) { it / 3 } +
                scaleIn(standardSpec(), initialScale = 0.96f)
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (message.role == Role.USER) Alignment.End else Alignment.Start,
        ) {
            if (message.role == Role.USER && editing) {
                EditBubble(
                    initial = message.content,
                    maxBubble = maxBubble,
                    onCancel = { editing = false },
                    onConfirm = {
                        editing = false
                        onEdit(it)
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxBubble)
                        .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
                        .background(if (message.role == Role.USER) colors.tonalFill else colors.surface)
                        .then(
                            if (message.role == Role.ASSISTANT) {
                                Modifier.border(
                                    1.dp, colors.border,
                                    RoundedCornerShape(KamTheme.dimens.cardRadius),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = message.role == Role.USER) { editing = true }
                        .padding(14.dp),
                ) {
                    if (message.role == Role.ASSISTANT) {
                        // Selecting any part of a response offers copy, follow up,
                        // and share for exactly that excerpt. PART 5 and 5B.
                        com.kamsiob.kamai.ui.components.SelectionActions(
                            onCopy = { }, // the platform copy already ran
                            onFollowUp = { if (it.isNotBlank()) onFollowUpSelection(it) },
                            onShare = { if (it.isNotBlank()) onShareText(it) },
                        ) {
                            // Assistant text is Markdown, rendered in the app's own
                            // type scale and colours (item 14).
                            com.kamsiob.kamai.ui.components.MarkdownText(
                                text = message.content,
                                color = colors.textPrimary,
                            )
                        }
                    } else {
                        Text(
                            message.content,
                            style = KamTheme.type.body,
                            color = colors.tonalText,
                        )
                    }
                }
            }

            // A stop reason is stated plainly under the message it belongs to.
            message.stoppedReason?.let { reason ->
                Spacer(Modifier.height(5.dp))
                Text(
                    reason,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                    modifier = Modifier.widthIn(max = maxBubble),
                )
            }

            if (message.role == Role.ASSISTANT && !message.incomplete) {
                Spacer(Modifier.height(5.dp))
                ActionRow(
                    flagged = flagged,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    canRegenerate = isLast,
                    text = message.content,
                    onFlag = onFlag,
                    onRegenerate = onRegenerate,
                    onReport = onReport,
                    onShareResponse = onShareResponse,
                    onShareThread = onShareThread,
                    onExportThread = onExportThread,
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun EditBubble(
    initial: String,
    maxBubble: androidx.compose.ui.unit.Dp,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = KamTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(initial, androidx.compose.ui.text.TextRange(initial.length))) }

    Column(
        modifier = Modifier
            .widthIn(max = maxBubble)
            .clip(RoundedCornerShape(KamTheme.dimens.cardRadius))
            .background(colors.tonalFill)
            .border(2.dp, colors.accent, RoundedCornerShape(KamTheme.dimens.cardRadius))
            .padding(14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = KamTheme.type.body.copy(color = colors.tonalText),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Editing removes everything after this and answers again.",
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cancel",
                style = KamTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.clickable(onClick = onCancel).padding(10.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Send again",
                style = KamTheme.type.label,
                color = colors.accent,
                modifier = Modifier
                    .clickable { onConfirm(value.text) }
                    .padding(10.dp),
            )
        }
    }
}

/** Flag, copy, play, regenerate, and the overflow that holds Report. */
@Composable
private fun ActionRow(
    flagged: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    canRegenerate: Boolean,
    text: String,
    onFlag: () -> Unit,
    onRegenerate: () -> Unit,
    onReport: () -> Unit,
    onShareResponse: () -> Unit,
    onShareThread: () -> Unit,
    onExportThread: (Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    val colors = KamTheme.colors
    val clipboard = LocalClipboardManager.current
    var overflowOpen by remember { mutableStateOf(false) }

    // The flag pops with overshoot and a small rotation, then turns amber.
    val flagScale by animateFloatAsState(
        targetValue = if (flagged) 1f else 1f,
        animationSpec = expressiveSpec(),
        label = "flag-scale",
    )
    val flagRotation by animateFloatAsState(
        targetValue = if (flagged) -12f else 0f,
        animationSpec = expressiveSpec(),
        label = "flag-rotation",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.graphicsLayer {
                scaleX = flagScale
                scaleY = flagScale
                rotationZ = flagRotation
            },
        ) {
            IconAction(
                // A bookmark, deliberately not a flag: the flag was too easily
                // mistaken for Report sitting a few icons over. The meaning is
                // unchanged, mark this for a closer look later, and it keeps the
                // reserved amber. Report has its own icon in the overflow.
                icon = if (flagged) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                description = if (flagged) "Bookmarked for follow-up" else "Bookmark for follow-up",
                onClick = onFlag,
                tint = if (flagged) colors.flagAmber else colors.textTertiary,
            )
        }
        IconAction(
            icon = Icons.Rounded.ContentCopy,
            description = "Copy",
            onClick = { clipboard.setText(AnnotatedString(text)) },
            tint = colors.textTertiary,
        )
        IconAction(
            icon = Icons.Rounded.Share,
            description = "Share this response",
            onClick = onShareResponse,
            tint = colors.textTertiary,
        )
        // Play is hidden entirely when no voice is installed, rather than shown
        // as a button that does nothing. While this response is being read, it
        // becomes a Stop control so playback can be interrupted at any point.
        if (ttsAvailable) {
            IconAction(
                icon = if (speaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                description = if (speaking) "Stop reading" else "Read aloud",
                onClick = onPlay,
                tint = if (speaking) colors.accent else colors.textTertiary,
            )
        }
        if (canRegenerate) {
            IconAction(
                icon = Icons.Rounded.Refresh,
                description = "Answer again",
                onClick = onRegenerate,
                tint = colors.textTertiary,
            )
        }
        Box {
            IconAction(
                icon = Icons.Rounded.MoreHoriz,
                description = "More actions",
                onClick = { overflowOpen = true },
                tint = colors.textTertiary,
            )
            androidx.compose.material3.DropdownMenu(
                expanded = overflowOpen,
                onDismissRequest = { overflowOpen = false },
                containerColor = colors.surface,
            ) {
                listOf(
                    "Share whole thread" to onShareThread,
                    "Export thread as text" to { onExportThread(false) },
                    "Export thread as Markdown" to { onExportThread(true) },
                ).forEach { (label, action) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label, style = KamTheme.type.body, color = colors.textPrimary) },
                        onClick = { overflowOpen = false; action() },
                    )
                }
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            "Report this response",
                            style = KamTheme.type.body,
                            color = colors.textPrimary,
                        )
                    },
                    onClick = {
                        overflowOpen = false
                        onReport()
                    },
                )
            }
        }
    }
}

/**
 * The quiet centered note that records a mode switch in the transcript. Not a
 * bubble from either side: it is a plain system line, so the history shows exactly
 * where behaviour changed. Uses the design system, never the reserved amber.
 */
@Composable
private fun ModeSwitchNote(text: String) {
    val colors = KamTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            style = KamTheme.type.secondary,
            color = colors.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSecondary)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/**
 * A calm, persistent marker shown while Logic Partner is active, so it is obvious
 * at a glance which mode is answering without shouting. Tonal fill and text from
 * the design system, no amber.
 */
@Composable
private fun LogicBanner() {
    val colors = KamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tonalFill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.SwapHoriz,
            contentDescription = null,
            tint = colors.tonalText,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Logic Partner is testing your reasoning, not agreeing with it.",
            style = KamTheme.type.secondary,
            color = colors.tonalText,
        )
    }
}

/**
 * States a Discover discussion's scope up front and gives the one-tap way out, so
 * an out-of-scope question is never a dead end (item 21). Tonal fill and text from
 * the design system, no amber; the escape reads as a plain accent action.
 */
@Composable
private fun GroundedBanner(onContinueOpen: () -> Unit) {
    val colors = KamTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.tonalFill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.MenuBook,
                contentDescription = null,
                tint = colors.tonalText,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Staying with this passage. Answers come from the text above, not the wider web.",
                style = KamTheme.type.secondary,
                color = colors.tonalText,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Continue in open chat",
            style = KamTheme.type.label,
            color = colors.accent,
            modifier = Modifier
                .clickable(onClick = onContinueOpen)
                .padding(start = 23.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}

/**
 * Whether to show the thinking indicator: work is under way and the answer has
 * produced no text yet. That covers the whole window a user waits through, model
 * load and prompt ingestion included, when the last turn is still theirs (or an
 * empty placeholder, or a brand-new empty chat), not only once tokens arrive.
 */
internal fun showThinkingIndicator(streaming: Boolean, lastRole: Role?, lastContent: String?): Boolean =
    streaming && (lastRole == null || lastRole == Role.USER || lastContent.isNullOrEmpty())

/** Three dots, preceding a response. */
@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val colors = KamTheme.colors
    val reduced = reducedMotion()

    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(3) { index ->
            val alpha = if (reduced) {
                0.55f
            } else {
                val transition = rememberInfiniteTransition(label = "typing")
                transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 0.9f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(620, delayMillis = index * 160),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-$index",
                ).value
            }
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.textTertiary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    val colors = KamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KamTheme.dimens.screenPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.amberFill)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = KamTheme.type.secondary, color = colors.flagAmber, modifier = Modifier.weight(1f))
    }
}

/** Pill field, microphone, and a round accent send that becomes stop. */
@Composable
private fun Composer(
    enabled: Boolean,
    streaming: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    initialText: String? = null,
    attachedName: String? = null,
    onAttach: () -> Unit = {},
    onRemoveAttachment: () -> Unit = {},
    voiceAvailable: Boolean = false,
    recording: Boolean = false,
    transcribing: Boolean = false,
    transcribed: kotlinx.coroutines.flow.SharedFlow<String>? = null,
    onMicStart: () -> Unit = {},
    onMicStop: () -> Unit = {},
) {
    val colors = KamTheme.colors
    var value by remember { mutableStateOf(initialText.orEmpty()) }

    // Transcribed text lands in the field, appended to whatever is already there.
    LaunchedEffect(transcribed) {
        transcribed?.collect { text ->
            value = if (value.isBlank()) text else "${value.trimEnd()} $text"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = KamTheme.dimens.screenPadding, vertical = 8.dp),
    ) {
      // The attached document, shown as a removable chip above the field.
      if (attachedName != null) {
          Row(
              modifier = Modifier
                  .clip(RoundedCornerShape(14.dp))
                  .background(colors.tonalFill)
                  .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Icon(Icons.Rounded.Description, contentDescription = null, tint = colors.tonalText, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text(attachedName, style = KamTheme.type.secondary, color = colors.tonalText)
              Spacer(Modifier.width(4.dp))
              Box(
                  modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .clickable(onClick = onRemoveAttachment)
                      .semantics { contentDescription = "Remove attachment" },
                  contentAlignment = Alignment.Center,
              ) {
                  Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.tonalText, modifier = Modifier.size(15.dp))
              }
          }
          Spacer(Modifier.height(6.dp))
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
      ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    when {
                        recording -> "Listening. Tap stop when you are done."
                        transcribing -> "Turning your voice into text..."
                        else -> "Ask, paste, or talk it out"
                    },
                    style = KamTheme.type.body,
                    color = colors.textTertiary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                enabled = enabled && !recording && !transcribing,
                textStyle = KamTheme.type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Attach a document for the model to read. Hidden while streaming or
        // recording, so the row stays uncluttered when it matters.
        if (!streaming && !recording && !transcribing) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable(onClick = onAttach)
                    .semantics { contentDescription = "Attach a file" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // The microphone shows only when voice typing is available and the field
        // is empty, so it never competes with sending a typed message.
        if (voiceAvailable && !streaming && (value.isBlank() || recording || transcribing)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (recording) colors.flagAmber else colors.surface)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable(enabled = !transcribing) {
                        if (recording) onMicStop() else onMicStart()
                    }
                    .semantics {
                        contentDescription = if (recording) "Stop recording" else "Start voice typing"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (transcribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                } else {
                    Icon(
                        if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = if (recording) colors.onAccent else colors.textSecondary,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable {
                    if (streaming) {
                        onStop()
                    } else if (value.isNotBlank()) {
                        onSend(value)
                        value = ""
                    }
                }
                .semantics {
                    contentDescription = if (streaming) "Stop generating" else "Send"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (streaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(21.dp),
            )
        }
      }
    }
}
