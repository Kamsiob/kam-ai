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
import com.kamsiob.kamai.llm.ContinuationJoin
import com.kamsiob.kamai.llm.ConversationTitler
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.ModelManager
import com.kamsiob.kamai.llm.MemoryExtractor
import com.kamsiob.kamai.llm.MemoryMode
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import com.kamsiob.kamai.llm.WrapUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _mode = MutableStateFlow(Mode.GENERAL)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    /** Set when generation stopped for a reason worth saying out loud. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * An unsent draft, kept so leaving the conversation and coming back does not
     * throw away what the user was part way through writing (#35, "always
     * recover input").
     *
     * A sent message is never at risk: it is written to the database before the
     * model is even asked, so a turn that fails to start still leaves it in the
     * transcript. What was genuinely lost was the half-typed message somebody
     * navigated away from.
     *
     * Held in the view model, which is keyed by conversation id, so each
     * conversation keeps its own draft. **Not persisted across process death**,
     * which would mean either a write per keystroke or a drafts table; neither is
     * worth it for the common case, which is a glance at another screen.
     */
    var draft: String = ""

    fun rememberDraft(text: String) {
        draft = text
    }

    private var generation: Job? = null

    /**
     * Where this conversation was scrolled to, so reopening it returns the user
     * where they were rather than to the top (#35).
     *
     * Held here rather than in the composable because a `rememberLazyListState`
     * dies when the screen leaves the stack, which is precisely the moment this
     * needs to survive. The view model is keyed by conversation id, so each
     * conversation keeps its own position and a different one cannot inherit it.
     *
     * Plain vars, not state: nothing recomposes when the user scrolls, and making
     * these observable would recompose the whole message list on every frame of
     * every scroll.
     */
    var scrollIndex: Int = 0
        private set
    var scrollOffset: Int = 0
        private set

    /**
     * Whether this conversation has a position worth restoring at all.
     *
     * Needed because index 0 offset 0 is both "the user was reading the very top"
     * and "nothing has been recorded", and those want opposite behaviour: the
     * first should be restored, the second should open at the newest message like
     * it always has.
     */
    var hasSavedScroll: Boolean = false
        private set

    fun rememberScroll(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
        hasSavedScroll = true
    }

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

    /**
     * Seconds captured so far, while recording.
     *
     * `AudioRecorder.seconds` existed, documented as being "for a live duration
     * read-out", and nothing read it. Talking into a phone that says only
     * "Listening" tells you nothing about whether it is still going or how much
     * you have given it, which matters most for exactly the long brain dump the
     * flagship voice flow is built around (#39).
     */
    private val _recordedSeconds = MutableStateFlow(0)
    val recordedSeconds: StateFlow<Int> = _recordedSeconds.asStateFlow()

    fun startRecording() {
        if (_recording.value || _transcribing.value) return
        if (recorder.start(viewModelScope)) {
            _recording.value = true
            _recordedSeconds.value = 0
            viewModelScope.launch {
                // Polls rather than pushes: the recorder counts samples on its own
                // thread and this only needs to be right to the second.
                while (_recording.value) {
                    _recordedSeconds.value = recorder.seconds.toInt()
                    kotlinx.coroutines.delay(250)
                }
            }
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
                // Nothing to say. The user stopped it on purpose and watched it
                // stop; a notice explaining that would be the app talking to
                // itself.
                com.kamsiob.kamai.voice.SttEngine.Result.Cancelled -> Unit
            }
            _transcribing.value = false
        }
    }

    /**
     * Abandons a transcription that is already running.
     *
     * Transcription was the one slow operation in the app with no way out. It
     * said "Turning your voice into text..." and then held the composer until
     * whisper finished, however long the recording was (item 5). The engine
     * aborts inside its computation, so this really stops rather than discarding
     * the result afterwards.
     */
    fun cancelTranscription(stt: com.kamsiob.kamai.voice.SttEngine) {
        if (_transcribing.value) stt.cancel()
    }

    /**
     * Abandons an in-flight recording, for example when the screen goes away.
     *
     * Says so, if there was anything worth losing. A Brainstorm brain dump asks
     * the user to talk continuously for a set time, so the recording that gets
     * thrown away here can be minutes of someone thinking out loud, and it used
     * to vanish without a word: leave the screen, take a call, come back to a
     * composer that looks exactly as it did before you started (#39).
     *
     * Two seconds, because an accidental tap on the microphone is not worth a
     * notice and a sentence of speech is.
     */
    fun cancelRecording() {
        if (!_recording.value) return
        val lost = recorder.seconds
        recorder.cancel()
        _recording.value = false
        if (lost >= 2f) {
            _notice.value = "Recording stopped when you left, and was not saved."
        }
    }

    /**
     * The screen is going away mid-recording, so keep what was said (#65).
     *
     * [cancelRecording] above tells the user their minutes of talking are gone,
     * which is honest and no help at all. When a transcription model is present
     * there is no reason to throw the audio away: transcribe it and leave the
     * words in the draft, where the composer will show them on return.
     *
     * The work runs in `viewModelScope`, which outlives the composable, so it
     * finishes after the screen has gone. The view model is keyed by
     * conversation, so the words reappear in the conversation they were spoken
     * into and nowhere else.
     *
     * Under two seconds is still discarded, for the same reason [cancelRecording]
     * stays quiet below that: it is a brush against the microphone rather than a
     * thought, and transcribing it would drop a cough into someone's composer.
     */
    fun stopAndKeepDraft(
        stt: com.kamsiob.kamai.voice.SttEngine,
        modelFile: java.io.File,
    ) {
        if (!_recording.value) return
        val spoken = recorder.seconds
        _recording.value = false
        val pcm = recorder.stop()
        if (spoken < 2f) return
        _transcribing.value = true
        viewModelScope.launch {
            when (val r = stt.transcribe(modelFile, pcm)) {
                is com.kamsiob.kamai.voice.SttEngine.Result.Ok -> {
                    val joined = appendToDraft(draft, r.text)
                    if (joined != draft) {
                        draft = joined
                        _notice.value =
                            "Recording stopped when you left. What you said is in the message box."
                    }
                }
                // The user has already left. A notice about a failure they cannot
                // see and did not ask for would only surface later, out of
                // context, attached to whatever they are doing then.
                is com.kamsiob.kamai.voice.SttEngine.Result.Error -> Unit
                com.kamsiob.kamai.voice.SttEngine.Result.Cancelled -> Unit
            }
            _transcribing.value = false
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

    /** The project this conversation belongs to, live, for the header's picker. */
    val projectId: StateFlow<String?> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else repository.observeConversation(id).map { it?.projectId }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The Workbench session this chat was started from, if any (#32). The link is
     * stored on both rows, so this is a plain read rather than a search.
     */
    val linkedSessionId: StateFlow<String?> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else repository.observeConversation(id).map { it?.linkedConversationId }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether this is a Discover discussion confined to a passage. Drives the
     *  scope banner and its one-tap escape into an open chat (item 21). */
    val grounded: StateFlow<Boolean> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(false)
                else repository.observeConversation(id).map { !it?.groundingMomentId.isNullOrBlank() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
            // Fills in a missing title only. Opening a conversation must never
            // re-title it: the length has not changed, so the refresh milestone
            // would fire again on every open, rewriting a title the user was
            // reading and costing a re-prefill each time (#38).
            ConversationTitler.titleIfNeeded(
                repository, engine, conversationId, allowRefresh = false,
            )
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

    /**
     * Explains the mode, once ever, at the top of the first conversation started
     * in it.
     *
     * A mid-conversation switch says what the new mode does. Starting a chat in
     * a mode said nothing at all, because the switch note is only written when
     * there is already something to mark, so a first Brainstorm conversation
     * opened straight from the Chats control just began asking questions with no
     * word about why (#28).
     *
     * Only at the top of a conversation, so it can never appear in the middle of
     * one, and not for General, which is the resting position and explains itself
     * by being ordinary.
     */
    private suspend fun explainModeIfFirstTime(conversationId: String) {
        val mode = _mode.value
        val explain = ModeExplainer.shouldExplain(
            mode = mode,
            historyIsEmpty = repository.messages(conversationId).isEmpty(),
            alreadyExplained = repository.wasModeExplained(mode),
        )
        if (!explain) return
        repository.addMessage(conversationId, Role.SYSTEM, SystemPrompts.modeSwitchNotice(mode))
        repository.markModeExplained(mode)
    }

    /**
     * Marks in the transcript that a linked Workbench was opened from here.
     *
     * Choosing Workbench from the mode picker does not change this
     * conversation's mode: it opens a separate, linked session. So this
     * deliberately does not go through [setMode], which would swap this
     * conversation's mode out from under it. Until now nothing at all was
     * recorded, so a chat that had spawned a Workbench looked identical to one
     * that had not, and the only route back was an overflow menu item you had
     * to already know about. `SystemPrompts.modeSwitchNotice(Mode.BENCH)` was
     * written for exactly this moment and had never once been shown.
     */
    fun noteWorkbenchOpened() {
        val convId = _conversationId.value ?: return
        viewModelScope.launch {
            val history = repository.messages(convId)
            if (WorkbenchNote.shouldMark(history)) {
                repository.addMessage(convId, Role.SYSTEM, WorkbenchNote.text)
            }
        }
    }

    /**
     * Lifts a grounded Discover discussion into a normal open chat, keeping the
     * history. The scope boundary is stated up front and this is its one-tap
     * escape, so an out-of-scope question does not dead-end (item 21). A quiet
     * SYSTEM note marks where the scope changed.
     */
    fun continueInOpenChat() {
        val convId = _conversationId.value ?: return
        if (!grounded.value) return
        // Lifting the scope also moves the conversation to open Chat: with no
        // passage left, its Discover mode would otherwise resolve to a grounded
        // prompt pointing at nothing. Set the mode directly (not setMode) so only
        // the one continue-open note is added, not a second mode-switch note.
        _mode.value = Mode.GENERAL
        viewModelScope.launch {
            repository.clearGrounding(convId)
            repository.setConversationMode(convId, Mode.GENERAL)
            repository.addMessage(convId, Role.SYSTEM, SystemPrompts.CONTINUE_OPEN_NOTICE)
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
            // Anything attached before this conversation existed goes in now,
            // before the prompt is built, so the first question can be about it.
            pendingAttachment?.let { (name, text) ->
                repository.setAttachment(id, name, text)
                pendingAttachment = null
            }
            explainModeIfFirstTime(id)
            repository.addMessage(id, Role.USER, trimmed)
            maybeManualRemember(id, trimmed)
            respond(id)
        }
    }

    /**
     * Picks up an answer that stopped early, appending to it rather than
     * starting a second one (#35).
     *
     * The instruction goes in the prompt and is never written to the transcript,
     * so the conversation does not gain a message the user did not send. The
     * partial answer is already in the history, so the model can see exactly what
     * it was part way through.
     */
    fun continueLast() {
        if (_streaming.value) return
        val id = _conversationId.value ?: return
        viewModelScope.launch {
            val last = repository.messages(id).lastOrNull { it.role == Role.ASSISTANT }
            if (last == null || last.stoppedReason == null) return@launch
            repository.finishMessage(last.id, null)
            respond(
                conversationId = id,
                continueFrom = last,
                continuePrompt = "Carry straight on from where your previous answer stopped. " +
                    "Do not repeat what you already wrote and do not start again.",
            )
        }
    }

    /**
     * Closes a Brainstorm session and asks for the summary (#58).
     *
     * The instruction goes in as the final user turn rather than being typed,
     * because the same words at the top of a long prompt lose: several exchanges
     * deep the model recited the convergence procedure back and then asked
     * another question. See [WrapUp].
     *
     * A quiet note goes in the transcript so the history shows the session was
     * closed deliberately, rather than an answer arriving from nowhere.
     */
    fun wrapUp() {
        if (_streaming.value) return
        val id = _conversationId.value ?: return
        _streaming.value = true
        viewModelScope.launch {
            repository.addMessage(id, Role.SYSTEM, WrapUp.NOTE)
            respond(conversationId = id, continuePrompt = WrapUp.INSTRUCTION)
        }
    }

    /** Throws away an answer that stopped early, leaving the question in place. */
    fun discardLast() {
        if (_streaming.value) return
        val id = _conversationId.value ?: return
        viewModelScope.launch {
            val last = repository.messages(id).lastOrNull { it.role == Role.ASSISTANT }
            if (last == null || last.stoppedReason == null) return@launch
            repository.deleteMessage(last.id)
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

    /**
     * Stops the answer in progress and keeps what was written.
     *
     * Deliberately does not cancel the job. Cancelling it was issue #40: the
     * `finally` in [respond] that records the stop reason and finishes the
     * message then ran inside an already-cancelled coroutine and threw at its
     * first suspension point, so the message stayed incomplete with no reason,
     * lost its whole action row, and was relabelled "Kam AI was closed while
     * this was being written." on the next launch, which was not true.
     *
     * The engine's abort flag ends the decode within a token, and it now reports
     * the stop as the user's rather than leaving it to be inferred. `_streaming`
     * is left for that same `finally` to clear, so the stop button disappears
     * when the answer has actually stopped rather than when it was asked to.
     */
    fun stop() {
        engine.requestStop()
    }

    // Attachments: a document the model reads for this conversation.
    private val _attachedName = MutableStateFlow<String?>(null)
    val attachedName: StateFlow<String?> = _attachedName.asStateFlow()

    /** Extracts text from [uri] on-device and attaches it to this conversation. */
    /**
     * A file attached before this conversation exists in the database.
     *
     * A new chat is not written until the first message is sent, so that backing
     * out of one leaves no empty row. Attaching used to give up on that: it read
     * the conversation id, found null, and returned. The user picked a document
     * out of the file picker and the app discarded it without a word, no chip,
     * no notice, no error (#39).
     *
     * Held here instead and written when the conversation is created. Attaching
     * a file is not, on its own, a reason to leave an empty conversation behind.
     */
    private var pendingAttachment: Pair<String, String>? = null

    /**
     * How many remembered facts the last built prompt carried (#16).
     *
     * Written by [buildPrompt] and read immediately after, when the answer's row
     * is created. A plain var because the two happen in the same coroutine, one
     * after the other, with nothing in between.
     */
    private var lastMemoriesUsed: Int = 0

    fun attach(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            when (val r = com.kamsiob.kamai.files.FileExtractor.extract(context, uri)) {
                is com.kamsiob.kamai.files.FileExtractor.Result.Ok -> {
                    val convId = _conversationId.value
                    if (convId != null) {
                        repository.setAttachment(convId, r.name, r.text)
                    } else {
                        pendingAttachment = r.name to r.text
                    }
                    _attachedName.value = r.name
                    _notice.value = "Attached ${r.name}. Ask about it."
                }
                is com.kamsiob.kamai.files.FileExtractor.Result.Error -> _notice.value = r.message
            }
        }
    }

    fun removeAttachment() {
        // Works before the conversation exists too, so a file attached to a new
        // chat can be taken off again without sending anything first.
        pendingAttachment = null
        _attachedName.value = null
        val convId = _conversationId.value ?: return
        viewModelScope.launch { repository.clearAttachment(convId) }
    }

    private suspend fun buildPrompt(
        conversationId: String,
        /** An instruction included in the prompt but never written to the
         *  transcript, used by [continueLast]. */
        pending: String? = null,
    ): String {
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
                system = SystemPrompts.withProject(system, it.instructions, it.notes)
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
        // Recorded on the answer that is about to be written, so the transcript
        // can say that memory was involved (#16). Set here rather than returned
        // because buildPrompt has one caller and four other things to say.
        lastMemoriesUsed = memories.size

        // Inject the real current date, which the model otherwise gets wrong. Day
        // granularity, not the time: a minute-precise stamp would change every
        // turn and, sitting before the history, would break the KV-cache prefix
        // reuse that keeps a long conversation fast (issue #38).
        val now = java.util.Date()
        val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", java.util.Locale.getDefault())
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

        return PromptBuilder.build(chatFormat(), system, fitted.turns, pending)
    }

    /** The conversation we have already warned about context trimming for, so the
     *  notice shows once rather than on every send. */
    private var trimWarnedFor: String? = null
    private var attachWarnedFor: String? = null

    /** The layout the loaded model wants. Falls back to Gemma, the default tier. */
    private suspend fun chatFormat(): ChatFormat =
        repository.activeModel()?.format ?: ChatFormat.GEMMA

    private fun respond(
        conversationId: String,
        continueFrom: MessageEntity? = null,
        continuePrompt: String? = null,
    ) {
        // A background title pass must never share the engine with a live reply.
        titlingJob?.cancel()
        val previous = generation
        _streaming.value = true

        generation = viewModelScope.launch {
            // Wait for any previous answer to finish tearing down before starting
            // another. Its `finally` is NonCancellable and does real work:
            // finishing the message, clearing `_streaming`, titling, remembering.
            // Letting the two overlap would allow the old teardown to clear the
            // streaming flag for this new answer and to put a second pass on the
            // engine at the same time. Cancelling the collector also trips
            // `awaitClose`, which aborts the native decode, so this does not wait
            // for a long answer to finish on its own.
            //
            // Before #40 the old teardown threw at its first suspension point and
            // never got far enough to cause this, so the bug was hiding the race.
            previous?.cancelAndJoin()
            _streaming.value = true
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

            val prompt = buildPrompt(conversationId, pending = continuePrompt)
            // Continuing appends to the answer that stopped rather than adding a
            // second bubble, so the result reads as the one answer it is.
            val messageId = continueFrom?.id ?: repository.addMessage(
                conversationId, Role.ASSISTANT, "", incomplete = true,
                memoriesUsed = lastMemoriesUsed,
            )

            val builder = StringBuilder(continueFrom?.content.orEmpty())
            // The stored text was trimmed on its way to the database, so an answer
            // that stopped between words lost the space that said so. Put it back
            // when the continuation arrives, rather than reading "to theoutside".
            var needsJoinSpace = continueFrom != null && builder.isNotEmpty()
            var stopReason: InferenceEngine.StopReason = InferenceEngine.StopReason.Finished

            try {
                engine.generate(prompt, _mode.value, onStop = { stopReason = it })
                    .collect { chunk ->
                        if (needsJoinSpace) {
                            // The first chunk after a continuation is the one
                            // that has to be joined on properly: spaced if it
                            // needs it, and with any restart of the last word
                            // dropped rather than printed twice.
                            val joined = ContinuationJoin.join(builder.toString(), chunk.text)
                            builder.setLength(0)
                            builder.append(joined)
                            needsJoinSpace = false
                        } else {
                            builder.append(chunk.text)
                        }
                        repository.updateMessage(
                            messageId, PromptBuilder.cleanOutput(builder.toString()), true,
                        )
                    }
            } finally {
                // NonCancellable because this block is what makes a stopped or
                // abandoned answer honest, and every call in it suspends. If the
                // scope is cancelled, by the screen going away mid-answer, an
                // unwrapped block throws at its first suspension point and the
                // message is left incomplete with no reason for ever. See #40.
                withContext(NonCancellable) {
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

/**
 * Joins speech kept from an interrupted recording onto whatever was already
 * typed (#65).
 *
 * Separate from the view model so the joining rules can be tested without a
 * database, a microphone, or a transcription model. Returns [draft] unchanged
 * when there is nothing to add, which is how the caller knows whether to say
 * anything.
 */
internal fun appendToDraft(draft: String, heard: String): String {
    val text = heard.trim()
    if (text.isEmpty()) return draft
    if (draft.isBlank()) return text
    return "${draft.trimEnd()} $text"
}
