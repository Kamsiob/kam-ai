package com.kamsiob.kamai.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material3.Text
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
import com.kamsiob.kamai.ui.components.ConfirmDialog
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
import com.kamsiob.kamai.lock.AppLock
import com.kamsiob.kamai.lock.Biometric
import com.kamsiob.kamai.lock.LockSettingsScreen
import com.kamsiob.kamai.data.DatabaseKey
import com.kamsiob.kamai.data.KamDatabase
import com.kamsiob.kamai.ui.settings.AppearanceScreen
import com.kamsiob.kamai.ui.settings.RoadmapScreen
import com.kamsiob.kamai.ui.settings.SafetyScreen
import com.kamsiob.kamai.ui.settings.SettingsScreen
import com.kamsiob.kamai.ui.settings.StorageScreen
import com.kamsiob.kamai.ui.theme.KamMotion
import com.kamsiob.kamai.ui.theme.KamTheme
import com.kamsiob.kamai.ui.theme.Appearance
import com.kamsiob.kamai.ui.theme.ThemeMode
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
    data object CrashReport : Pushed
    data object Voice : Pushed
    data object Workbench : Pushed
    data object Appearance : Pushed
    data object Safety : Pushed
    data object AppLock : Pushed
    data class Conversation(
        val id: String,
        val startMode: Mode? = null,
        val initialText: String? = null,
    ) : Pushed
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

    // The assistant overlay hands a conversation off by id. Open it once it lands,
    // clearing anything already stacked so it is the top of the app.
    val handoff by com.kamsiob.kamai.assist.Handoff.pending.collectAsStateWithLifecycle()
    LaunchedEffect(handoff) {
        val id = handoff ?: return@LaunchedEffect
        com.kamsiob.kamai.assist.Handoff.consume()
        tab = NavItem.CHATS
        stack.clear()
        stack.add(Pushed.Conversation(id))
    }

    // System integrations: text arriving from the selection menu or share sheet,
    // and bare new-chat / voice requests from the widget and tile.
    val intake by com.kamsiob.kamai.integrations.Intake.pending.collectAsStateWithLifecycle()
    LaunchedEffect(intake) {
        val req = intake ?: return@LaunchedEffect
        com.kamsiob.kamai.integrations.Intake.consume()
        stack.clear()
        when (req.target) {
            com.kamsiob.kamai.integrations.Intake.Target.CHAT -> {
                tab = NavItem.CHATS
                stack.add(Pushed.Conversation(NEW_CONVERSATION, Mode.CHAT, initialText = req.text))
            }
            com.kamsiob.kamai.integrations.Intake.Target.WORKBENCH -> {
                app.setWorkbenchInput(req.text)
                stack.add(Pushed.Workbench)
            }
        }
    }
    val newChatReq by com.kamsiob.kamai.integrations.Intake.newChat.collectAsStateWithLifecycle()
    LaunchedEffect(newChatReq) {
        if (!newChatReq) return@LaunchedEffect
        com.kamsiob.kamai.integrations.Intake.consumeNewChat()
        tab = NavItem.CHATS
        stack.clear()
        stack.add(Pushed.Conversation(NEW_CONVERSATION, Mode.CHAT))
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
                    is Pushed.Conversation -> ConversationScreen(app, pushed.id, pushed.startMode, pushed.initialText)
                    Pushed.Settings -> SettingsHost(app, stack, openUrl)
                    Pushed.Model -> ModelHost(app)
                    Pushed.Voice -> VoiceHost(app)
                    Pushed.Workbench -> WorkbenchHost(app)
                    Pushed.Storage -> StorageHost(app)
                    Pushed.Memory -> MemoryHost(app)
                    Pushed.Questions -> QuestionsScreen()
                    Pushed.About -> AboutHost(app, stack, openUrl)
                    Pushed.Roadmap -> RoadmapScreen()
                    Pushed.Licenses -> LicensesScreen(models = app.tiers)
                    Pushed.CrashReport -> CrashReportHost(stack)
                    Pushed.Appearance -> AppearanceHost(app)
                    Pushed.Safety -> SafetyScreen()
                    Pushed.AppLock -> LockSettingsHost(app)
                }
            }

            if (stack.isEmpty()) {
                val followUpCount by app.followUpCount.collectAsStateWithLifecycle()
                KamBottomNav(
                    current = tab,
                    onSelect = { selected ->
                        if (selected == NavItem.NEW) {
                            stack.add(Pushed.Conversation(NEW_CONVERSATION, Mode.CHAT))
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

    val confirm by app.confirm.collectAsStateWithLifecycle()
    ConfirmDialog(request = confirm, onDismiss = app::dismissConfirm)
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
                onNewChat = { mode ->
                    if (mode == Mode.BENCH) stack.add(Pushed.Workbench)
                    else stack.add(Pushed.Conversation(NEW_CONVERSATION, mode))
                },
                onRename = app::renameConversation,
                onPin = app::setPinned,
                onArchive = app::archive,
                onDelete = { id ->
                    app.deleteConversation(id, conversations.firstOrNull { it.id == id }?.title)
                },
                onDeleteMany = app::deleteConversations,
            )
        }

        NavItem.NEW -> Unit

        NavItem.DISCOVER -> DiscoverHost(app, stack)

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
private fun ConversationScreen(
    app: AppViewModel,
    conversationId: String,
    startMode: Mode? = null,
    initialText: String? = null,
) {
    val context = LocalContext.current
    val chat: ChatViewModel = viewModel(
        key = "chat-$conversationId",
        factory = ChatViewModel.factory(app.repository, app.engine, app.modelManager),
    )

    LaunchedEffect(conversationId) {
        if (conversationId.isNotEmpty()) chat.open(conversationId)
        else startMode?.let { chat.setMode(it) }
    }

    val messages by chat.messages.collectAsStateWithLifecycle()
    val mode by chat.mode.collectAsStateWithLifecycle()
    val streaming by chat.streaming.collectAsStateWithLifecycle()
    val notice by chat.notice.collectAsStateWithLifecycle()
    val activeModel by app.activeModel.collectAsStateWithLifecycle()
    val flagged = remember { mutableStateListOf<String>() }

    // Voice typing. Available only when a speech model is installed and active.
    val recording by chat.recording.collectAsStateWithLifecycle()
    val transcribing by chat.transcribing.collectAsStateWithLifecycle()
    val sttModel by app.activeSttModel.collectAsStateWithLifecycle()
    val ttsVoice by app.activeTtsVoice.collectAsStateWithLifecycle()
    val voiceAvailable = sttModel != null

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            chat.startRecording()
        } else {
            app.showToast("Voice typing needs the microphone. You can turn it on in Settings.")
        }
    }

    // The screen going away should not leave the mic recording or a voice reading.
    DisposableEffect(Unit) {
        onDispose {
            chat.cancelRecording()
            app.stopSpeaking()
        }
    }

    ChatScreen(
        initialComposerText = initialText,
        voiceAvailable = voiceAvailable,
        recording = recording,
        transcribing = transcribing,
        transcribed = chat.transcribed,
        onMicStart = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) chat.startRecording() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        },
        onMicStop = {
            val model = sttModel ?: return@ChatScreen
            chat.stopAndTranscribe(
                com.kamsiob.kamai.voice.Voice.stt(context),
                app.repository.fileForStt(model),
            )
        },
        messages = messages,
        mode = mode,
        streaming = streaming,
        notice = notice,
        modelLabel = activeModel?.displayName,
        flaggedMessageIds = flagged.toSet(),
        // Play is hidden until a reading voice exists, rather than shown doing nothing.
        ttsAvailable = ttsVoice != null,
        onModeChange = chat::setMode,
        onSend = chat::send,
        onStop = chat::stop,
        onFlag = { message ->
            flagged.add(message.id)
            app.flag(message.content, mode, chat.conversationId.value, message.id)
        },
        onRegenerate = chat::regenerate,
        onReport = { message -> reportResponse(context, message.content, app) },
        onShareResponse = { message -> Share.text(context, message.content) },
        onShareThread = {
            Share.text(context, Share.renderThread(null, messages))
        },
        onExportThread = { asMarkdown ->
            Share.exportThread(context, messages.firstOrNull()?.content?.take(40), messages, asMarkdown)
        },
        onShareText = { text -> Share.text(context, text) },
        onFollowUpSelection = { message, text ->
            // The selected excerpt becomes the follow-up content, linked back to
            // the full source response. PART 5.
            app.flag(text, mode, chat.conversationId.value, message.id)
        },
        onPlay = { message -> app.speak(message.content) },
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
    val context = LocalContext.current
    val artifacts by app.artifacts.collectAsStateWithLifecycle()
    val activeModel by app.activeModel.collectAsStateWithLifecycle()
    val confirmChatDelete by app.confirmChatDelete.collectAsStateWithLifecycle()
    val installedStt by app.installedStt.collectAsStateWithLifecycle()

    SettingsScreen(
        activeModel = activeModel,
        storageBytes = artifacts.sumOf { it.sizeBytes },
        // These rows appear as their phases land, rather than sitting there
        // with coming-soon text, which DESIGN.md rules out.
        voiceInstalled = installedStt.isNotEmpty(),
        backupAvailable = false,
        webSearchAvailable = false,
        onModel = { stack.add(Pushed.Model) },
        onVoice = { stack.add(Pushed.Voice) },
        onStorage = { stack.add(Pushed.Storage) },
        onWebSearch = { },
        onBackup = { },
        onDeleteEverything = { app.requestDeleteEverything(includeDownloads = false) },
        confirmChatDelete = confirmChatDelete,
        onConfirmChatDelete = app::setConfirmChatDelete,
        appLockEnabled = com.kamsiob.kamai.lock.AppLock.enabled,
        onAppLock = { stack.add(Pushed.AppLock) },
        isDefaultAssistant = com.kamsiob.kamai.assist.AssistantRole.isDefault(context),
        onAssistant = {
            if (!com.kamsiob.kamai.assist.AssistantRole.openSettings(context)) {
                app.showToast("Open Settings, then Apps, then Default apps, then Digital assistant app.")
            }
        },
        onReplayOnboarding = app::replayOnboarding,
        onAppearance = { stack.add(Pushed.Appearance) },
        onSafety = { stack.add(Pushed.Safety) },
        onQuestions = { stack.add(Pushed.Questions) },
        onAbout = { stack.add(Pushed.About) },
        onMemory = { stack.add(Pushed.Memory) },
        onSupport = {
            openUrl(Links.SUPPORT)
            // The support button also closes the Settings page.
            stack.clear()
        },
    )
}

@Composable
private fun LockSettingsHost(app: AppViewModel) {
    val activity = LocalContext.current as androidx.fragment.app.FragmentActivity
    LockSettingsScreen(
        enabled = AppLock.enabled,
        mode = AppLock.mode,
        biometricEnabled = AppLock.biometricEnabled,
        biometricAvailable = Biometric.canAuthenticate(activity),
        onEnableDevice = {
            AppLock.enable(AppLock.Mode.DEVICE, null)
            app.showToast("App lock is on")
        },
        onEnablePassphrase = { secret ->
            // Add the passphrase layer to the key file so the data itself is
            // gated, then hold the passphrase for this session.
            DatabaseKey.rewrap(activity, currentSecret = null, newSecret = secret)
            AppLock.enable(AppLock.Mode.PASSPHRASE, secret)
            app.showToast("App lock is on")
        },
        onDisable = {
            if (AppLock.mode == AppLock.Mode.PASSPHRASE) {
                DatabaseKey.rewrap(activity, currentSecret = AppLock.sessionSecret, newSecret = null)
            }
            AppLock.disable()
            app.showToast("App lock is off")
        },
        onBiometricToggle = { AppLock.chooseBiometricEnabled(it) },
    )
}

@Composable
private fun AppearanceHost(app: AppViewModel) {
    val dark = when (Appearance.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    AppearanceScreen(
        themeMode = Appearance.themeMode,
        accentId = Appearance.accentId,
        isDark = dark,
        onThemeMode = { Appearance.chooseThemeMode(it) },
        onAccent = { Appearance.chooseAccent(it) },
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
        advanced = com.kamsiob.kamai.model.ModelCatalog.advanced,
        installedIds = artifacts.map { it.id }.toSet(),
        activeId = activeModel?.id,
        download = download,
        onDownload = app::downloadModel,
        onActivate = app::activateModel,
    )
}

@Composable
private fun DiscoverHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
) {
    val context = LocalContext.current
    val vm: com.kamsiob.kamai.ui.discover.DiscoverViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }

    val current by vm.current.collectAsStateWithLifecycle()
    val exhausted by vm.exhausted.collectAsStateWithLifecycle()
    val installedIds by vm.installedIds.collectAsStateWithLifecycle()
    val readerOpen by vm.readerOpen.collectAsStateWithLifecycle()
    val currentSaved by vm.currentSaved.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val manifest by vm.manifest.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    val downloadingPack by vm.downloadingPackId.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

    var showPacks by remember { mutableStateOf(false) }

    LaunchedEffect(notice) { notice?.let { app.showToast(it); vm.dismissNotice() } }

    val openUrl: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { app.showToast("No app on this phone can open that link.") }
    }

    com.kamsiob.kamai.ui.discover.DiscoverScreen(
        current = current,
        exhausted = exhausted,
        hasPacks = installedIds.isNotEmpty(),
        currentSaved = currentSaved,
        saved = saved,
        stats = stats,
        installedCount = installedIds.size,
        onDeal = vm::deal,
        onOpenReader = vm::openReader,
        onToggleSave = vm::toggleSave,
        onQuiz = { vm.quizMe() },
        onReshuffle = vm::reshuffle,
        onOpenPacks = { showPacks = true },
        onOpenSaved = { s ->
            // Tapping a saved moment opens a grounded discussion of its passage.
            vm.openSaved(s.packId, s.momentId) { id -> stack.add(Pushed.Conversation(id)) }
        },
    )

    if (readerOpen && current != null) {
        com.kamsiob.kamai.ui.discover.ReaderSheet(
            moment = current!!,
            onDismiss = vm::closeReader,
            onDiscuss = { vm.discuss { id -> vm.closeReader(); stack.add(Pushed.Conversation(id)) } },
            onExplore = { vm.explore { id -> vm.closeReader(); stack.add(Pushed.Conversation(id)) } },
            onOpenSource = openUrl,
        )
    }

    if (showPacks) {
        com.kamsiob.kamai.ui.discover.PacksSheet(
            manifest = manifest,
            installedIds = installedIds,
            download = download,
            downloadingPackId = downloadingPack,
            onGet = vm::downloadPack,
            onRemove = vm::removePack,
            onDismiss = { showPacks = false },
        )
    }

    // Pre-quiz prompt when the reader was not opened for this card.
    if (quiz is com.kamsiob.kamai.ui.discover.DiscoverViewModel.QuizState.NeedsReader) {
        com.kamsiob.kamai.ui.components.ConfirmDialog(
            request = com.kamsiob.kamai.ui.components.ConfirmRequest(
                tier = com.kamsiob.kamai.ui.components.ConfirmTier.SINGLE,
                title = "Read the full moment first?",
                body = "The quiz is drawn from the full passage, not just the preview. Reading " +
                    "it first gives you a fair shot.",
                confirmLabel = "Quiz me anyway",
                cancelLabel = "Read it first",
                onConfirm = { vm.quizMe(force = true); Unit },
            ),
            onDismiss = { vm.cancelQuiz(); vm.openReader() },
        )
    }

    val quizState = quiz
    if (quizState !is com.kamsiob.kamai.ui.discover.DiscoverViewModel.QuizState.Idle &&
        quizState !is com.kamsiob.kamai.ui.discover.DiscoverViewModel.QuizState.NeedsReader
    ) {
        com.kamsiob.kamai.ui.discover.QuizSheet(
            state = quizState,
            onReveal = vm::revealAnswer,
            onMark = vm::markQuizAnswer,
            onFlag = { q -> vm.flagMissed(q) { app.showToast("Flagged to Follow-ups") } },
            onDone = vm::cancelQuiz,
            onDismiss = vm::cancelQuiz,
        )
    }
}

@Composable
private fun WorkbenchHost(app: AppViewModel) {
    val context = LocalContext.current
    val bench: com.kamsiob.kamai.ui.workbench.WorkbenchViewModel = viewModel(
        factory = com.kamsiob.kamai.ui.workbench.WorkbenchViewModel.factory(
            app.repository, app.engine, app.modelManager,
        ),
    )
    val input by bench.input.collectAsStateWithLifecycle()
    val output by bench.output.collectAsStateWithLifecycle()
    val running by bench.running.collectAsStateWithLifecycle()
    val notice by bench.notice.collectAsStateWithLifecycle()
    val recording by bench.recording.collectAsStateWithLifecycle()
    val transcribing by bench.transcribing.collectAsStateWithLifecycle()
    val sttModel by app.activeSttModel.collectAsStateWithLifecycle()

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) bench.startRecording()
        else app.showToast("Voice input needs the microphone. You can turn it on in Settings.")
    }
    DisposableEffect(Unit) { onDispose { bench.cancelRecording() } }

    com.kamsiob.kamai.ui.workbench.WorkbenchScreen(
        input = input,
        output = output,
        running = running,
        notice = notice,
        voiceAvailable = sttModel != null,
        recording = recording,
        transcribing = transcribing,
        onInputChange = bench::setInput,
        onAction = bench::run,
        onCustom = bench::runCustom,
        onChain = bench::chain,
        onStop = bench::stop,
        onCopied = { app.showToast("Copied") },
        onFlag = { text -> app.flag(text, Mode.BENCH, null, null); app.showToast("Flagged to Follow-ups") },
        onMicStart = {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) bench.startRecording()
            else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        },
        onMicStop = {
            val model = sttModel ?: return@WorkbenchScreen
            bench.stopAndTranscribe(
                com.kamsiob.kamai.voice.Voice.stt(context),
                app.repository.fileForStt(model),
            )
        },
        onDismissNotice = bench::dismissNotice,
    )
}

@Composable
private fun VoiceHost(app: AppViewModel) {
    val installedStt by app.installedStt.collectAsStateWithLifecycle()
    val activeStt by app.activeSttModel.collectAsStateWithLifecycle()
    val download by app.download.collectAsStateWithLifecycle()
    val downloadingStt by app.downloadingSttId.collectAsStateWithLifecycle()
    val installedTts by app.installedTts.collectAsStateWithLifecycle()
    val activeTts by app.activeTtsVoice.collectAsStateWithLifecycle()
    val downloadingTts by app.downloadingTtsId.collectAsStateWithLifecycle()

    com.kamsiob.kamai.ui.settings.VoiceScreen(
        sttModels = com.kamsiob.kamai.voice.SttCatalog.ALL,
        installedSttIds = installedStt.toSet(),
        activeSttId = activeStt?.id,
        recommendedSttId = com.kamsiob.kamai.voice.SttCatalog.recommendedFor(app.totalRamGb).id,
        download = download,
        downloadingSttId = downloadingStt,
        onDownloadStt = app::downloadStt,
        onActivateStt = app::activateStt,
        ttsVoices = com.kamsiob.kamai.voice.TtsCatalog.ALL,
        installedTtsIds = installedTts.toSet(),
        activeTtsId = activeTts?.id,
        downloadingTtsId = downloadingTts,
        onDownloadTts = app::downloadTts,
        onActivateTts = app::activateTts,
        onPreviewTts = app::previewTts,
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
    val mode by app.memoryMode.collectAsStateWithLifecycle()
    MemoryScreen(
        entries = memory,
        mode = mode,
        onModeChange = app::setMemoryMode,
        onForget = { id, text -> app.forget(id, text) },
        onForgetMany = app::forgetMany,
        onForgetAll = app::forgetAll,
    )
}

@Composable
private fun AboutHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    openUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val hasCrash = remember { com.kamsiob.kamai.CrashLog.lastCrash(context) != null }
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
        hasCrashReport = hasCrash,
        onCrashReport = { stack.add(Pushed.CrashReport) },
    )
}

/**
 * Shows the last recorded crash so a user can read it and, if they choose, share
 * it. Nothing here leaves the phone unless the user taps share. Clearing removes
 * the local file.
 */
@Composable
private fun CrashReportHost(
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(com.kamsiob.kamai.CrashLog.lastCrash(context)) }
    com.kamsiob.kamai.ui.settings.CrashReportScreen(
        report = text,
        onShare = {
            val body = text ?: return@CrashReportScreen
            runCatching {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Kam AI crash report")
                            putExtra(Intent.EXTRA_TEXT, body)
                        },
                        "Share crash report",
                    ),
                )
            }
        },
        onClear = {
            com.kamsiob.kamai.CrashLog.clear(context)
            text = null
            stack.remove(Pushed.CrashReport)
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
