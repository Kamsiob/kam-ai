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
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.TierRecommendation
import com.kamsiob.kamai.ui.components.ConfirmRequest
import com.kamsiob.kamai.ui.components.ConfirmTier
import kotlinx.coroutines.flow.MutableStateFlow
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
    val engine = InferenceEngine(application)

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

            loadActiveModelIfPresent()
            _ready.value = true
        }
    }

    private suspend fun loadActiveModelIfPresent() {
        val model = repository.activeModel() ?: return
        val file = repository.fileFor(model)
        if (file.exists()) engine.load(model, file)
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

    fun downloadModel(model: TierModel) {
        viewModelScope.launch {
            repository.downloader.download(
                url = model.sourceUrl,
                destination = repository.fileFor(model),
                expectedSizeBytes = model.downloadBytes,
                expectedSha256 = model.sha256,
            ).collect { progress ->
                _download.value = progress
                if (progress is Downloader.Progress.Done) {
                    repository.registerModel(model, progress.file)
                    engine.load(model, progress.file)
                    showToast("${model.displayName} is ready")
                }
                if (progress is Downloader.Progress.Failed) {
                    showToast(progress.message)
                }
            }
        }
    }

    fun clearDownload() {
        _download.value = null
    }

    // Conversations

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch {
        repository.setPinned(id, pinned)
        showToast(if (pinned) "Pinned" else "Unpinned")
    }

    fun archive(id: String) = viewModelScope.launch {
        repository.setArchived(id, true)
        showToast("Archived")
    }

    /** Single chat delete. Tier one, and skipped entirely if the user turned
     *  the confirmation off. Never a two-step gauntlet for one chat. */
    fun deleteConversation(id: String, title: String?) {
        val doDelete = {
            viewModelScope.launch {
                repository.deleteConversation(id)
                showToast("Deleted")
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

    fun saveProject(id: String?, name: String, instructions: String) = viewModelScope.launch {
        repository.upsertProject(id, name, instructions)
    }

    fun deleteProject(id: String) = viewModelScope.launch {
        repository.deleteProject(id)
        showToast("Deleted")
    }

    // Storage

    /** Switches the active model and loads it, quickly. PART 2. */
    fun activateModel(model: TierModel) = viewModelScope.launch {
        val file = repository.fileFor(model)
        if (!file.exists()) {
            showToast("That model is not downloaded.")
            return@launch
        }
        repository.setActiveModel(model.id)
        engine.load(model, file)
        showToast("${model.displayName} is now in use")
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
                        val wasActive = repository.activeModel()?.id == id
                        val fallback = if (wasActive) repository.nextModelAfterDeleting(id) else null
                        repository.deleteArtifact(id)
                        // Deleting the active model must never leave the app with
                        // nothing usable. Fall back to another installed model, or
                        // unload entirely so the user is sent to download one.
                        if (wasActive) {
                            if (fallback != null) {
                                val file = repository.fileFor(fallback)
                                repository.setActiveModel(fallback.id)
                                if (file.exists()) engine.load(fallback, file)
                                showToast("Deleted. ${fallback.displayName} is now in use.")
                            } else {
                                engine.unload()
                                showToast("Deleted. Download a model to keep chatting.")
                            }
                        } else {
                            showToast("Deleted")
                        }
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { engine.unload() }
    }
}
