package com.kamsiob.kamai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamsiob.kamai.data.ArtifactEntity
import com.kamsiob.kamai.data.ConversationSummary
import com.kamsiob.kamai.data.FollowUpEntity
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.MemoryEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.ProjectEntity
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.llm.MemoryMode
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.TierRecommendation
import com.kamsiob.kamai.ui.components.ConfirmRequest
import com.kamsiob.kamai.ui.components.ConfirmTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-wide state: which model is loaded, what has been downloaded, the lists
 * every screen reads, and the one-line toasts.
 *
 * One view model rather than one per screen. The screens in this app share far
 * more state than they own, and threading a repository through six of them buys
 * nothing but ceremony.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    val repository = KamRepository.get(application)

    /** The one engine and the one manager, shared process-wide. The manager is
     *  the single source of truth for what is resident. See ModelManager. */
    val engine = com.kamsiob.kamai.llm.Models.engine(application)
    val modelManager = com.kamsiob.kamai.llm.Models.manager(application)
    val modelStatus get() = modelManager.status

    /** Chats list view. Compact is the default and the last used is restored. */
    enum class ChatsView { COMFORTABLE, COMPACT, GRID }

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _chatsView = MutableStateFlow(ChatsView.COMPACT)
    val chatsView: StateFlow<ChatsView> = _chatsView.asStateFlow()

    private val _download = MutableStateFlow<Downloader.Progress?>(null)
    val download: StateFlow<Downloader.Progress?> = _download.asStateFlow()

    /** The one confirmation dialog, driven centrally. */
    private val _confirm = MutableStateFlow<ConfirmRequest?>(null)
    val confirm: StateFlow<ConfirmRequest?> = _confirm.asStateFlow()

    /** Whether deleting a single chat asks first. Default on. PART 0. */
    private val _confirmChatDelete = MutableStateFlow(true)
    val confirmChatDelete: StateFlow<Boolean> = _confirmChatDelete.asStateFlow()

    private val _memoryMode = MutableStateFlow(MemoryMode.MANUAL)
    val memoryMode: StateFlow<MemoryMode> = _memoryMode.asStateFlow()

    val totalRamGb: Int = repository.totalRamGb()
    val tiers: List<TierModel> = ModelCatalog.defaults
    val recommendedTier = TierRecommendation.recommended(totalRamGb)

    val conversations: StateFlow<List<ConversationSummary>> =
        repository.observeConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openFollowUps: StateFlow<List<FollowUpEntity>> =
        repository.observeOpenFollowUps()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completedFollowUps: StateFlow<List<FollowUpEntity>> =
        repository.observeCompletedFollowUps()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val followUpCount: StateFlow<Int> =
        repository.observeOpenFollowUpCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val artifacts: StateFlow<List<ArtifactEntity>> =
        repository.observeArtifacts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memory: StateFlow<List<MemoryEntity>> =
        repository.observeMemory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> =
        repository.observeProjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeModel: StateFlow<TierModel?> =
        repository.observeActiveModel()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The active speech-to-text model, or null when voice typing is not set up. */
    val activeSttModel: StateFlow<com.kamsiob.kamai.voice.SttModel?> =
        repository.observeActiveSttModel()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // A process death can leave a response half written. Repair it
            // before any screen reads it, so it is never shown as if finished.
            repository.repairIncompleteMessages()

            _onboardingDone.value = repository.isOnboardingDone()
            _chatsView.value = runCatching {
                ChatsView.valueOf(repository.setting(KamRepository.Keys.CHATS_VIEW).orEmpty())
            }.getOrDefault(ChatsView.COMPACT)
            _confirmChatDelete.value =
                repository.setting(KamRepository.Keys.CONFIRM_CHAT_DELETE) != "false"
            _memoryMode.value = repository.memoryMode()

            // Never load a model at startup. The manager only reads which model
            // is active and repairs a dangling reference; loading happens lazily
            // on first use. This is the fix for the blank-screen-at-launch bug:
            // startup no longer blocks on loading a multi-gigabyte model.
            modelManager.refreshActive()
            // Re-surface any download a process death interrupted, so a partial
            // on disk becomes a resumable "Paused" row instead of vanishing.
            restoreInterruptedDownloads()
            _ready.value = true
        }
    }

    fun requestConfirm(request: ConfirmRequest) { _confirm.value = request }
    fun dismissConfirm() { _confirm.value = null }

    fun setConfirmChatDelete(on: Boolean) {
        _confirmChatDelete.value = on
        viewModelScope.launch {
            repository.putSetting(KamRepository.Keys.CONFIRM_CHAT_DELETE, on.toString())
        }
    }

    fun showToast(message: String) {
        _toast.value = message
    }

    fun clearToast() {
        _toast.value = null
    }

    fun setChatsView(view: ChatsView) {
        _chatsView.value = view
        viewModelScope.launch {
            repository.putSetting(KamRepository.Keys.CHATS_VIEW, view.name)
        }
    }

    // Onboarding

    fun finishOnboarding() {
        viewModelScope.launch {
            repository.markOnboardingDone()
            _onboardingDone.value = true
        }
    }

    fun replayOnboarding() {
        viewModelScope.launch {
            repository.replayOnboarding()
            _onboardingDone.value = false
        }
    }

    // Downloads

    private var downloadJob: kotlinx.coroutines.Job? = null
    private var downloadingModelId: String? = null

    /** Live state of every download in flight, from the background manager. */
    val downloads: StateFlow<List<com.kamsiob.kamai.download.Downloads.Item>> =
        com.kamsiob.kamai.download.Downloads.items

    /** The download spec for a model, used both to start and to restore one. */
    private fun modelSpec(model: TierModel) = com.kamsiob.kamai.download.Downloads.Spec(
        id = model.id,
        displayName = model.displayName,
        kind = "model",
        url = model.sourceUrl,
        destination = repository.fileFor(model),
        sizeBytes = model.downloadBytes,
        sha256 = model.sha256,
        onInstalled = { file ->
            repository.registerModel(model, file, makeActive = false)
            modelManager.onModelInstalled(model)
            showToast("${model.displayName} is ready")
        },
    )

    fun downloadModel(model: TierModel) {
        // A download brings memory and disk pressure; free an idle resident model
        // first. It never triggers a load or changes the active model.
        viewModelScope.launch { modelManager.onDownloadStarting() }
        com.kamsiob.kamai.download.Downloads.start(
            getApplication(), repository.downloader, modelSpec(model),
        )
    }

    /**
     * After a restart, a model download that a process death interrupted has a
     * partial file on disk but no live state. Re-surface each as a paused,
     * resumable download so the progress is not silently lost.
     */
    private fun restoreInterruptedDownloads() {
        for (model in com.kamsiob.kamai.model.ModelCatalog.all) {
            com.kamsiob.kamai.download.Downloads.restorePaused(modelSpec(model))
        }
    }

    fun pauseDownload(id: String) = com.kamsiob.kamai.download.Downloads.pause(getApplication(), id)
    fun cancelDownload(id: String) = com.kamsiob.kamai.download.Downloads.cancel(getApplication(), id)
    fun resumeDownload(id: String) =
        com.kamsiob.kamai.download.Downloads.resume(getApplication(), repository.downloader, id)
    fun dismissDownload(id: String) = com.kamsiob.kamai.download.Downloads.dismiss(id)

    fun clearDownload() {
        _download.value = null
    }

    /** Seeds the Workbench source, used by the share and selection integrations. */
    fun setWorkbenchInput(text: String) = viewModelScope.launch {
        repository.putSetting(KamRepository.Keys.WORKBENCH_INPUT, text)
    }

    // System-wide custom instructions (item 15).

    val systemInstructionsMax: Int get() = repository.systemInstructionsMax

    val userInstructions: StateFlow<String> =
        repository.observeUserInstructions()
            .map { it.orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun saveUserInstructions(text: String) = viewModelScope.launch {
        repository.setUserInstructions(text)
        showToast(if (text.isBlank()) "Cleared" else "Saved")
    }

    /**
     * Re-reads the one-shot settings after a restore. The list flows are backed by
     * Room and refresh themselves; these preferences are read once at startup, so
     * a restore that changed them needs a nudge. The active model reference is
     * repaired so a backup from another phone never points at a model not here.
     */
    fun reloadAfterRestore() = viewModelScope.launch {
        _chatsView.value = runCatching {
            ChatsView.valueOf(repository.setting(KamRepository.Keys.CHATS_VIEW).orEmpty())
        }.getOrDefault(ChatsView.COMPACT)
        _confirmChatDelete.value = repository.setting(KamRepository.Keys.CONFIRM_CHAT_DELETE) != "false"
        _memoryMode.value = repository.memoryMode()
        modelManager.refreshActive()
    }

    // Conversations

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch {
        repository.setPinned(id, pinned)
        showToast(if (pinned) "Pinned" else "Unpinned")
    }

    fun archive(id: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        repository.setArchived(id, true)
        showToast("Archived")
        onDone()
    }

    /** Archiving is reversible, unlike deletion. */
    fun unarchive(id: String) = viewModelScope.launch {
        repository.setArchived(id, false)
        showToast("Moved back to Chats")
    }

    /** Archived conversations, for the archived view reached from Chats. */
    val archivedConversations: StateFlow<List<com.kamsiob.kamai.data.ConversationSummary>> =
        repository.observeArchived()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun renameConversation(id: String, title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        repository.renameConversation(id, title)
        showToast("Renamed")
    }

    /** Single chat delete. Tier one, and skipped entirely if the user turned
     *  the confirmation off. Never a two-step gauntlet for one chat. */
    fun deleteConversation(id: String, title: String?, onDone: () -> Unit = {}) {
        val doDelete = {
            viewModelScope.launch {
                repository.deleteConversation(id)
                showToast("Deleted")
                onDone()
            }
            Unit
        }
        if (_confirmChatDelete.value) {
            requestConfirm(
                ConfirmRequest(
                    tier = ConfirmTier.SINGLE,
                    title = "Delete this chat?",
                    body = "\"${title ?: "This conversation"}\" and its messages will be removed.",
                    confirmLabel = "Delete",
                    onConfirm = doDelete,
                ),
            )
        } else {
            doDelete()
        }
    }

    /** Bulk chat delete. Tier two, because deleting several at once is major. */
    fun deleteConversations(ids: List<String>) {
        if (ids.isEmpty()) return
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.MAJOR,
                title = "Delete ${ids.size} chats?",
                body = "This removes ${ids.size} conversations and everything in them.",
                undoneNote = "Those ${ids.size} conversations will be gone for good. A backup " +
                    "is the only way to bring them back.",
                confirmLabel = "Delete ${ids.size}",
                onConfirm = {
                    viewModelScope.launch {
                        ids.forEach { repository.deleteConversation(it) }
                        showToast("Deleted ${ids.size}")
                    }
                },
            ),
        )
    }

    // Follow-ups

    fun flag(snippet: String, mode: Mode, conversationId: String?, messageId: String?) =
        viewModelScope.launch {
            repository.flag(snippet, mode, conversationId, messageId)
            showToast("Saved to Follow-ups")
        }

    fun setFollowUpCompleted(id: String, completed: Boolean) = viewModelScope.launch {
        repository.setFollowUpCompleted(id, completed)
    }

    /** Single follow-up: light swipe-remove with a toast, no dialog. It is the
     *  least destructive thing in the app and a bookmark is cheap to recreate. */
    fun deleteFollowUp(id: String) = viewModelScope.launch {
        repository.deleteFollowUp(id)
        showToast("Removed")
    }

    // Memory and projects

    fun forget(id: String, text: String? = null) {
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.SINGLE,
                title = "Forget this?",
                body = text?.let { "\"${it.take(80)}\"" } ?: "This memory will be removed.",
                confirmLabel = "Forget",
                onConfirm = {
                    viewModelScope.launch {
                        repository.forget(id)
                        showToast("Forgotten")
                    }
                    Unit
                },
            ),
        )
    }

    fun setMemoryMode(mode: MemoryMode) {
        _memoryMode.value = mode
        viewModelScope.launch { repository.setMemoryMode(mode) }
    }

    fun forgetAll() {
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.MAJOR,
                title = "Forget everything?",
                body = "Every memory Kam AI has kept will be removed.",
                undoneNote = "All of it will be gone for good.",
                confirmLabel = "Forget all",
                onConfirm = {
                    viewModelScope.launch {
                        repository.forgetAllMemory()
                        showToast("Memory cleared")
                    }
                    Unit
                },
            ),
        )
    }

    fun forgetMany(ids: List<String>) {
        if (ids.isEmpty()) return
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.MAJOR,
                title = "Forget ${ids.size} memories?",
                body = "The selected ${ids.size} memories will be removed.",
                undoneNote = "Those ${ids.size} memories will be gone for good.",
                confirmLabel = "Forget ${ids.size}",
                onConfirm = {
                    viewModelScope.launch {
                        ids.forEach { repository.forget(it) }
                        showToast("Forgotten ${ids.size}")
                    }
                    Unit
                },
            ),
        )
    }

    // Projects (item 2).

    val projectInstructionsMax: Int get() = repository.projectInstructionsMax

    fun observeProject(id: String) = repository.observeProject(id)
    fun conversationsInProject(id: String) = repository.conversationsInProject(id)

    /** Creates a project and returns its id through [onCreated] so the caller can open it. */
    fun createProject(name: String, onCreated: (String) -> Unit = {}) = viewModelScope.launch {
        val id = repository.upsertProject(null, name.ifBlank { "New project" }, "")
        onCreated(id)
    }

    fun saveProject(id: String?, name: String, instructions: String) = viewModelScope.launch {
        repository.upsertProject(id, name, instructions)
        showToast("Saved")
    }

    /** Starts a new chat that already belongs to [projectId]. */
    fun createProjectChat(projectId: String, onReady: (String) -> Unit) = viewModelScope.launch {
        val id = repository.createConversation(com.kamsiob.kamai.data.Mode.CHAT, projectId)
        onReady(id)
    }

    /** Moves a conversation into a project, or out to the general list (null). */
    fun assignConversationToProject(conversationId: String, projectId: String?) = viewModelScope.launch {
        repository.assignConversationToProject(conversationId, projectId)
        showToast(if (projectId == null) "Moved to Chats" else "Moved to project")
    }

    fun assignConversationsToProject(ids: List<String>, projectId: String?) = viewModelScope.launch {
        ids.forEach { repository.assignConversationToProject(it, projectId) }
        showToast(if (projectId == null) "Moved ${ids.size} to Chats" else "Moved ${ids.size}")
    }

    /**
     * Deletes a project. Its conversations are never silently destroyed: they are
     * moved back to the general Chats list, and the confirmation says so plainly.
     * The user can then delete any of them from Chats if they choose.
     */
    fun deleteProject(id: String, name: String?, conversationCount: Int, onDone: () -> Unit = {}) {
        val body = if (conversationCount == 0) {
            "This empty project will be removed."
        } else {
            "Its $conversationCount ${if (conversationCount == 1) "chat" else "chats"} will move back " +
                "to Chats, not be deleted. The project itself will be removed."
        }
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.SINGLE,
                title = "Delete ${name ?: "this project"}?",
                body = body,
                confirmLabel = "Delete project",
                onConfirm = {
                    viewModelScope.launch {
                        repository.deleteProject(id, deleteConversations = false)
                        showToast(if (conversationCount == 0) "Deleted" else "Project deleted, chats kept")
                        onDone()
                    }
                    Unit
                },
            ),
        )
    }

    // Storage

    /** Switches the active model. The manager fully unloads the current one
     *  first; the new one loads lazily on next use. PART 2. */
    fun activateModel(model: TierModel) = viewModelScope.launch {
        if (!repository.fileFor(model).exists()) {
            showToast("That model is not downloaded.")
            return@launch
        }
        modelManager.switchTo(model)
        showToast("${model.displayName} is now in use")
    }

    // Voice: speech-to-text model management.

    /** Ids of installed STT models, for the Voice screen's installed state. */
    val installedStt: StateFlow<List<String>> =
        repository.observeSttArtifacts()
            .map { list -> list.map { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun downloadStt(model: com.kamsiob.kamai.voice.SttModel) {
        com.kamsiob.kamai.download.Downloads.start(
            getApplication(), repository.downloader,
            com.kamsiob.kamai.download.Downloads.Spec(
                id = model.id,
                displayName = model.displayName,
                kind = "voice",
                url = model.sourceUrl,
                destination = repository.fileForStt(model),
                sizeBytes = model.downloadBytes,
                sha256 = model.sha256,
                onInstalled = { file ->
                    repository.registerSttModel(model, file, makeActive = true)
                    showToast("${model.displayName} is ready")
                },
            ),
        )
    }

    fun activateStt(model: com.kamsiob.kamai.voice.SttModel) = viewModelScope.launch {
        if (!repository.fileForStt(model).exists()) {
            showToast("That voice model is not downloaded.")
            return@launch
        }
        repository.setActiveSttModel(model.id)
        showToast("${model.displayName} is now in use")
    }

    // Voice: text-to-speech voice management.

    val installedTts: StateFlow<List<String>> =
        repository.observeTtsArtifacts()
            .map { list -> list.map { it.id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeTtsVoice: StateFlow<com.kamsiob.kamai.voice.TtsVoice?> =
        repository.observeActiveTtsVoice()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun downloadTts(voice: com.kamsiob.kamai.voice.TtsVoice) {
        com.kamsiob.kamai.download.Downloads.start(
            getApplication(), repository.downloader,
            com.kamsiob.kamai.download.Downloads.Spec(
                id = voice.id,
                displayName = voice.displayName,
                kind = "voice",
                url = voice.sourceUrl,
                destination = repository.fileForTts(voice),
                sizeBytes = voice.downloadBytes,
                sha256 = voice.sha256,
                onInstalled = { file ->
                    repository.registerTtsVoice(voice, file, makeActive = true)
                    showToast("${voice.displayName} is ready")
                },
            ),
        )
    }

    fun activateTts(voice: com.kamsiob.kamai.voice.TtsVoice) = viewModelScope.launch {
        if (!repository.fileForTts(voice).exists()) {
            showToast("That voice is not downloaded.")
            return@launch
        }
        repository.setActiveTtsVoice(voice.id)
        showToast("${voice.displayName} is now the reading voice")
    }

    /** Previews a voice by reading a short line aloud. */
    fun previewTts(voice: com.kamsiob.kamai.voice.TtsVoice) = viewModelScope.launch {
        val file = repository.fileForTts(voice)
        if (!file.exists()) {
            showToast("Download this voice first.")
            return@launch
        }
        val tts = com.kamsiob.kamai.voice.Voice.tts(getApplication())
        val result = tts.speak(
            voice, file,
            "This is how I sound. I read your notes and drafts aloud, all on your phone.",
        )
        if (result is com.kamsiob.kamai.voice.TtsEngine.Result.Error) showToast(result.message)
    }

    /** The message currently being read aloud, so its control shows Stop. */
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()
    private var speakJob: kotlinx.coroutines.Job? = null

    /**
     * Reads an assistant message aloud, or stops it if it is already the one
     * speaking, so the play control genuinely toggles. Only one thing speaks at a
     * time: starting a new one stops whatever was playing first.
     */
    fun toggleSpeak(messageId: String, text: String) {
        if (_speakingMessageId.value == messageId) {
            stopSpeaking()
            return
        }
        speakJob?.cancel()
        com.kamsiob.kamai.voice.Voice.tts(getApplication()).stop()
        _speakingMessageId.value = messageId
        speakJob = viewModelScope.launch {
            val voice = repository.activeTtsVoice()
            val file = voice?.let { repository.fileForTts(it) }
            if (voice == null || file == null || !file.exists()) {
                if (_speakingMessageId.value == messageId) _speakingMessageId.value = null
                return@launch
            }
            try {
                val result = com.kamsiob.kamai.voice.Voice.tts(getApplication()).speak(voice, file, text)
                if (result is com.kamsiob.kamai.voice.TtsEngine.Result.Error) showToast(result.message)
            } finally {
                // Clear only if a newer request has not already taken over.
                if (_speakingMessageId.value == messageId) _speakingMessageId.value = null
            }
        }
    }

    fun stopSpeaking() {
        speakJob?.cancel()
        com.kamsiob.kamai.voice.Voice.tts(getApplication()).stop()
        _speakingMessageId.value = null
    }

    fun deleteArtifact(id: String, name: String? = null) {
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.SINGLE,
                title = "Delete ${name ?: "this download"}?",
                body = "It will be removed from this phone. You can download it again later.",
                confirmLabel = "Delete",
                onConfirm = {
                    viewModelScope.launch {
                        val model = com.kamsiob.kamai.model.ModelCatalog.byId(id)
                        if (model != null) {
                            // A model: the manager unloads it if resident, cancels
                            // its download if mid-flight, removes it, and repairs
                            // the active reference so nothing is left dangling.
                            val midDownload = downloadingModelId == id
                            modelManager.delete(
                                model = model,
                                midDownload = midDownload,
                                cancelDownload = {
                                    downloadJob?.cancel()
                                    downloadingModelId = null
                                    repository.deletePartialDownload(model)
                                },
                                removeArtifact = { repository.deleteArtifact(id) },
                            )
                            showToast("Deleted")
                        } else {
                            // A voice or pack: a plain single delete.
                            repository.deleteArtifact(id)
                            showToast("Deleted")
                        }
                    }
                    Unit
                },
            ),
        )
    }

    /**
     * Delete a chosen set of downloads at once. One confirmation, then each is
     * removed the same careful way a single delete is: a model unloads and cancels
     * any in-flight download; a voice or pack is a plain removal.
     */
    fun deleteArtifacts(ids: List<String>) {
        if (ids.isEmpty()) return
        if (ids.size == 1) {
            val only = artifacts.value.firstOrNull { it.id == ids.first() }
            deleteArtifact(ids.first(), only?.displayName)
            return
        }
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.SINGLE,
                title = "Delete ${ids.size} downloads?",
                body = "They will be removed from this phone. You can download them again later.",
                confirmLabel = "Delete ${ids.size}",
                onConfirm = {
                    viewModelScope.launch {
                        for (id in ids) {
                            val model = com.kamsiob.kamai.model.ModelCatalog.byId(id)
                            if (model != null) {
                                modelManager.delete(
                                    model = model,
                                    midDownload = downloadingModelId == id,
                                    cancelDownload = {
                                        downloadJob?.cancel()
                                        downloadingModelId = null
                                        repository.deletePartialDownload(model)
                                    },
                                    removeArtifact = { repository.deleteArtifact(id) },
                                )
                            } else {
                                repository.deleteArtifact(id)
                            }
                        }
                        showToast("Deleted ${ids.size}")
                    }
                    Unit
                },
            ),
        )
    }

    fun requestDeleteEverything(includeDownloads: Boolean) {
        requestConfirm(
            ConfirmRequest(
                tier = ConfirmTier.MAJOR,
                title = "Delete everything?",
                body = "Every conversation, everything remembered, every project, and " +
                    "every follow-up." + if (includeDownloads) " Downloaded models too." else "",
                undoneNote = "This erases all of it and cannot be undone. If you have a " +
                    "backup, that is the only way to bring any of it back.",
                confirmWord = "delete",
                confirmLabel = "Delete everything",
                onConfirm = {
                    viewModelScope.launch {
                        repository.deleteEverything(includeDownloads)
                        if (includeDownloads) engine.unload()
                        showToast("Everything is gone")
                    }
                    Unit
                },
            ),
        )
    }

    // The engine and manager are process-wide singletons now, so the view model
    // being cleared must not unload them; unloading is the manager's decision,
    // driven by memory pressure and the app going to the background.
}
