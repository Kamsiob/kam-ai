package com.kamsiob.kamai.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
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
    data class Workbench(
        val sessionId: String? = null,
        val forConversation: String? = null,
        /** Opened from a chat that has no conversation yet, so there is nothing
         *  to link to but it must still start empty rather than restoring
         *  somebody's last session. */
        val startFresh: Boolean = false,
    ) : Pushed
    data object Backup : Pushed
    data object Appearance : Pushed
    data object Safety : Pushed
    data object AppLock : Pushed
    data object AutoArchive : Pushed
    data object Archived : Pushed

    /** Everything kept from Discover, on its own screen instead of trailing off
     *  the bottom of the Discover tab. */
    data object SavedMoments : Pushed
    data object CustomInstructions : Pushed
    data class Project(val id: String) : Pushed
    data class Conversation(
        val id: String,
        val startMode: Mode? = null,
        val initialText: String? = null,
        // The view model key for this screen. Existing conversations key by id, so
        // reopening one reuses its state. A new chat has an empty id and gets a
        // unique token instead: without this, every new chat shared the one key
        // "chat-" and the second new chat reopened the first one's conversation,
        // because the cached view model kept the earlier conversation's id. The
        // token is computed once here, at push time, so it stays stable across
        // recompositions of this screen. See item 1 in DECISIONS.md.
        val vmKey: String = conversationVmKey(id),
    ) : Pushed
}

/**
 * The view-model key for a conversation screen. A real conversation id is stable
 * and reused so its state survives navigating away and back; a new chat (empty
 * id) gets a fresh unique token so it can never reuse a previous new chat's view
 * model. [nonce] is injected for testing.
 */
internal fun conversationVmKey(
    id: String,
    nonce: () -> String = { java.util.UUID.randomUUID().toString() },
): String = if (id.isNotEmpty()) id else "new-${nonce()}"

/**
 * Pops the navigation stack, and does nothing when there is nothing to pop (#147).
 *
 * **This is the crash `03299e6` fixed in one form and left in another.** That commit
 * converted every `remove(element)` on a `SnapshotStateList` to `removeAll { }`,
 * because `remove` resolves an index internally and a second activation resolved
 * minus one. The `removeAt(lastIndex)` form has the identical failure and was not
 * covered: `lastIndex` on an empty list *is* minus one, so the call throws
 * "index: -1, size: 0" from the same place.
 *
 * How it is reached, which is the part that makes it a store rejection rather than an
 * exotic case: `BrandBar`'s back action was `onBack = if (stack.isNotEmpty()) { {
 * stack.removeAt(stack.lastIndex) } } else null`. **The emptiness check runs at
 * composition and the lambda runs later**, so two activations arriving before the next
 * recomposition both invoke a lambda that was built when the stack had one entry. The
 * first empties it; the second computes minus one.
 *
 * It arrived through `dispatchKeyEvent`, a key event activating a focused control,
 * which is how somebody using a keyboard or an accessibility service meets it. Exactly
 * as in `03299e6`.
 *
 * Checking inside the lambda is sufficient rather than lucky: Compose dispatches click
 * and key handlers on the main thread, so two activations run in sequence and the
 * second sees the emptied list. That is also why the four sites that already checked
 * inside their lambdas never crashed while this one did.
 */
private fun androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>.popTop() {
    val i = lastIndex
    if (i >= 0) removeAt(i)
}

@Composable
fun KamAiApp(app: AppViewModel = viewModel()) {
    val colors = KamTheme.colors
    val context = LocalContext.current

    val ready by app.ready.collectAsStateWithLifecycle()
    val onboardingDone by app.onboardingDone.collectAsStateWithLifecycle()
    val toast by app.toast.collectAsStateWithLifecycle()
    val toastAction by app.toastAction.collectAsStateWithLifecycle()

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
            // Longer when there is something to undo. 2.2 seconds is fine for an
            // acknowledgement nobody needs to act on, and far too short to read a
            // message, decide, and reach the control.
            delay(if (toastAction != null) 6000 else 2200)
            app.clearToast()
        }
    }

    // Auto-archive runs on launch and whenever Chats comes to the front (#31).
    // Both, because the app can sit open for days: a launch-only pass would never
    // fire for somebody who never closes it. The pass is silent when it finds
    // nothing, which is the ordinary case after the first sweep.
    LaunchedEffect(ready) {
        if (ready) app.runAutoArchive()
    }
    LaunchedEffect(tab, stack.size) {
        // Only when Chats is actually on screen, not merely selected behind a
        // pushed screen, and never while a conversation is open: the open one is
        // exempt anyway, but sweeping underneath somebody mid-read is not the
        // moment for it.
        if (tab == NavItem.CHATS && stack.isEmpty()) app.runAutoArchive()
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
                stack.add(Pushed.Conversation(NEW_CONVERSATION, Mode.GENERAL, initialText = req.text))
            }
            com.kamsiob.kamai.integrations.Intake.Target.WORKBENCH -> {
                app.setWorkbenchInput(req.text)
                stack.add(Pushed.Workbench())
            }
        }
    }
    val newChatReq by com.kamsiob.kamai.integrations.Intake.newChat.collectAsStateWithLifecycle()
    LaunchedEffect(newChatReq) {
        if (!newChatReq) return@LaunchedEffect
        com.kamsiob.kamai.integrations.Intake.consumeNewChat()
        tab = NavItem.CHATS
        stack.clear()
        stack.add(Pushed.Conversation(NEW_CONVERSATION, Mode.GENERAL))
    }

    if (!ready) {
        Box(Modifier.fillMaxSize().background(colors.background))
        return
    }

    if (!onboardingDone) {
        val downloads by app.downloads.collectAsStateWithLifecycle()
        // Onboarding downloads one recommended model; show its progress.
        var voiceQueued by remember { mutableStateOf(false) }
        val onboardingDl = downloads.firstOrNull {
            it.kind == "model" && it.status != com.kamsiob.kamai.download.Downloads.Status.PAUSED
        }
        val startSlide by app.onboardingSlide.collectAsStateWithLifecycle()
        OnboardingScreen(
            startSlide = startSlide,
            freeBytes = app.repository.freeDownloadBytes(),
            onSlideChanged = app::saveOnboardingSlide,
            totalRamGb = app.totalRamGb,
            tiers = app.tiers,
            downloadProgress = onboardingDl?.let {
                if (it.status == com.kamsiob.kamai.download.Downloads.Status.DONE) 1f else it.fraction
            },
            onDownload = app::downloadModel,
            // One recommended speech setup for this phone rather than a choice
            // between transcription tiers, which is not a decision worth handing
            // to somebody who has not used the app yet (#77).
            voiceLabel = com.kamsiob.kamai.voice.SttCatalog
                .recommendedFor(app.totalRamGb).downloadLabel,
            voiceQueued = voiceQueued,
            onAddVoice = {
                voiceQueued = true
                app.downloadStt(com.kamsiob.kamai.voice.SttCatalog.recommendedFor(app.totalRamGb))
            },
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

    // A Discover discussion runs on a sheet over whatever opened it, rather than
    // as a pushed screen (#11). Held here, above the screen stack, because the
    // point of the surface is that the screen underneath stays where it was.
    var groundedTarget by remember {
        mutableStateOf<com.kamsiob.kamai.data.KamRepository.GroundedDiscussion?>(null)
    }

    KamPredictiveBack(
        enabled = stack.isNotEmpty() || tab != NavItem.CHATS,
        onProgress = { backGesture = it },
        onBack = {
            when {
                stack.isNotEmpty() -> stack.popTop()
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
                    { stack.popTop() }
                } else {
                    null
                },
                onSettings = if (stack.isEmpty()) {
                    { stack.add(Pushed.Settings) }
                } else {
                    null
                },
            )

            // Every screen shows what is downloading, not just the one that
            // started it (#81). Under the brand bar and above the screen, so it
            // is present without being in the way, and expanding rather than
            // appearing so nothing jumps under a finger.
            val allDownloads by app.downloads.collectAsStateWithLifecycle()
            com.kamsiob.kamai.ui.components.DownloadIndicator(
                items = allDownloads,
                onOpen = { stack.add(Pushed.Storage) },
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
                    null -> TabContent(
                        app, tab, stack,
                        onGrounded = { groundedTarget = it },
                        onSelectTab = { tab = it },
                    )
                    is Pushed.Conversation -> ConversationScreen(
                        app, pushed.id, pushed.startMode, pushed.initialText, pushed.vmKey,
                        onExit = { stack.popTop() },
                        onOpenModel = { stack.add(Pushed.Model) },
                        onOpenWorkbench = { chatId ->
                            stack.add(
                                Pushed.Workbench(
                                    forConversation = chatId,
                                    startFresh = chatId == null,
                                ),
                            )
                        },
                        onOpenWorkbenchSession = { stack.add(Pushed.Workbench(it)) },
                        onOpenMemory = { stack.add(Pushed.Memory) },
                    )
                    Pushed.Settings -> SettingsHost(app, stack, openUrl)
                    Pushed.Model -> ModelHost(app)
                    Pushed.Voice -> VoiceHost(app)
                    is Pushed.Workbench -> WorkbenchHost(
                        app, stack, pushed.sessionId, pushed.forConversation, pushed.startFresh,
                    )
                    Pushed.Backup -> BackupHost(app)
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
                    Pushed.AutoArchive -> AutoArchiveHost(app)
                    Pushed.Archived -> ArchivedHost(app, stack)
                    Pushed.SavedMoments -> SavedMomentsHost(
                        app, stack, onGrounded = { groundedTarget = it },
                    )
                    Pushed.CustomInstructions -> CustomInstructionsHost(app, stack)
                    is Pushed.Project -> ProjectHost(app, stack, pushed.id)
                }
            }

            if (stack.isEmpty()) {
                val followUpCount by app.followUpCount.collectAsStateWithLifecycle()
                KamBottomNav(
                    current = tab,
                    onSelect = { selected -> tab = selected },
                    followUpCount = followUpCount,
                    modifier = Modifier.navigationBarsPadding(),
                )
            } else {
                Box(Modifier.navigationBarsPadding())
            }
        }

        groundedTarget?.let { target ->
            GroundedSheetHost(
                app = app,
                target = target,
                onClose = { groundedTarget = null },
                onExpanded = {
                    groundedTarget = null
                    stack.add(Pushed.Conversation(target.conversationId))
                },
            )
        }

        KamToast(
            message = toast,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            actionLabel = toastAction?.label,
            onAction = toastAction?.onAction,
        )
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
    onGrounded: (com.kamsiob.kamai.data.KamRepository.GroundedDiscussion) -> Unit,
    /** Switches tab, for a search result that lives on another one (#87). */
    onSelectTab: (NavItem) -> Unit,
) {
    when (tab) {
        NavItem.CHATS -> {
            // The search runs in the database and reaches conversations,
            // follow-ups and projects (#87). Held here rather than in the screen
            // so the screen stays a screen.
            var searchQuery by remember { mutableStateOf("") }
            val searchResults by remember(searchQuery) { app.search(searchQuery) }
                .collectAsStateWithLifecycle(initialValue = null)

            val conversations by app.conversations.collectAsStateWithLifecycle()
            val archived by app.archivedConversations.collectAsStateWithLifecycle()
            val view by app.chatsView.collectAsStateWithLifecycle()
            val chatsProjects by app.projects.collectAsStateWithLifecycle()
            val modeHintPending by app.modeHintPending.collectAsStateWithLifecycle()
            ChatsScreen(
                showModeHint = modeHintPending,
                onDismissModeHint = app::dismissModeHint,
                conversations = conversations,
                archivedCount = archived.size,
                onOpenArchived = { stack.add(Pushed.Archived) },
                view = view,
                onViewChange = app::setChatsView,
                onOpen = { id ->
                    // A Workbench session is a conversation, but it is not a chat:
                    // reopening one has to land back on the surface that made it,
                    // with its text and result, rather than on a transcript of two
                    // messages nobody typed as a conversation (#32).
                    val row = conversations.firstOrNull { it.id == id }
                    if (row?.mode == Mode.BENCH) stack.add(Pushed.Workbench(id))
                    else stack.add(Pushed.Conversation(id))
                },
                onNewChat = { mode ->
                    if (mode == Mode.BENCH) stack.add(Pushed.Workbench())
                    else stack.add(Pushed.Conversation(NEW_CONVERSATION, mode))
                },
                onRename = app::renameConversation,
                onSearch = { searchQuery = it },
                searchResults = searchResults,
                onOpenFollowUps = { onSelectTab(NavItem.FOLLOW_UPS) },
                onOpenProject = { stack.add(Pushed.Project(it)) },
                onPin = app::setPinned,
                onArchive = app::archive,
                onDelete = { id ->
                    app.deleteConversation(id, conversations.firstOrNull { it.id == id }?.title)
                },
                onDeleteMany = app::deleteConversations,
                projectOptions = chatsProjects.map { p -> p.id to p.name },
                onMoveMany = app::assignConversationsToProject,
            )
        }

        NavItem.PROJECTS -> {
            val projects by app.projects.collectAsStateWithLifecycle()
            val projectCounts by app.projectCounts.collectAsStateWithLifecycle()
            com.kamsiob.kamai.ui.projects.ProjectsScreen(
                projects = projects,
                onOpen = { stack.add(Pushed.Project(it)) },
                onCreate = { name -> app.createProject(name) { id -> stack.add(Pushed.Project(id)) } },
                counts = projectCounts,
            )
        }

        NavItem.DISCOVER -> DiscoverHost(app, stack, onGrounded)

        NavItem.FOLLOW_UPS -> {
            val open by app.openFollowUps.collectAsStateWithLifecycle()
            val done by app.completedFollowUps.collectAsStateWithLifecycle()
            FollowUpsScreen(
                open = open,
                completed = done,
                onToggle = app::setFollowUpCompleted,
                onRemove = app::deleteFollowUp,
                onOpenSource = { stack.add(Pushed.Conversation(it)) },
                onOpenMoment = { packId, momentId ->
                    app.openSavedMoment(packId, momentId, onGrounded)
                },
                onSetKind = app::setFollowUpKind,
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
    vmKey: String = conversationId,
    onExit: () -> Unit = {},
    onOpenModel: () -> Unit = {},
    /** Null when the chat has no conversation yet, which is an ordinary new chat. */
    onOpenWorkbench: (String?) -> Unit = {},
    /** Opens the Workbench session this chat is paired with, when it has one. */
    onOpenWorkbenchSession: (String) -> Unit = {},
    /** True inside the Discover sheet, which supplies its own header (#11). */
    scoped: Boolean = false,
    /** Opens the Memory screen from the memory line under an answer (#16). */
    onOpenMemory: () -> Unit = {},
) {
    val context = LocalContext.current
    val chat: ChatViewModel = viewModel(
        key = "chat-$vmKey",
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
    // The tier the phone's memory actually supports, and its catalog entry.
    val recommendedModel = remember(app.recommendedTier) {
        app.tiers.firstOrNull { it.tier == app.recommendedTier }
    }
    val chatDownloads by app.downloads.collectAsStateWithLifecycle()
    val modelDownloading = chatDownloads.any {
        it.kind == "model" && it.status != com.kamsiob.kamai.download.Downloads.Status.DONE
    }
    // Derived from what is stored rather than remembered locally, so it cannot
    // disagree with it. This was a `mutableStateListOf` that started empty on
    // every composition, so reopening a conversation showed every saved reply as
    // unsaved, and tapping the grey bookmark made a duplicate (#128).
    val openUps by app.openFollowUps.collectAsStateWithLifecycle()
    val doneUps by app.completedFollowUps.collectAsStateWithLifecycle()
    val flagged = remember(openUps, doneUps) {
        (openUps + doneUps).mapNotNull { it.messageId }.toSet()
    }

    // Voice typing. Available only when a speech model is installed and active.
    val recording by chat.recording.collectAsStateWithLifecycle()
    val recordedSeconds by chat.recordedSeconds.collectAsStateWithLifecycle()
    val transcribing by chat.transcribing.collectAsStateWithLifecycle()
    val sttModel by app.activeSttModel.collectAsStateWithLifecycle()
    val ttsVoice by app.activeTtsVoice.collectAsStateWithLifecycle()
    val voiceAvailable = sttModel != null
    val attachedName by chat.attachedName.collectAsStateWithLifecycle()
    val speakingId by app.speakingMessageId.collectAsStateWithLifecycle()
    val conversationTitle by chat.title.collectAsStateWithLifecycle()
    val conversationProjectId by chat.projectId.collectAsStateWithLifecycle()
    val grounded by chat.grounded.collectAsStateWithLifecycle()
    val summaryState by chat.summary.collectAsStateWithLifecycle()
    val sessionNumber by app.sessionNumber.collectAsStateWithLifecycle()
    val reminderDismissed by app.reminderDismissed.collectAsStateWithLifecycle()
    val reminderShownThisSession by app.reminderShown.collectAsStateWithLifecycle()
    val linkedSessionId by chat.linkedSessionId.collectAsStateWithLifecycle()
    val allProjects by app.projects.collectAsStateWithLifecycle()

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) chat.attach(context, uri) }

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
    DisposableEffect(sttModel) {
        onDispose {
            // Leaving mid-recording keeps what was said rather than dropping it
            // (#65). The transcription runs in the view model's scope, so it
            // finishes after this screen has gone and lands in the draft.
            val model = sttModel
            if (model != null) {
                chat.stopAndKeepDraft(
                    com.kamsiob.kamai.voice.Voice.stt(context),
                    app.repository.fileForStt(model),
                )
            } else {
                chat.cancelRecording()
            }
            app.stopSpeaking()
        }
    }

    // Collected here rather than passed down, so flipping the toggle updates an
    // already open conversation on the next frame instead of only new ones (#144).
    val showMemoryNote by app.memoryNoteShown.collectAsStateWithLifecycle()

    ChatScreen(
        showMemoryNote = showMemoryNote,
        // The draft wins over a fresh intake text, since a half-written message
        // the user left behind is more theirs than one the share sheet supplied.
        initialComposerText = chat.draft.ifEmpty { initialText.orEmpty() },
        onDraftChanged = chat::rememberDraft,
        initialScrollIndex = chat.scrollIndex,
        initialScrollOffset = chat.scrollOffset,
        hasSavedScroll = chat.hasSavedScroll,
        onScrollChanged = chat::rememberScroll,
        attachedName = attachedName,
        onAttach = {
            pickFile.launch(
                arrayOf(
                    "text/plain", "text/markdown", "text/*", "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ),
            )
        },
        onRemoveAttachment = chat::removeAttachment,
        voiceAvailable = voiceAvailable,
        recording = recording,
        recordedSeconds = recordedSeconds,
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
        onCancelTranscription = {
            chat.cancelTranscription(com.kamsiob.kamai.voice.Voice.stt(context))
        },
        messages = messages,
        mode = mode,
        streaming = streaming,
        notice = notice,
        modelLabel = activeModel?.displayName,
        // Declared per model in the catalog, so a future text-only model hides
        // the paperclip with no code change here (#22).
        canAttachDocuments = activeModel?.supports(
            com.kamsiob.kamai.model.Capability.DOCUMENTS,
        ) ?: false,
        flaggedMessageIds = flagged,
        // Play is hidden until a reading voice exists, rather than shown doing nothing.
        ttsAvailable = ttsVoice != null,
        onModeChange = chat::setMode,
        onOpenModel = onOpenModel,
        onOpenWorkbench = {
            // Always opens, with or without a conversation behind it. This used
            // to be wrapped in `conversationId.value?.let { }`, and a new chat
            // has no conversation until its first message is sent, so choosing
            // Workbench from the picker in a fresh chat did nothing at all: no
            // screen, no error, nothing. The other three modes were fine because
            // setMode copes with a null conversation, which is exactly why this
            // looked like "Workbench is the one mode I cannot switch to".
            val id = chat.conversationId.value
            if (id != null) chat.noteWorkbenchOpened()
            onOpenWorkbench(id)
        },
        // A new message stops any answer that is being read aloud.
        onSend = { text -> app.stopSpeaking(); chat.send(text) },
        onStop = chat::stop,
        speakingMessageId = speakingId,
        onFlag = { message ->
            // No local bookkeeping: the follow-up list is the state, and it
            // updates through the flows above once this lands.
            app.flag(message.content, mode, chat.conversationId.value, message.id)
        },
        onOpenWorkbenchSession = linkedSessionId?.let { session ->
            { onOpenWorkbenchSession(session) }
        },
        onRegenerate = chat::regenerate,
        onContinueIncomplete = chat::continueLast,
        onWrapUp = chat::wrapUp,
        onDiscardIncomplete = chat::discardLast,
        onReport = { message -> reportResponse(context, message.content, app) },
        // Shared as the reader saw it, not as Markdown source.
        onShareResponse = { message ->
            Share.text(context, com.kamsiob.kamai.ui.components.markdownToPlainText(message.content))
        },
        onShareThread = {
            // The conversation's real title, not null: a shared thread used to
            // head "Kam AI conversation" even when it had one (#41).
            Share.text(context, Share.renderThread(conversationTitle, messages))
        },
        onExportThread = { asMarkdown ->
            Share.exportThread(
                context,
                Share.exportName(conversationTitle, messages),
                messages,
                asMarkdown,
            )
        },
        onFollowUpSelection = { message, text ->
            // The selected excerpt becomes the follow-up content, linked back to
            // the full source response. PART 5.
            app.flag(text, mode, chat.conversationId.value, message.id)
        },
        onPlay = { message -> app.toggleSpeak(message.id, message.content) },
        onEdit = chat::editAndResend,
        onDismissNotice = chat::dismissNotice,
        conversationTitle = conversationTitle,
        projectOptions = allProjects.map { it.id to it.name },
        conversationProjectId = conversationProjectId,
        grounded = grounded,
        scoped = scoped,
        onOpenMemory = onOpenMemory,
        onCopied = { app.showToast("Copied") },
        onSummarize = chat::summarize,
        // At most once a session, only in the first few, and never again once
        // dismissed (#84). Counted here because this is where a session is a
        // session; the chat screen only knows about one conversation.
        showCheckReminder = com.kamsiob.kamai.ui.chat.CheckReminder.shouldShow(
            dismissedForever = reminderDismissed,
            sessionNumber = sessionNumber,
            answersThisSession = messages.count { it.role == com.kamsiob.kamai.data.Role.ASSISTANT && !it.incomplete },
            alreadyShownThisSession = reminderShownThisSession,
        ),
        onCheckReminderShown = app::noteCheckReminderShown,
        onDismissCheckReminder = app::dismissCheckReminder,
        summary = summaryState,
        onCancelSummary = chat::cancelSummary,
        onDismissSummary = chat::dismissSummary,
        onSaveSummary = chat::saveSummary,
        onShareText = { text -> Share.text(context, text) },
        // No model means the chat cannot answer, so the chat offers to get one
        // rather than naming the problem and pointing at Settings (#80). Not
        // shown while a download is already running: the indicator above says so
        // and offering it twice would be offering it once too often.
        setup = if (activeModel == null && !modelDownloading) {
            com.kamsiob.kamai.ui.chat.ModelSetupOffer(
                modelName = recommendedModel?.displayName,
                downloadLabel = recommendedModel?.downloadLabel,
                explanation = com.kamsiob.kamai.model.TierRecommendation.explain(app.totalRamGb),
                onDownload = { recommendedModel?.let(app::downloadModel) },
                onSeeOptions = onOpenModel,
            )
        } else {
            null
        },
        onContinueOpen = chat::continueInOpenChat,
        onMoveToProject = { projectId ->
            chat.conversationId.value?.let { app.assignConversationToProject(it, projectId) }
        },
        onRenameConversation = { newTitle ->
            chat.conversationId.value?.let { app.renameConversation(it, newTitle) }
        },
        onArchiveConversation = {
            chat.conversationId.value?.let { app.archive(it) { onExit() } }
        },
        onDeleteConversation = {
            chat.conversationId.value?.let { id ->
                app.deleteConversation(id, conversationTitle) { onExit() }
            }
        },
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
    val autoArchive by app.autoArchive.collectAsStateWithLifecycle()

    SettingsScreen(
        activeModel = activeModel,
        storageBytes = artifacts.sumOf { it.sizeBytes },
        // These rows appear as their phases land, rather than sitting there
        // with coming-soon text, which DESIGN.md rules out.
        voiceInstalled = installedStt.isNotEmpty(),
        backupAvailable = true,
        webSearchAvailable = false,
        onModel = { stack.add(Pushed.Model) },
        onVoice = { stack.add(Pushed.Voice) },
        onStorage = { stack.add(Pushed.Storage) },
        onWebSearch = { },
        onBackup = { stack.add(Pushed.Backup) },
        onDeleteEverything = { app.requestDeleteEverything(includeDownloads = false) },
        confirmChatDelete = confirmChatDelete,
        onConfirmChatDelete = app::setConfirmChatDelete,
        appLockEnabled = com.kamsiob.kamai.lock.AppLock.enabled,
        onAppLock = { stack.add(Pushed.AppLock) },
        onAutoArchive = { stack.add(Pushed.AutoArchive) },
        autoArchive = autoArchive,
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
        onCustomInstructions = { stack.add(Pushed.CustomInstructions) },
        assistantDefaultVoice = app.assistantDefaultVoice.collectAsStateWithLifecycle().value,
        onAssistantDefaultVoice = app::setAssistantDefaultVoice,
        onSupport = {
            openUrl(Links.SUPPORT)
            // The support button also closes the Settings page.
            stack.clear()
        },
    )
}

@Composable
private fun LockSettingsHost(app: AppViewModel) {
    // LocalActivity rather than casting LocalContext, which lint is right about:
    // a Context is not always an Activity and the cast throws rather than failing
    // gracefully. The FragmentActivity cast on top of it is needed by the
    // biometric prompt, so it stays, but it is now reached from something that is
    // actually an activity.
    val activity = androidx.activity.compose.LocalActivity.current
        as? androidx.fragment.app.FragmentActivity
    if (activity == null) {
        // Nothing to show a biometric prompt against. Saying so beats crashing,
        // and this is unreachable in the app as it ships.
        app.showToast("App lock is unavailable here.")
        return
    }
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
    val downloads by app.downloads.collectAsStateWithLifecycle()

    val measuredSpeeds by app.measuredSpeeds.collectAsStateWithLifecycle()
    ModelScreen(
        measuredSpeeds = measuredSpeeds,
        totalRamGb = app.totalRamGb,
        tiers = app.tiers,
        advanced = com.kamsiob.kamai.model.ModelCatalog.advanced,
        installedIds = artifacts.map { it.id }.toSet(),
        activeId = activeModel?.id,
        downloads = downloads,
        onDownload = app::downloadModel,
        onPause = app::pauseDownload,
        onResume = app::resumeDownload,
        onCancel = app::cancelDownload,
        onActivate = app::activateModel,
        freeBytes = app.repository.freeDownloadBytes(),
    )
}

@Composable
private fun DiscoverHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    onGrounded: (com.kamsiob.kamai.data.KamRepository.GroundedDiscussion) -> Unit,
) {
    val context = LocalContext.current
    val vm: com.kamsiob.kamai.ui.discover.DiscoverViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }

    val current by vm.current.collectAsStateWithLifecycle()
    val exhausted by vm.exhausted.collectAsStateWithLifecycle()
    val installedIds by vm.installedIds.collectAsStateWithLifecycle()
    val updatableIds by vm.updatableIds.collectAsStateWithLifecycle()
    val readerOpen by vm.readerOpen.collectAsStateWithLifecycle()
    val currentSaved by vm.currentSaved.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val manifest by vm.manifest.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val quiz by vm.quiz.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()

    var showPacks by remember { mutableStateOf(false) }

    // Leaving Discover cancels a quiz still being made, so it never keeps running
    // and pops up unexpectedly on return (item 5).
    DisposableEffect(Unit) { onDispose { vm.cancelQuiz() } }

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
            // Saved moments always carry both ids; the null-guard is only for the
            // shared follow-up shape.
            val packId = s.packId
            val momentId = s.momentId
            if (packId != null && momentId != null) {
                vm.openSaved(packId, momentId, onGrounded)
            }
        },
        onOpenSavedList = { stack.add(Pushed.SavedMoments) },
    )

    if (readerOpen && current != null) {
        com.kamsiob.kamai.ui.discover.ReaderSheet(
            moment = current!!,
            onDismiss = vm::closeReader,
            // Grounded: a sheet over Discover, not a screen instead of it (#11).
            onDiscuss = { vm.discuss { opened -> vm.closeReader(); onGrounded(opened) } },
            onExplore = { vm.explore { id -> vm.closeReader(); stack.add(Pushed.Conversation(id)) } },
            onOpenSource = openUrl,
        )
    }

    if (showPacks) {
        com.kamsiob.kamai.ui.discover.PacksSheet(
            manifest = manifest,
            installedIds = installedIds,
            updatableIds = updatableIds,
            downloads = downloads,
            onGet = vm::downloadPack,
            onRemove = vm::removePack,
            onPause = vm::pauseDownload,
            onResume = vm::resumeDownload,
            onCancel = vm::cancelDownload,
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
                // Starting a quiz destroys nothing, so it does not wear the
                // reserved gold (#61).
                destructive = false,
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
            onFlag = { q -> vm.flagMissed(q) { app.showToast("Bookmarked to Follow-ups") } },
            onDone = vm::cancelQuiz,
            onDismiss = vm::cancelQuiz,
        )
    }
}

@Composable
private fun WorkbenchHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    sessionId: String? = null,
    forConversation: String? = null,
    startFresh: Boolean = false,
) {
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
    val linkedId by bench.linkedId.collectAsStateWithLifecycle()

    // Reopening a recorded session loads its text and result back in (#32). A
    // Workbench opened from a chat instead starts empty and pairs with that chat
    // once it has produced something (#39).
    LaunchedEffect(sessionId, forConversation, startFresh) {
        when {
            sessionId != null -> bench.openSession(sessionId)
            forConversation != null -> bench.openForConversation(forConversation)
            // Opened from a chat too new to have a conversation. Nothing to link
            // to, but restoring an unrelated session would be the same surprise
            // #39 was about.
            startFresh -> bench.newSession()
        }
    }

    DisposableEffect(Unit) { onDispose { bench.cancelRecording() } }

    com.kamsiob.kamai.ui.workbench.WorkbenchScreen(
        linked = linkedId != null,
        onDiscuss = {
            bench.discussResult { chatId ->
                // Replaces this screen rather than stacking on it, so back from
                // the discussion goes to Chats rather than into the session the
                // user has just moved on from.
                stack.removeLastOrNull()
                stack.add(Pushed.Conversation(chatId))
            }
        },
        onOpenLinked = { linkedId?.let { stack.add(Pushed.Conversation(it)) } },
        onNewSession = bench::newSession,
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
        onFlag = { text -> app.flag(text, Mode.BENCH, null, null); app.showToast("Bookmarked to Follow-ups") },
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
    val downloads by app.downloads.collectAsStateWithLifecycle()
    val installedTts by app.installedTts.collectAsStateWithLifecycle()
    val activeTts by app.activeTtsVoice.collectAsStateWithLifecycle()

    com.kamsiob.kamai.ui.settings.VoiceScreen(
        sttModels = com.kamsiob.kamai.voice.SttCatalog.ALL,
        installedSttIds = installedStt.toSet(),
        activeSttId = activeStt?.id,
        recommendedSttId = com.kamsiob.kamai.voice.SttCatalog.recommendedFor(app.totalRamGb).id,
        downloads = downloads,
        onDownloadStt = app::downloadStt,
        onActivateStt = app::activateStt,
        ttsVoices = com.kamsiob.kamai.voice.TtsCatalog.ALL,
        installedTtsIds = installedTts.toSet(),
        activeTtsId = activeTts?.id,
        onDownloadTts = app::downloadTts,
        onActivateTts = app::activateTts,
        onPreviewTts = app::previewTts,
        onPause = app::pauseDownload,
        onResume = app::resumeDownload,
        onCancel = app::cancelDownload,
    )
}

@Composable
private fun BackupHost(app: AppViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var importPass by remember { mutableStateOf("") }
    var importReplace by remember { mutableStateOf(false) }

    val manager = remember {
        com.kamsiob.kamai.data.BackupManager(
            app.repository, BuildConfig.VERSION_NAME,
            // The database schema version, not the backup format version. Those
            // are different numbers that happened to both be 3.
            com.kamsiob.kamai.data.KamDatabase.VERSION,
        )
    }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            // Export only reads, so a canceled one loses nothing but the
            // half-written file. Kept uncancellable anyway so the file the user
            // just named is either complete or not created by us at all.
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        manager.export(out, exportPass)
                    } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            app.showToast(if (ok) "Backup saved" else "Could not write the backup file.")
        }
    }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            // NonCancellable around the restore itself. `scope` is the
            // composition's, so backing out of Backup and restore cancels it,
            // and a replace-mode restore that stops halfway used to leave the
            // user with everything deleted and only part of the backup written.
            // The transaction in `importSnapshot` makes that atomic at the
            // database level; this stops the coroutine being torn down in the
            // middle of the file read that feeds it.
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        manager.import(input, importPass, importReplace)
                    } ?: error("no stream")
                }.getOrNull()
            }
            busy = false
            app.showToast(result?.message ?: "Could not read the backup file.")
            // Reload app state after a successful restore.
            if (result?.ok == true) app.reloadAfterRestore()
        }
    }

    com.kamsiob.kamai.ui.settings.BackupScreen(
        onExport = { pass ->
            exportPass = pass
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            createDoc.launch("kam-ai-backup-$stamp.kambackup")
        },
        onImport = { pass, replace ->
            importPass = pass
            importReplace = replace
            openDoc.launch(arrayOf("*/*"))
        },
        busy = busy,
    )
}

@Composable
private fun StorageHost(app: AppViewModel) {
    val artifacts by app.artifacts.collectAsStateWithLifecycle()
    val downloads by app.downloads.collectAsStateWithLifecycle()
    StorageScreen(
        artifacts = artifacts,
        onDelete = app::deleteArtifact,
        onDeleteMany = app::deleteArtifacts,
        downloads = downloads,
        onPause = app::pauseDownload,
        onResume = app::resumeDownload,
        onCancel = app::cancelDownload,
    )
}

@Composable
private fun ProjectHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    projectId: String,
) {
    val project by app.observeProject(projectId).collectAsStateWithLifecycle(initialValue = null)
    val conversations by app.conversationsInProject(projectId).collectAsStateWithLifecycle(initialValue = emptyList())
    val unassignedChats by app.conversations.collectAsStateWithLifecycle()
    com.kamsiob.kamai.ui.projects.ProjectScreen(
        project = project,
        conversations = conversations,
        instructionsMax = app.projectInstructionsMax,
        notesMax = app.projectNotesMax,
        onSave = { text, notes ->
            app.saveProject(projectId, project?.name ?: "Project", text, notes)
        },
        // Renaming must carry both fields through, or saving a new name would
        // blank whatever the project had been told (#2).
        onRename = { name ->
            app.saveProject(
                projectId, name, project?.instructions.orEmpty(), project?.notes.orEmpty(),
            )
        },
        onNewChatHere = { mode ->
            app.createProjectChat(projectId, mode) { id -> stack.add(Pushed.Conversation(id)) }
        },
        onOpenConversation = { stack.add(Pushed.Conversation(it)) },
        onRemoveFromProject = { id -> app.assignConversationToProject(id, null) },
        // Only chats that are not already in a project. app.conversations is the
        // main list, which excludes project chats, so it is exactly that set.
        unassigned = unassignedChats,
        onAddExisting = { id -> app.assignConversationToProject(id, projectId) },
        onDelete = {
            app.deleteProject(projectId, project?.name, conversations.size) {
                stack.popTop()
            }
        },
    )
}

@Composable
private fun CustomInstructionsHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
) {
    val current by app.userInstructions.collectAsStateWithLifecycle()
    com.kamsiob.kamai.ui.settings.CustomInstructionsScreen(
        initial = current,
        maxChars = app.systemInstructionsMax,
        onSave = { text ->
            app.saveUserInstructions(text)
            stack.popTop()
        },
    )
}

@Composable
private fun ArchivedHost(
    app: AppViewModel,
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
) {
    val archived by app.archivedConversations.collectAsStateWithLifecycle()
    com.kamsiob.kamai.ui.chats.ArchivedScreen(
        conversations = archived,
        onOpen = { stack.add(Pushed.Conversation(it)) },
        onUnarchive = app::unarchive,
        onDelete = { id -> app.deleteConversation(id, archived.firstOrNull { it.id == id }?.title) },
    )
}

@Composable
private fun MemoryHost(app: AppViewModel) {
    val memory by app.memory.collectAsStateWithLifecycle()
    val mode by app.memoryMode.collectAsStateWithLifecycle()
    val noteShown by app.memoryNoteShown.collectAsStateWithLifecycle()
    MemoryScreen(
        entries = memory,
        mode = mode,
        onModeChange = app::setMemoryMode,
        noteShown = noteShown,
        onNoteShownChange = app::setMemoryNoteShown,
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
            stack.removeAll { it == Pushed.CrashReport }
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


/** The auto-archive window (#31). */
@Composable
private fun AutoArchiveHost(app: AppViewModel) {
    val setting by app.autoArchive.collectAsStateWithLifecycle()
    // Recounted whenever the setting changes, so switching between 3, 7 and 30
    // days answers "and what would that do?" without having to commit to it.
    val dueNow by androidx.compose.runtime.produceState<Int?>(null, setting) {
        value = app.repository.autoArchiveCandidates(setting).size
    }
    com.kamsiob.kamai.ui.settings.AutoArchiveScreen(
        value = setting,
        onChange = { app.setAutoArchive(it) },
        dueNow = dueNow,
    )
}

/**
 * Everything kept from Discover, reached from the Discover tab.
 *
 * Reads the same saved-moment list the tab does, so the two can never disagree,
 * and opens each one the same way: as a grounded discussion of its passage.
 */
@Composable
private fun SavedMomentsHost(
    @Suppress("UNUSED_PARAMETER") app: AppViewModel,
    @Suppress("UNUSED_PARAMETER")
    stack: androidx.compose.runtime.snapshots.SnapshotStateList<Pushed>,
    onGrounded: (com.kamsiob.kamai.data.KamRepository.GroundedDiscussion) -> Unit,
) {
    val vm: com.kamsiob.kamai.ui.discover.DiscoverViewModel = viewModel()
    LaunchedEffect(Unit) { vm.refresh() }
    val saved by vm.saved.collectAsStateWithLifecycle()
    com.kamsiob.kamai.ui.discover.SavedMomentsScreen(
        saved = saved,
        onOpen = { s ->
            val packId = s.packId
            val momentId = s.momentId
            if (packId != null && momentId != null) {
                vm.openSaved(packId, momentId, onGrounded)
            }
        },
    )
}

/**
 * The Discover discussion sheet, with the conversation running inside it (#11).
 *
 * The view model is fetched with the same key `ConversationScreen` uses, so this
 * and the transcript below it are the same instance. That is what lets the
 * header's expand control lift the grounding on the conversation the sheet is
 * showing, rather than on a second copy of it.
 *
 * Expanding is the escape hatch from item 21, not just a change of window: it
 * lifts the passage scope, drops the honest note about a small model's recall
 * into the transcript, and opens the whole history in the full chat. Somebody
 * only reaches for it because the passage would not answer their question.
 */
@Composable
private fun GroundedSheetHost(
    app: AppViewModel,
    target: com.kamsiob.kamai.data.KamRepository.GroundedDiscussion,
    onClose: () -> Unit,
    onExpanded: () -> Unit,
) {
    val chat: ChatViewModel = viewModel(
        key = "chat-${target.conversationId}",
        factory = ChatViewModel.factory(app.repository, app.engine, app.modelManager),
    )
    com.kamsiob.kamai.ui.discover.GroundedSheet(
        title = target.title,
        source = target.source,
        onClose = onClose,
        onExpand = {
            chat.continueInOpenChat()
            onExpanded()
        },
    ) {
        ConversationScreen(app, target.conversationId, scoped = true)
    }
}
