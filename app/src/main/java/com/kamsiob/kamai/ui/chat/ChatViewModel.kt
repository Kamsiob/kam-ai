package com.kamsiob.kamai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.ChatFormat
import com.kamsiob.kamai.llm.ConversationTitler
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.ModelManager
import com.kamsiob.kamai.llm.MemoryExtractor
import com.kamsiob.kamai.llm.MemoryMode
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives one conversation: sending, streaming, stopping, regenerating, and
 * editing. All four of those end up in the same place, which is why they share
 * [respond] rather than each building their own prompt.
 */
class ChatViewModel(
    private val repository: KamRepository,
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _mode = MutableStateFlow(Mode.CHAT)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    /** Set when generation stopped for a reason worth saying out loud. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var generation: Job? = null

    // Voice typing. The recorder captures 16 kHz mono; transcription runs through
    // the injected SttEngine, which loads and unloads whisper within the call so
    // it never sits resident next to the language model.
    private val recorder = com.kamsiob.kamai.voice.AudioRecorder()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    /** Emits transcribed text for the composer to place in its field. */
    private val _transcribed = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcribed: kotlinx.coroutines.flow.SharedFlow<String> = _transcribed

    fun startRecording() {
        if (_recording.value || _transcribing.value) return
        if (recorder.start(viewModelScope)) {
            _recording.value = true
        } else {
            _notice.value = "The microphone could not be opened. Check it is not in use elsewhere."
        }
    }

    /** Stops recording and transcribes with [stt] using [modelFile]. */
    fun stopAndTranscribe(stt: com.kamsiob.kamai.voice.SttEngine, modelFile: java.io.File) {
        if (!_recording.value) return
        _recording.value = false
        val pcm = recorder.stop()
        _transcribing.value = true
        viewModelScope.launch {
            when (val r = stt.transcribe(modelFile, pcm)) {
                is com.kamsiob.kamai.voice.SttEngine.Result.Ok -> _transcribed.emit(r.text)
                is com.kamsiob.kamai.voice.SttEngine.Result.Error -> _notice.value = r.message
            }
            _transcribing.value = false
        }
    }

    /** Abandons an in-flight recording, for example when the screen goes away. */
    fun cancelRecording() {
        if (_recording.value) {
            recorder.cancel()
            _recording.value = false
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The open conversation's title, live, so the header updates when it is set. */
    val title: StateFlow<String?> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else repository.observeConversation(id).map { it?.title }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A background titling pass for a conversation opened without a title. */
    private var titlingJob: kotlinx.coroutines.Job? = null

    fun open(conversationId: String) {
        _conversationId.value = conversationId
        viewModelScope.launch {
            repository.conversation(conversationId)?.let { _mode.value = it.mode }
            _attachedName.value = repository.attachmentName(conversationId)
        }
        // Safety net for item 17: any conversation that gained content elsewhere
        // and never got a title (an interrupted generation, an older entry point)
        // is titled now, through the shared path. titleIfNeeded no-ops when a
        // title already exists, so this only generates when something is missing.
        // It is cancelled the moment a real turn starts, so it never runs on the
        // engine at the same time as a reply.
        titlingJob?.cancel()
        titlingJob = viewModelScope.launch {
            ConversationTitler.titleIfNeeded(repository, engine, conversationId)
        }
    }

    fun setMode(mode: Mode) {
        if (mode == _mode.value) return
        _mode.value = mode
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            // Persist the switch so it survives reopening, and drop a quiet marker
            // into the transcript at the switch point, but only once the
            // conversation has real content to mark.
            repository.setConversationMode(convId, mode)
            val history = repository.messages(convId)
            if (history.any { it.role == Role.USER || it.role == Role.ASSISTANT }) {
                repository.addMessage(convId, Role.SYSTEM, SystemPrompts.modeSwitchNotice(mode))
            }
        }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _streaming.value) return

        // Flip to working immediately, before any database write or model load,
        // so the thinking indicator appears the instant the user taps send rather
        // than after the model has finished loading and ingesting the prompt.
        _streaming.value = true
        viewModelScope.launch {
            val id = _conversationId.value ?: repository.createConversation(_mode.value).also {
                _conversationId.value = it
            }
            repository.addMessage(id, Role.USER, trimmed)
            maybeManualRemember(id, trimmed)
            respond(id)
        }
    }

    /** Replaces the last response rather than adding another one. */
    fun regenerate() {
        if (_streaming.value) return
        val id = _conversationId.value ?: return

        viewModelScope.launch {
            val history = repository.messages(id)
            val lastAssistant = history.lastOrNull { it.role == Role.ASSISTANT } ?: return@launch
            repository.deleteMessage(lastAssistant.id)
            respond(id)
        }
    }

    /**
     * Editing truncates everything after the edited message and re-answers.
     * There is deliberately no branching, so the old tail is gone.
     */
    fun editAndResend(message: MessageEntity, newText: String) {
        if (_streaming.value) return
        val id = _conversationId.value ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.truncateAfter(id, message)
            repository.updateMessage(message.id, trimmed, incomplete = false)
            respond(id)
        }
    }

    fun stop() {
        engine.requestStop()
        generation?.cancel()
        _streaming.value = false
    }

    // Attachments: a document the model reads for this conversation.
    private val _attachedName = MutableStateFlow<String?>(null)
    val attachedName: StateFlow<String?> = _attachedName.asStateFlow()

    /** Extracts text from [uri] on-device and attaches it to this conversation. */
    fun attach(context: android.content.Context, uri: android.net.Uri) {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            when (val r = com.kamsiob.kamai.files.FileExtractor.extract(context, uri)) {
                is com.kamsiob.kamai.files.FileExtractor.Result.Ok -> {
                    repository.setAttachment(convId, r.name, r.text)
                    _attachedName.value = r.name
                    _notice.value = "Attached ${r.name}. Ask about it."
                }
                is com.kamsiob.kamai.files.FileExtractor.Result.Error -> _notice.value = r.message
            }
        }
    }

    fun removeAttachment() {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            repository.clearAttachment(convId)
            _attachedName.value = null
        }
    }

    private suspend fun buildPrompt(conversationId: String): String {
        val conversation = repository.conversation(conversationId)
        val history = repository.messages(conversationId)

        // A Discover conversation is grounded in a saved passage: confine the
        // model to it instead of the plain mode prompt.
        val grounding = conversation?.groundingMomentId
        var system = if (!grounding.isNullOrBlank()) {
            SystemPrompts.grounded(grounding)
        } else {
            SystemPrompts.forMode(_mode.value)
        }

        // Precedence (see DECISIONS.md): the app's mode and hard rules above win
        // and can never be overridden; then the user's system-wide instructions;
        // then this project's instructions; then memory.
        val userInstructions = repository.userInstructions()
        if (userInstructions.isNotBlank()) {
            system = SystemPrompts.withUserInstructions(system, userInstructions)
        }

        conversation?.projectId?.let { projectId ->
            repository.project(projectId)?.let {
                system = SystemPrompts.withProject(system, it.instructions)
            }
        }

        // Only the memories relevant to this message, within a small slice of the
        // window, so memory never crowds out the instructions or the conversation.
        // Placed here, near the front of the system block, where models attend
        // well. Retrieval is keyword-and-recency for now; see DECISIONS.md.
        val contextSize = engine.contextSize.takeIf { it > 0 } ?: DEFAULT_CONTEXT
        val lastUser = history.lastOrNull { it.role == Role.USER }?.content.orEmpty()
        val memBudgetChars = (contextSize * MEMORY_CTX_FRACTION * CHARS_PER_TOKEN).toInt()
        val memories = repository.relevantMemory(lastUser, memBudgetChars, MEMORY_LIMIT)
        system = SystemPrompts.withMemory(system, memories)

        // Inject the real current date, which the model otherwise gets wrong.
        val now = java.util.Date()
        val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault())
        system = SystemPrompts.withDate(system, fmt.format(now))

        // A document the user attached, given to the model as context. It gets
        // most of the window, leaving room for the question and the reply, and is
        // truncated with a plain note (never silently) when it is longer than fits.
        val attachText = repository.attachmentText(conversationId)
        if (!attachText.isNullOrBlank()) {
            val attachName = repository.attachmentName(conversationId) ?: "the file"
            val ctx = engine.contextSize.takeIf { it > 0 } ?: DEFAULT_CONTEXT
            val maxChars = ((ctx - RESERVED_FOR_REPLY - 256) * 3.2).toInt().coerceAtLeast(1000)
            system = SystemPrompts.withAttachment(system, attachName, attachText, maxChars)
            if (attachText.length > maxChars && conversationId != attachWarnedFor) {
                attachWarnedFor = conversationId
                _notice.value = "That document is long, so only the first part fits in the " +
                    "model's memory. Ask about a specific section, or paste that part in."
            }
        }

        // Leave room for the reply itself, not just the prompt.
        val budget = contextSize - engine.countTokens(system) - RESERVED_FOR_REPLY

        // SYSTEM entries are display-only mode markers; never send them as turns.
        val turns = history
            .filter { it.role != Role.SYSTEM }
            .map { PromptBuilder.Turn(it.role, it.content) }
        val fitted = PromptBuilder.fitToBudget(turns, budget.coerceAtLeast(256)) {
            PromptBuilder.roughTokenCount(it)
        }

        // Context overflow: warn, never silently drop. When the oldest turns no
        // longer fit, say so plainly, once per conversation, so the user knows the
        // model can no longer see the start of the thread rather than wondering
        // why it forgot. Only a genuine budget drop counts: a Discover chat's
        // opening greeting is trimmed for structure on the very first question,
        // and warning about that would be both false and alarming.
        if (fitted.droppedForBudget > 0 && conversationId != trimWarnedFor) {
            trimWarnedFor = conversationId
            _notice.value = "This conversation is long enough that the earliest messages " +
                "no longer fit in the model's memory. It can still see the recent part. " +
                "Start a new chat for a clean slate."
        }

        return PromptBuilder.build(chatFormat(), system, fitted.turns)
    }

    /** The conversation we have already warned about context trimming for, so the
     *  notice shows once rather than on every send. */
    private var trimWarnedFor: String? = null
    private var attachWarnedFor: String? = null

    /** The layout the loaded model wants. Falls back to Gemma, the default tier. */
    private suspend fun chatFormat(): ChatFormat =
        repository.activeModel()?.format ?: ChatFormat.GEMMA

    private fun respond(conversationId: String) {
        // A background title pass must never share the engine with a live reply.
        titlingJob?.cancel()
        generation?.cancel()
        _streaming.value = true

        generation = viewModelScope.launch {
            // Lazy load on first use, through the manager. It enforces the memory
            // check and the one-resident rule; we only proceed once a model is
            // actually resident, and surface anything else plainly.
            when (val status = modelManager.ensureLoaded()) {
                is ModelManager.Status.Loaded -> Unit
                is ModelManager.Status.NoModel -> {
                    _notice.value = "No model is set up yet. Download one in Settings to start."
                    _streaming.value = false
                    return@launch
                }
                is ModelManager.Status.Refused -> {
                    _notice.value = status.reason
                    _streaming.value = false
                    return@launch
                }
                is ModelManager.Status.Failed -> {
                    _notice.value = status.reason
                    _streaming.value = false
                    return@launch
                }
                else -> {
                    _notice.value = "The model is not ready yet. Try again in a moment."
                    _streaming.value = false
                    return@launch
                }
            }

            val prompt = buildPrompt(conversationId)
            val messageId = repository.addMessage(
                conversationId, Role.ASSISTANT, "", incomplete = true,
            )

            val builder = StringBuilder()
            var stopReason: InferenceEngine.StopReason = InferenceEngine.StopReason.Finished

            try {
                engine.generate(prompt, _mode.value, onStop = { stopReason = it })
                    .collect { chunk ->
                        builder.append(chunk.text)
                        repository.updateMessage(
                            messageId, PromptBuilder.cleanOutput(builder.toString()), true,
                        )
                    }
            } finally {
                val finalText = PromptBuilder.cleanOutput(builder.toString())
                val reason = when (val r = stopReason) {
                    is InferenceEngine.StopReason.Overheating -> r.message
                    is InferenceEngine.StopReason.OutOfRoom -> r.message
                    is InferenceEngine.StopReason.Failed -> r.message
                    InferenceEngine.StopReason.UserStopped -> "You stopped this one."
                    InferenceEngine.StopReason.Finished -> null
                }

                if (finalText.isEmpty() && reason != null) {
                    // Nothing was produced, so an empty bubble would be worse
                    // than no bubble. Say what happened instead.
                    repository.deleteMessage(messageId)
                    _notice.value = reason
                } else {
                    repository.updateMessage(messageId, finalText, incomplete = false)
                    repository.finishMessage(messageId, reason)
                }

                _streaming.value = false
                maybeTitle(conversationId)
                maybeAutoRemember(conversationId)
            }
        }
    }

    /**
     * The user asked to remember something. Fires in Manual and Auto, never Off.
     * A plain confirmation is surfaced through the notice line.
     */
    private suspend fun maybeManualRemember(conversationId: String, userText: String) {
        if (repository.memoryMode() == MemoryMode.OFF) return
        val fact = MemoryExtractor.manualFact(userText) ?: return
        repository.remember(fact, conversationId, auto = false)
        _notice.value = "Saved to memory: ${fact.take(60)}"
    }

    /**
     * In Auto mode, surface durable facts worth keeping. Runs as a separate,
     * bounded pass on a batch of recent turns, and only every few user messages
     * rather than after every single one, so it stays cheap and does not drain
     * the battery. The extractor is told what is already stored so it does not
     * re-suggest known facts. Does nothing in Manual or Off.
     */
    private suspend fun maybeAutoRemember(conversationId: String) {
        if (repository.memoryMode() != MemoryMode.AUTO) return
        val history = repository.messages(conversationId)
        val userTurns = history.count { it.role == Role.USER }
        // Batch: only run on every Nth user message, never after every exchange.
        if (userTurns == 0 || userTurns % AUTO_MEMORY_EVERY != 0) return

        val recent = history.filter { it.role == Role.USER || it.role == Role.ASSISTANT }.takeLast(6)
        if (recent.none { it.role == Role.USER }) return
        val transcript = recent.joinToString("\n") {
            val who = if (it.role == Role.USER) "User" else "You"
            "$who: ${it.content.take(400)}"
        }

        // Give the model what it already knows, so it can skip duplicates.
        val known = repository.allMemoryTexts().take(40)
        val knownBlock = if (known.isEmpty()) "" else
            "\n\nAlready remembered (do not repeat these):\n" + known.joinToString("\n") { "- $it" }

        val prompt = PromptBuilder.oneShot(
            chatFormat(), MemoryExtractor.AUTO_INSTRUCTION, transcript + knownBlock,
        )

        val builder = StringBuilder()
        engine.generate(prompt, Mode.BENCH, maxTokens = AUTO_MEMORY_MAX_TOKENS).collect {
            builder.append(it.text)
        }
        val facts = MemoryExtractor.parseAutoReply(PromptBuilder.cleanOutput(builder.toString()))
        facts.forEach { repository.remember(it, conversationId, auto = true) }
    }

    /**
     * Titles the conversation after the first exchange, as its own one-shot
     * request. Asking the chat model to title itself mid-conversation confuses
     * small models into answering the instruction instead.
     */
    private suspend fun maybeTitle(conversationId: String) {
        // Titling is shared across every entry point so it behaves identically
        // wherever a conversation was created. See ConversationTitler.
        ConversationTitler.titleIfNeeded(repository, engine, conversationId)
    }

    companion object {
        fun factory(
            repository: KamRepository,
            engine: InferenceEngine,
            modelManager: ModelManager,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, engine, modelManager) }
        }

        const val MEMORY_LIMIT = 12
        const val RESERVED_FOR_REPLY = 768
        const val DEFAULT_CONTEXT = 4096
        const val AUTO_MEMORY_MAX_TOKENS = 60

        /** Memory gets at most this fraction of the context, so it never crowds
         *  out the instructions or the conversation. */
        const val MEMORY_CTX_FRACTION = 0.10
        const val CHARS_PER_TOKEN = 3.6
        /** Auto-extraction runs on a batch of turns, not every message, to keep
         *  it cheap: only when the user has spoken this many times. */
        const val AUTO_MEMORY_EVERY = 3
    }
}
