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
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import kotlinx.coroutines.launch
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

/**
 * A scroll offset larger than any message can be, in pixels. Asking to scroll an
 * item to this offset lands past its end, which the list clamps to the bottom of
 * the content. That is how "follow the newest text" is expressed: scrolling to
 * the last item's *start* would sit at the top of a long answer while it grew
 * below the fold.
 */
private const val FOLLOW_TO_END_OFFSET = 1_000_000

/**
 * Scrolls to the true bottom of the list.
 *
 * Deliberately the last *item*, not the last *message*. The thinking indicator is
 * an extra item below the messages, so while it is showing there is one more row
 * than there are messages. Scrolling to `messages.lastIndex` therefore stops one
 * row short, never reaches the bottom, and leaves `atBottom` false, which in turn
 * means a response never starts following once it does begin to arrive. That was
 * a live defect found on the phone, not in the tests: the old index-only
 * `atBottom` happened to tolerate it and the stricter one does not.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.followToEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last >= 0) animateScrollToItem(last, FOLLOW_TO_END_OFFSET)
}

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
    onOpenModel: () -> Unit = {},
    onOpenWorkbench: () -> Unit = {},
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onFlag: (MessageEntity) -> Unit,
    onRegenerate: () -> Unit,
    /** The three ways out of an answer that stopped early (#35). Retry reuses
     *  [onRegenerate], since replacing the answer is exactly what it does. */
    onContinueIncomplete: () -> Unit = {},
    onDiscardIncomplete: () -> Unit = {},
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
    /** Where to open the list, and where to report it back to (#35). */
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    /** False for a conversation never opened before, which starts at the newest
     *  message rather than at a position nobody chose. */
    hasSavedScroll: Boolean = false,
    onScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    /** An unsent draft to restore, and where to report it back to (#35). */
    onDraftChanged: (String) -> Unit = {},
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
    val screenScope = androidx.compose.runtime.rememberCoroutineScope()

    // The top banner is a switch-triggered reminder: it appears when the user
    // changes mode in this session, and does not replay when an existing
    // conversation is merely reopened. The persistent indicator (bottom bar) is
    // always there regardless. See DESIGN.md, Four-Mode Update Part 2.
    var switchedTo by remember { mutableStateOf<Mode?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val onModeSwitch: (Mode) -> Unit = { m ->
        if (m != mode) switchedTo = m
        onModeChange(m)
    }

    // Whether the newest message's end is on screen. Drives the jump-to-latest
    // control and decides whether streaming text follows down. Asking only
    // whether the last item is visible is not enough while that item is growing:
    // its index stays the last index however far below the fold the new text has
    // gone. See ScrollFollow, and issue #43.
    val atBottom by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            ScrollFollow.isAtBottom(
                lastVisibleIndex = last?.index,
                lastVisibleItemEnd = if (last == null) 0 else last.offset + last.size,
                totalItems = info.totalItemsCount,
                viewportEnd = info.viewportEndOffset,
            )
        }
    }

    // Per-conversation scroll restoration (#35). Applied once, on opening, and
    // only when there is something to restore to: a fresh conversation and one
    // last read at the bottom both want the default. Reported back on every
    // settled scroll so the caller holds the position rather than this screen,
    // which does not survive being navigated away from.
    // This decides where the conversation opens, and it has to be the only thing
    // that does. The streaming-follow effect below used to win the race on open
    // and slide everything to the newest message, so a restored position was
    // visibly overwritten a frame later. Opening at the latest message is now one
    // branch of this rather than a competing effect.
    var scrollRestored by remember { mutableStateOf(false) }
    LaunchedEffect(messages.isNotEmpty()) {
        if (scrollRestored || messages.isEmpty()) return@LaunchedEffect
        if (hasSavedScroll) {
            runCatching { listState.scrollToItem(initialScrollIndex, initialScrollOffset) }
        } else {
            // Never opened before, so the newest message is the right place.
            listState.followToEnd()
        }
        scrollRestored = true
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && scrollRestored) {
            onScrollChanged(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    // Once the user takes over during a response, following stops for the rest of
    // that response (#43). A drag is the signal, rather than "is the list
    // scrolling", because the latter is also true while the code scrolls the list
    // itself, which would latch against our own following.
    val followLatch = remember { FollowLatch() }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) followLatch.userDragged()
        }
    }

    // Coming back to the bottom by hand is how following resumes.
    LaunchedEffect(atBottom) {
        if (atBottom) followLatch.returnedToBottom()
    }

    // A new message is a new response, which follows from the start again. This
    // is declared before the effect below so it has already run when that one
    // fires on the same change.
    LaunchedEffect(messages.size) {
        followLatch.newResponseStarted()
    }

    // Follow the stream as it writes, unless the user is reading elsewhere. The
    // large offset asks for a position past the end of the last item, which the
    // list clamps to the very bottom of the content: following has to mean the
    // newest text, and scrolling to the item's start would sit at the top of a
    // long answer while it grew below the fold.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length, streaming) {
        // Gated on the opening position having been decided, so following cannot
        // run before it and undo it.
        // Only while something is actually streaming. Following exists to keep up
        // with text as it is written, and on open there is nothing to keep up
        // with: this effect used to fire once as the messages loaded, see a list
        // that had not been measured yet (so atBottom was trivially true), and
        // slide straight to the newest message, overwriting the position the
        // restore above had just set. Sending sets streaming before it writes the
        // message, so a new turn is still followed.
        if (scrollRestored && streaming && messages.isNotEmpty() &&
            followLatch.shouldFollow(atBottom)
        ) {
            listState.followToEnd()
        }
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

        // A one-line, mode-coloured banner appears at the top when the user just
        // switched mode this session, as a reminder of what the mode does. It is
        // not shown when simply opening an existing conversation.
        if (switchedTo == mode && !grounded) {
            Box(Modifier.padding(horizontal = KamTheme.dimens.screenPadding, vertical = 4.dp)) {
                ModeBanner(mode)
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
                // Per mode, not generic (#29). A grounded Discover discussion is
                // excluded: it is not one of the four modes and it already carries
                // its own scope banner, so a second large decorative panel would
                // be two explanations of the same screen.
                if (grounded) {
                    EmptyState(
                        title = whenEmptyTitle(mode),
                        body = whenEmptyBody(mode),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    com.kamsiob.kamai.ui.components.ModeNudge(
                        mode = mode,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
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
                            onContinue = onContinueIncomplete,
                            onDiscard = onDiscardIncomplete,
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

                // Jump to latest, shown only when scrolled up away from the newest
                // message, positioned so it covers neither the messages nor the
                // input. Arrives and leaves on the standard spring.
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && messages.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    enter = androidx.compose.animation.fadeIn(standardSpec()) +
                        androidx.compose.animation.scaleIn(standardSpec(), initialScale = 0.8f),
                    exit = androidx.compose.animation.fadeOut(standardSpec()) +
                        androidx.compose.animation.scaleOut(standardSpec(), targetScale = 0.8f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .border(1.dp, colors.border, CircleShape)
                            .clickable {
                                // Tapping this is the user asking to follow again,
                                // so it releases the latch as well as scrolling.
                                followLatch.jumpTapped()
                                scope.launch { listState.followToEnd() }
                            }
                            .semantics { contentDescription = "Jump to the latest message" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        if (notice != null) {
            NoticeBar(notice, onDismissNotice)
        }

        // The persistent mode indicator sits at the bottom, adjacent to the input,
        // so it is reachable one-handed. It always shows the current mode and the
        // active model, and opens the deliberate mode picker when tapped. Hidden in
        // a grounded Discover discussion, which is not one of the four modes.
        if (!grounded) {
            ModeBar(
                mode = mode,
                modelLabel = modelLabel,
                onOpenPicker = { showPicker = true },
                onOpenModel = onOpenModel,
                modifier = Modifier.padding(horizontal = KamTheme.dimens.screenPadding),
            )
        }

        Composer(
            enabled = true,
            streaming = streaming,
            onSend = { text ->
                // Sending is a deliberate act, so it always goes to the bottom,
                // exactly like tapping jump-to-latest. The no-yank rule (#43) is
                // about text arriving while you are reading something else, not
                // about your own message: without this, sending while scrolled up
                // left the user staring at old messages with no sign anything had
                // happened. Reachable now that a conversation reliably reopens
                // where it was left (#35).
                followLatch.jumpTapped()
                screenScope.launch { listState.followToEnd() }
                onSend(text)
            },
            onStop = onStop,
            initialText = initialComposerText,
            onTextChanged = onDraftChanged,
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

    if (showPicker) {
        ModePicker(
            current = mode,
            onSelect = { m ->
                showPicker = false
                if (m == Mode.BENCH) onOpenWorkbench() else onModeSwitch(m)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * The persistent mode indicator at the bottom of a conversation, adjacent to the
 * input so it is within thumb reach. Shows the current mode (dot plus name in its
 * colour) and the active model, and opens the mode picker when tapped. Tapping the
 * model name opens model settings (Part 11B).
 */
@Composable
private fun ModeBar(
    mode: Mode,
    modelLabel: String?,
    onOpenPicker: () -> Unit,
    onOpenModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KamTheme.colors
    val modeColor = com.kamsiob.kamai.ui.theme.ModeColors.of(mode, colors.isDark)
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceSecondary)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .semantics {
                    contentDescription =
                        "Mode: ${com.kamsiob.kamai.ui.theme.ModeColors.name(mode)}. Tap to change mode."
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.kamsiob.kamai.ui.components.ModeDot(mode, size = 7.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                com.kamsiob.kamai.ui.theme.ModeColors.name(mode),
                style = KamTheme.type.label,
                color = modeColor,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (modelLabel != null) {
            Text(
                modelLabel,
                style = KamTheme.type.mono,
                color = colors.textTertiary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenModel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Model: $modelLabel. Tap to change model." },
            )
        }
    }
}

/** The one-line, mode-coloured switch banner. Tonal fill in the mode's colour,
 *  the mode glyph, and the mode's one-sentence description. */
@Composable
private fun ModeBanner(mode: Mode) {
    val colors = KamTheme.colors
    val modeColor = com.kamsiob.kamai.ui.theme.ModeColors.of(mode, colors.isDark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(modeColor.copy(alpha = if (colors.isDark) 0.18f else 0.12f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            com.kamsiob.kamai.ui.components.modeIcon(mode),
            contentDescription = null,
            tint = modeColor,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            com.kamsiob.kamai.llm.SystemPrompts.topBanner(mode),
            style = KamTheme.type.secondary,
            color = modeColor,
        )
    }
}

/**
 * The deliberate mode picker: a small sheet listing the four modes, each with its
 * colour dot, name, and a short line describing what it does, the current one
 * marked. Choosing applies and dismisses; dismissing changes nothing. Tapping the
 * indicator opens this rather than switching immediately, since a switch changes
 * how the assistant behaves for the rest of the conversation.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModePicker(
    current: Mode,
    onSelect: (Mode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = KamTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Choose a mode", style = KamTheme.type.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Switching changes how Kam AI answers for the rest of this chat, and keeps what you have said so far.",
                style = KamTheme.type.secondary, color = colors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            com.kamsiob.kamai.ui.theme.ModeColors.fourModes.forEach { mode ->
                val isCurrent = mode == current
                val modeColor = com.kamsiob.kamai.ui.theme.ModeColors.of(mode, colors.isDark)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isCurrent) { onSelect(mode) }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.kamsiob.kamai.ui.components.ModeDot(mode, size = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            com.kamsiob.kamai.ui.theme.ModeColors.name(mode),
                            style = KamTheme.type.body, color = modeColor,
                        )
                        Text(modePickerBlurb(mode), style = KamTheme.type.secondary, color = colors.textSecondary)
                    }
                    if (isCurrent) {
                        Text("Current", style = KamTheme.type.mono, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}

/** One plain line per mode for the picker. Workbench states that it opens a linked
 *  session rather than converting this conversation (Part 4). */
private fun modePickerBlurb(mode: Mode) = when (mode) {
    Mode.LOGIC -> "Argues the other side and tests your reasoning."
    Mode.BRAINSTORM -> "Pulls ideas out of you instead of handing them over."
    Mode.BENCH -> "Opens a linked Workbench to rework text, side by side."
    else -> "Answers plainly and helps with whatever you are working on."
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
                    text = { Text("Delete", style = KamTheme.type.body, color = colors.goldText) },
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

/** Bubbles animate in from below with slight scale. */
@Composable
private fun MessageRow(
    onContinue: () -> Unit = {},
    onDiscard: () -> Unit = {},
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

            // A stop reason is stated plainly under the message it belongs to,
            // and, on the last answer, offers the three ways out (#35). Saying an
            // answer stopped without offering anything to do about it is half a
            // sentence: the point of the honest state is that it is actionable.
            message.stoppedReason?.let { reason ->
                Spacer(Modifier.height(5.dp))
                Text(
                    reason,
                    style = KamTheme.type.secondary,
                    color = colors.textTertiary,
                    modifier = Modifier.widthIn(max = maxBubble),
                )
                if (isLast && message.role == Role.ASSISTANT) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Continue first, because picking up where it stopped is
                        // almost always what somebody wants after stopping it
                        // themselves, and it keeps what was already written.
                        IncompleteAction("Continue", onContinue)
                        IncompleteAction("Retry", onRegenerate)
                        IncompleteAction("Discard", onDiscard)
                    }
                }
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
                tint = if (flagged) colors.goldText else colors.textTertiary,
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
        Text(text, style = KamTheme.type.secondary, color = colors.goldText, modifier = Modifier.weight(1f))
    }
}

/** Pill field, microphone, and a round accent send that becomes stop. */
/** A quiet bordered pill, for the three ways out of a stopped answer (#35). */
@Composable
private fun IncompleteAction(label: String, onClick: () -> Unit) {
    val colors = KamTheme.colors
    Text(
        label,
        style = KamTheme.type.secondary,
        color = colors.textSecondary,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceSecondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Composer(
    enabled: Boolean,
    streaming: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    initialText: String? = null,
    onTextChanged: (String) -> Unit = {},
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

    // Reported on every change so the caller can hold the draft. This screen does
    // not survive being navigated away from, so it cannot hold it itself (#35).
    LaunchedEffect(value) { onTextChanged(value) }

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
