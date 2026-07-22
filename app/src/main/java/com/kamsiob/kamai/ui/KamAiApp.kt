package com.kamsiob.kamai.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamsiob.kamai.BuildConfig
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.ui.chat.ChatScreen
import com.kamsiob.kamai.ui.chat.ChatViewModel
import com.kamsiob.kamai.ui.chats.ChatsScreen
import com.kamsiob.kamai.ui.components.BrandBar
import com.kamsiob.kamai.ui.components.KamBottomNav
import com.kamsiob.kamai.ui.components.KamToast
import com.kamsiob.kamai.ui.components.NavItem
import com.kamsiob.kamai.ui.followups.FollowUpsScreen
import com.kamsiob.kamai.ui.onboarding.OnboardingScreen
import com.kamsiob.kamai.ui.settings.AboutScreen
import com.kamsiob.kamai.ui.settings.LicensesScreen
import com.kamsiob.kamai.ui.settings.MemoryScreen
import com.kamsiob.kamai.ui.settings.ModelScreen
import com.kamsiob.kamai.ui.settings.QuestionsScreen
import com.kamsiob.kamai.ui.settings.RoadmapScreen
import com.kamsiob.kamai.ui.settings.SettingsScreen
import com.kamsiob.kamai.ui.settings.StorageScreen
import com.kamsiob.kamai.ui.theme.KamMotion
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.reducedMotion
import kotlinx.coroutines.delay

/** Screens pushed a level deep, above the tabs. */
private sealed interface Pushed {
    data object Settings : Pushed
    data object Model : Pushed
    data object Storage : Pushed
    data object Memory : Pushed
    data object Questions : Pushed
    data object About : Pushed
    data object Roadmap : Pushed
    data object Licenses : Pushed
    data class Conversation(val id: String) : Pushed
}

@Composable
fun KamAiApp(app: AppViewModel = viewModel()) {
    val colors = KamTheme.colors
    val context = LocalContext.current

    val ready by app.ready.collectAsStateWithLifecycle()
    val onboardingDone by app.onboardingDone.collectAsStateWithLifecycle()
    val toast by app.toast.collectAsStateWithLifecycle()

    // A stack rather than a single value, so back always goes where it came from.
    val stack = remember { mutableStateListOf<Pushed>() }
    var tab by remember { mutableStateOf(NavItem.CHATS) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { app.showToast("No app on this phone can open that link.") }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            app.clearToast()
        }
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }

    if (!onboardingDone) {
        val download by app.download.collectAsStateWithLifecycle()
        OnboardingScreen(
            totalRamGb = app.totalRamGb,
            tiers = app.tiers,
            downloadProgress = (download as? com.kamsiob.kamai.download.Downloader.Progress.Running)
                ?.fraction
                ?: (download as? com.kamsiob.kamai.download.Downloader.Progress.Done)?.let { 1f },
            onDownload = app::downloadModel,
            onFinish = app::finishOnboarding,
            onSupport = { openUrl(Links.SUPPORT) },
        )
        return
    }

    // Back handling, outermost layer. Dialogs, sheets and swipe rows register
    // their own handlers closer in, and the dispatcher resolves innermost
    // first, so those consume the event before any of this runs.
    //
    // A pushed screen pops. With an empty stack on a tab other than Chats, back
    // returns to Chats rather than leaving: Chats is the home root, and a person
    // who wandered into Follow-ups and pressed back almost never means "close
    // the app". Only Chats with an empty stack falls through and exits.
    var backGesture by remember { mutableStateOf(BackGesture()) }

    KamPredictiveBack(
        enabled = stack.isNotEmpty() || tab != NavItem.CHATS,
        onProgress = { backGesture = it },
        onBack = {
            when {
                stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
                tab != NavItem.CHATS -> tab = NavItem.CHATS
            }
        },
    )

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .predictiveBackPeek(backGesture)
                .statusBarsPadding(),
        ) {
            BrandBar(
                onBack = if (stack.isNotEmpty()) {
                    { stack.removeAt(stack.lastIndex) }
                } else {
                    null
                },
                onSettings = if (stack.isEmpty()) {
                    { stack.add(Pushed.Settings) }
                } else {
                    null
                },
            )

            // Screens slide 26dp in the direction of travel with a fade, and
            // going back slides the opposite way.
            val depth = stack.size
            val reduced = reducedMotion()
            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = { screenTransition(depth, reduced) },
                modifier = Modifier.weight(1f),
                label = "screen",
            ) { pushed ->
                when (pushed) {
                    null -> TabContent(app, tab, stack)
                    is Pushed.Conversation -> ConversationScreen(app, pushed.id)
                    Pushed.Settings -> SettingsHost(app, stack, openUrl)
                    Pushed.Model -> ModelHost(app)
                    Pushed.Storage -> StorageHost(app)
                    Pushed.Memory -> MemoryHost(app)
                    Pushed.Questions -> QuestionsScreen()
                    Pushed.About -> AboutHost(app, stack, openUrl)
                    Pushed.Roadmap -> RoadmapScreen()
                    Pushed.Licenses -> LicensesScreen(models = app.tiers)
                }
            }

            if (stack.isEmpty()) {
                val followUpCount by app.followUpCount.collectAsStateWithLifecycle()
                KamBottomNav(
                    current = tab,
                    onSelect = { selected ->
                        if (selected == NavItem.NEW) {
                            stack.add(Pushed.Conversation(NEW_CONVERSATION))
                        } else {
                            tab = selected
                        }
                    },
                    followUpCount = followUpCount,
                    modifier = Modifier.navigationBarsPadding(),
                )
            } else {
                Box(Modifier.navigationBarsPadding())
            }
        }

        KamToast(toast, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private const val NEW_CONVERSATION = ""

@Composable
private fun TabContent(
    app: AppViewModel,
    tab: NavItem,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
) {
    when (tab) {
        NavItem.CHATS -> {
            val conversations by app.conversations.collectAsStateWithLifecycle()
            val view by app.chatsView.collectAsStateWithLifecycle()
            ChatsScreen(
                conversations = conversations,
                view = view,
                onViewChange = app::setChatsView,
                onOpen = { stack.add(Pushed.Conversation(it)) },
                onNewChat = { stack.add(Pushed.Conversation(NEW_CONVERSATION)) },
                onPin = app::setPinned,
                onArchive = app::archive,
                onDelete = app::deleteConversation,
            )
        }

        NavItem.NEW -> Unit

        NavItem.DISCOVER -> com.kamsiob.kamai.ui.components.EmptyState(
            title = "Discover is on the way",
            body = "Packs of short reads, dealt one card at a time. It arrives with " +
                "the Discover phase of the build.",
        )

        NavItem.FOLLOW_UPS -> {
            val open by app.openFollowUps.collectAsStateWithLifecycle()
            val done by app.completedFollowUps.collectAsStateWithLifecycle()
            FollowUpsScreen(
                open = open,
                completed = done,
                onToggle = app::setFollowUpCompleted,
                onRemove = app::deleteFollowUp,
                onOpenSource = { stack.add(Pushed.Conversation(it)) },
            )
        }
    }
}

@Composable
private fun ConversationScreen(app: AppViewModel, conversationId: String) {
    val context = LocalContext.current
    val chat: ChatViewModel = viewModel(
        key = "chat-$conversationId",
        factory = ChatViewModel.factory(app.repository, app.engine),
    )

    LaunchedEffect(conversationId) {
        if (conversationId.isNotEmpty()) chat.open(conversationId)
    }

    val messages by chat.messages.collectAsStateWithLifecycle()
    val mode by chat.mode.collectAsStateWithLifecycle()
    val streaming by chat.streaming.collectAsStateWithLifecycle()
    val notice by chat.notice.collectAsStateWithLifecycle()
    val activeModel by app.activeModel.collectAsStateWithLifecycle()
    val flagged = remember { mutableStateListOf<String>() }

    ChatScreen(
        messages = messages,
        mode = mode,
        streaming = streaming,
        notice = notice,
        modelLabel = activeModel?.displayName,
        flaggedMessageIds = flagged.toSet(),
        // Play is hidden until a voice exists, rather than shown doing nothing.
        ttsAvailable = false,
        onModeChange = chat::setMode,
        onSend = chat::send,
        onStop = chat::stop,
        onFlag = { message ->
            flagged.add(message.id)
            app.flag(message.content, mode, chat.conversationId.value, message.id)
        },
        onRegenerate = chat::regenerate,
        onReport = { message -> reportResponse(context, message.content, app) },
        onPlay = { },
        onEdit = chat::editAndResend,
        onDismissNotice = chat::dismissNotice,
    )
}

/**
 * Report a response: opens a prefilled email draft containing the response.
 * Nothing sends unless the user sends it, which is what Play's AI-generated
 * content reporting requirement asks for.
 */
private fun reportResponse(
    context: android.content.Context,
    responseText: String,
    app: AppViewModel,
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Links.REPORT_EMAIL}")
        putExtra(Intent.EXTRA_SUBJECT, "Kam AI, reporting a response")
        putExtra(
            Intent.EXTRA_TEXT,
            buildString {
                append("I am reporting this response from Kam AI.\n\n")
                append("What was wrong with it:\n\n\n")
                append("The response:\n\n")
                append(responseText)
                append("\n\nApp version: ").append(BuildConfig.VERSION_NAME)
            },
        )
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        app.showToast("No email app is set up on this phone.")
    }
}

@Composable
private fun SettingsHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    openUrl: (String) -> Unit,
) {
    val artifacts by app.artifacts.collectAsStateWithLifecycle()
    val activeModel by app.activeModel.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    SettingsScreen(
        activeModel = activeModel,
        storageBytes = artifacts.sumOf { it.sizeBytes },
        // These rows appear as their phases land, rather than sitting there
        // with coming-soon text, which DESIGN.md rules out.
        voiceInstalled = false,
        backupAvailable = false,
        webSearchAvailable = false,
        onModel = { stack.add(Pushed.Model) },
        onVoice = { },
        onStorage = { stack.add(Pushed.Storage) },
        onWebSearch = { },
        onBackup = { },
        onDeleteEverything = { confirmDelete = true },
        onReplayOnboarding = app::replayOnboarding,
        onQuestions = { stack.add(Pushed.Questions) },
        onAbout = { stack.add(Pushed.About) },
        onMemory = { stack.add(Pushed.Memory) },
        onSupport = {
            openUrl(Links.SUPPORT)
            // The support button also closes the Settings page.
            stack.clear()
        },
    )

    // AlertDialog routes the back event to onDismissRequest itself, so this
    // handler exists only to guarantee the dialog is what consumes it rather
    // than the navigation stack underneath.
    BackHandler(enabled = confirmDelete) { confirmDelete = false }

    if (confirmDelete) {
        DeleteEverythingDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = { includeDownloads ->
                confirmDelete = false
                app.deleteEverything(includeDownloads)
            },
        )
    }
}

@Composable
private fun DeleteEverythingDialog(
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    val colors = KamTheme.colors
    // Re-downloading several gigabytes is a real cost, and not everyone means
    // that by "delete my data", so the models are a separate, explicit choice.
    var includeDownloads by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Delete everything?", style = KamTheme.type.cardTitle, color = colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    "This removes every conversation, everything remembered, every " +
                        "project, and every follow-up. It cannot be undone.",
                    style = KamTheme.type.body,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { includeDownloads = !includeDownloads }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeDownloads,
                        onCheckedChange = { includeDownloads = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.flagAmber,
                            uncheckedColor = colors.textTertiary,
                            checkmarkColor = colors.onAccent,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Also delete downloaded models",
                        style = KamTheme.type.body,
                        color = colors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(includeDownloads) }) {
                Text("Delete everything", style = KamTheme.type.label, color = colors.flagAmber)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep it", style = KamTheme.type.label, color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun ModelHost(app: AppViewModel) {
    val artifacts by app.artifacts.collectAsStateWithLifecycle()
    val activeModel by app.activeModel.collectAsStateWithLifecycle()
    val download by app.download.collectAsStateWithLifecycle()

    ModelScreen(
        totalRamGb = app.totalRamGb,
        tiers = app.tiers,
        installedIds = artifacts.map { it.id }.toSet(),
        activeId = activeModel?.id,
        download = download,
        onDownload = app::downloadModel,
        onActivate = { app.showToast("${it.displayName} is now in use") },
    )
}

@Composable
private fun StorageHost(app: AppViewModel) {
    val artifacts by app.artifacts.collectAsStateWithLifecycle()
    StorageScreen(artifacts = artifacts, onDelete = app::deleteArtifact)
}

@Composable
private fun MemoryHost(app: AppViewModel) {
    val memory by app.memory.collectAsStateWithLifecycle()
    MemoryScreen(entries = memory, onForget = app::forget)
}

@Composable
private fun AboutHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    openUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    AboutScreen(
        versionName = BuildConfig.VERSION_NAME,
        onLink = openUrl,
        onEmail = { address ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")),
                )
            }.onFailure { app.showToast("No email app is set up on this phone.") }
        },
        onLicenses = { stack.add(Pushed.Licenses) },
        onRoadmap = { stack.add(Pushed.Roadmap) },
        onSupport = {
            openUrl(Links.SUPPORT)
            stack.clear()
        },
    )
}

/** Forward slides in the direction of travel; back slides the opposite way. */
private fun AnimatedContentTransitionScope<Pushed?>.screenTransition(
    depth: Int,
    reduced: Boolean,
): ContentTransform {
    if (reduced) {
        return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    }
    val forward = targetState != null && initialState == null ||
        (targetState != null && initialState != null && depth > 0)
    val distance = 26
    return (
        slideInHorizontally(KamMotion.standard()) { if (forward) distance else -distance } +
            fadeIn(tween(KamMotion.MEDIUM_MS))
        ) togetherWith (
        slideOutHorizontally(KamMotion.standard()) { if (forward) -distance else distance } +
            fadeOut(tween(KamMotion.FAST_MS))
        ) using SizeTransform(clip = false)
}
