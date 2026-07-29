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
import com.kamsiob.kamai.llm.ConversationState
import com.kamsiob.kamai.llm.ConversationTitler
import com.kamsiob.kamai.llm.GenerationService
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.MethodAnnouncement
import com.kamsiob.kamai.llm.ModelManager
import com.kamsiob.kamai.llm.MemoryExtractor
import com.kamsiob.kamai.llm.MemoryMode
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.PromptEcho
import com.kamsiob.kamai.llm.RestatementRetry
import com.kamsiob.kamai.llm.Summarizer
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
     * and "nothing has been recorded", and those want opposite behavior: the
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
        // It is canceled the moment a real turn starts, so it never runs on the
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
        // The system prompt is the very front of the cached prefix, so a mode
        // switch invalidates all of it (#52).
        invalidateState()
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
     * Says once, in the transcript, that the installed model is weak at this mode.
     *
     * Written as a note in the conversation rather than as a banner because that
     * is how this application already says a thing once: it sits where the user
     * is looking, it scrolls away, and it cannot come back. A badge on the mode
     * chip or a line above every reply would be nagging, which is banned.
     */
    private suspend fun noteWeakMode(conversationId: String, mode: Mode) {
        val model = repository.activeModel() ?: return
        val seen = repository.wasWeakModeNoticed(mode, model.id)
        if (!WeakModeNote.shouldShow(mode, model, seen)) return
        repository.addMessage(conversationId, Role.SYSTEM, WeakModeNote.text(mode, model))
        repository.markWeakModeNoticed(mode, model.id)
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
            // After the mode explanation, so somebody meeting a mode for the
            // first time reads what it does before what it is weak at here.
            noteWeakMode(id, _mode.value)
            repository.addMessage(id, Role.USER, trimmed)
            maybeManualRemember(id, trimmed)
            // Typing "let's wrap up" reaches the same place as the Wrap-up
            // control, rather than the ordinary path where the instruction loses
            // against a long history and comes back as another question (#58).
            val wrapping = _mode.value == Mode.BRAINSTORM && WrapUp.isRequest(trimmed)
            respond(id, continuePrompt = if (wrapping) WrapUp.INSTRUCTION else null)
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

        // Editing rewrites the history at a position the cache already holds.
        invalidateState()
        viewModelScope.launch {
            repository.truncateAfter(id, message)
            repository.updateMessage(message.id, trimmed, incomplete = false)
            respond(id)
        }
    }

    /**
     * Stops the answer in progress and keeps what was written.
     *
     * Deliberately does not cancel the job. Canceling it was issue #40: the
     * `finally` in [respond] that records the stop reason and finishes the
     * message then ran inside an already-canceled coroutine and threw at its
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

    /**
     * How many of the oldest turns the last built prompt had to drop to fit (#90).
     *
     * Read straight after [buildPrompt], like [lastMemoriesUsed]. A summary of a
     * conversation longer than the window covers only what fitted, and the sheet
     * says which portion rather than presenting a partial reading as a whole one.
     */
    private var lastPromptDroppedTurns: Int = 0

    /**
     * The conversation whose saved cache has already been restored in this
     * process (#52).
     *
     * Restoring is once per open, not once per turn: after the first turn the
     * live context already holds the history, and reading a large file back over
     * it would be slower than the prefill it replaces.
     */
    private var restoredFor: String? = null

    /**
     * Puts this conversation's saved KV cache back, once per open (#52).
     *
     * Extracted because [summarize] needs it too and did not have it, which is
     * why the first attempt at #90 measured no better on a cold start: it built
     * the conversation's prompt, correctly expecting the cache to match, against a
     * context that was still empty. 1490 tokens of prefill and forty-six seconds,
     * for a cache that was sitting on disk unread.
     *
     * Failure is not handled because there is nothing to handle: no file, a file
     * for another model, or a rejected blob all mean the prompt is prefilled as it
     * would have been anyway.
     */
    private suspend fun restorePersistedCache(conversationId: String) {
        if (restoredFor == conversationId) return
        restoredFor = conversationId
        modelManager.activeId()?.let { modelId ->
            ConversationState.restore(repository.appContext, engine, conversationId, modelId)
        }
    }

    /**
     * Summarizing this conversation, on request only (#86).
     *
     * Never automatic and never on opening: it costs time and battery, and the
     * user is the only one who knows whether this conversation is worth either.
     */
    sealed interface SummaryState {
        data object Idle : SummaryState
        data class Working(val step: String) : SummaryState
        data class Ready(
            val text: String,
            val provenance: String,
            /** True while tokens are still arriving, so the sheet can say so. */
            val streaming: Boolean = false,
        ) : SummaryState
        data class NotWorthIt(val message: String) : SummaryState
        data class Failed(val message: String) : SummaryState
    }

    private val _summary = MutableStateFlow<SummaryState>(SummaryState.Idle)
    val summary: StateFlow<SummaryState> = _summary.asStateFlow()

    private var summaryJob: Job? = null

    /**
     * Runs a summary. Cancellable, per the rule that anything slow must be.
     *
     * **Built from the conversation's own prompt, not a fresh one.** The first
     * version sent a minimal instruction plus the transcript through
     * `PromptBuilder.oneShot`, which looks like the efficient thing and is the
     * opposite: that prompt shares almost no prefix with what the context already
     * holds, so the whole conversation was re-prefilled from scratch. Measured on
     * a twelve hundred word conversation, roughly sixteen hundred tokens at about
     * 33 tok/s is fifty seconds before a single token of summary appears, and four
     * hundred output tokens at about 5 tok/s is another eighty. That is the
     * reported two minutes, and it is arithmetic rather than mystery.
     *
     * Passing the instruction as `pending` to [buildPrompt] produces the
     * conversation's prompt with the instruction as the final turn, so the cached
     * prefix matches and only the instruction is new. The system prompt and mode
     * rules ride along, which is not waste: they are already in the cache, and
     * paying a few hundred cached tokens to avoid re-reading sixteen hundred
     * uncached ones is the whole trade.
     */
    fun summarize() {
        if (_streaming.value) return
        val id = _conversationId.value ?: return
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            _summary.value = SummaryState.Working("Reading the conversation")
            val history = repository.messages(id)
                .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
                .map { it.content }

            // Only two outcomes matter now that the conversation's own cache is
            // reused: it is too short to bother with, or it is summarized in one
            // pass. Sectioning is gone from this path because the conversation by
            // definition fits the context it is being held in; where the history
            // is longer than the budget, buildPrompt trims the oldest turns and
            // the sheet says which portion was covered rather than pretending.
            val plan = Summarizer.plan(history, budgetChars = Int.MAX_VALUE)
            if (plan is Summarizer.Plan.TooShort) {
                _summary.value = SummaryState.NotWorthIt(plan.message)
                return@launch
            }

            when (val status = modelManager.ensureLoaded()) {
                is ModelManager.Status.Loaded -> Unit
                else -> {
                    _summary.value = SummaryState.Failed(
                        "A model has to be ready before this can run. " +
                            (status as? ModelManager.Status.Refused)?.reason.orEmpty(),
                    )
                    return@launch
                }
            }

            runCatching {
                // Before the prompt is built, so the cached prefix it is counting
                // on actually exists (#90).
                restorePersistedCache(id)
                val prompt = buildPrompt(id, pending = Summarizer.WHOLE_INSTRUCTION)
                val covered = lastPromptDroppedTurns
                val provenance = if (covered > 0) {
                    "Kam AI's reading of the most recent part of this conversation. " +
                        "The earliest $covered turns did not fit and are not included."
                } else {
                    Summarizer.provenance(Summarizer.Plan.Whole(""))
                }

                // Streamed, because a summary that starts appearing after two
                // seconds feels fast even if it takes twenty, and one that shows
                // nothing for twenty feels broken even if it finishes sooner.
                val builder = StringBuilder()
                var first = true
                engine.preservingCache {
                engine.generate(prompt, Mode.BENCH, maxTokens = SUMMARY_MAX_TOKENS)
                    .collect { chunk ->
                        builder.append(chunk.text)
                        if (first) {
                            first = false
                            _summary.value = SummaryState.Ready(
                                PromptBuilder.cleanOutput(builder.toString()),
                                provenance,
                                streaming = true,
                            )
                        } else {
                            _summary.value = SummaryState.Ready(
                                PromptBuilder.cleanOutput(builder.toString()),
                                provenance,
                                streaming = true,
                            )
                        }
                    }
                }
                _summary.value = SummaryState.Ready(
                    PromptBuilder.cleanOutput(builder.toString()),
                    provenance,
                    streaming = false,
                )
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _summary.value = SummaryState.Failed(
                    "That did not finish. Nothing was changed, and you can try again.",
                )
            }
        }
    }

    private suspend fun runPass(instruction: String, body: String): String {
        val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
        val prompt = PromptBuilder.oneShot(format, instruction, body)
        val builder = StringBuilder()
        engine.preservingCache {
            engine.generate(prompt, Mode.BENCH, maxTokens = SUMMARY_MAX_TOKENS)
                .collect { builder.append(it.text) }
        }
        return PromptBuilder.cleanOutput(builder.toString())
    }

    /** Stops a summary in progress, leaving nothing behind. */
    fun cancelSummary() {
        summaryJob?.cancel()
        summaryJob = null
        engine.requestStop()
        _summary.value = SummaryState.Idle
    }

    /** Closes the sheet. The summary was never part of the transcript. */
    fun dismissSummary() {
        _summary.value = SummaryState.Idle
    }

    /**
     * Keeps a summary in Follow-ups, labeled as generated.
     *
     * Marked plainly rather than filed as something either party said, because a
     * summary read back in a week is exactly the thing that would otherwise be
     * mistaken for a quote.
     */
    fun saveSummary() {
        val state = _summary.value as? SummaryState.Ready ?: return
        val id = _conversationId.value
        viewModelScope.launch {
            repository.flag(
                snippet = "Summary (written by Kam AI)\n\n${state.text}",
                mode = _mode.value,
                conversationId = id,
                messageId = null,
            )
            _notice.value = "Saved to Follow-ups, marked as a generated summary."
            _summary.value = SummaryState.Idle
        }
    }

    /**
     * A conversation whose last message was typed before a model was ready, held
     * so it can be sent the moment one is (#78).
     *
     * One at a time, because it is always the newest message in this
     * conversation: a second attempt while still waiting simply replaces the
     * first, and the transcript already holds both.
     */
    private var pendingSend: String? = null

    /** Whether a model download is running, for the honest version of the wait. */
    private suspend fun downloadInFlight(): Boolean =
        modelManager.activeId() == null && repository.hasModelDownloadInFlight()

    /**
     * Whether this view model has already warmed the cache, so it happens once
     * rather than on every mode observation.
     */
    private var warmedFor: Mode? = null

    /**
     * Decodes the current mode's instruction block before the user sends anything
     * (#38).
     *
     * Cold time to first token was about thirty seconds, and measurement showed
     * prefill was 99.5 percent of it: 31438 ms of a 31588 ms wait, for roughly
     * 1100 tokens at 37 tokens per second. There was no defect to fix. Batching
     * was already correct, and that prefill to decode ratio is ordinary for this
     * CPU. The prompt is large and the phone is slow.
     *
     * So the wait is moved rather than removed. Nearly all of those tokens are the
     * mode's system prompt, which never varies and is known the moment the screen
     * opens, so it is decoded while the user is still reading. Their first message
     * then prefills only itself.
     *
     * The mode prompt alone, deliberately, without the user's own instructions,
     * project text or memory. Those vary per conversation, and warming a prefix
     * that turns out not to match would waste the time it was trying to save.
     * Prefix diffing keeps whatever is genuinely common.
     */
    private fun warmCacheFor(mode: Mode) {
        if (warmedFor == mode) return
        warmedFor = mode
        viewModelScope.launch {
            if (modelManager.activeId() == null) return@launch
            if (modelManager.ensureLoaded() !is ModelManager.Status.Loaded) return@launch
            // The templated opening, not the bare system text. Warming the raw
            // prompt was measured to buy nothing, because the real prompt starts
            // with the format's turn opener and the token streams diverged there.
            val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
            engine.warmUp(format.warmPrefix(SystemPrompts.forMode(mode)))
        }
    }

    init {
        // Warm on open, and again whenever the mode changes, since each mode has
        // its own instruction block and switching is the other moment a full
        // prefill would otherwise be paid for.
        viewModelScope.launch { _mode.collect { warmCacheFor(it) } }

        // Release a held message when a model becomes available. Collected for
        // the life of this view model, which is the life of the conversation the
        // message belongs to.
        viewModelScope.launch {
            modelManager.status.collect { status ->
                val waiting = pendingSend ?: return@collect
                if (status is ModelManager.Status.Loaded || status is ModelManager.Status.Idle) {
                    pendingSend = null
                    _notice.value = "The model is ready. Sending what you typed."
                    respond(waiting)
                }
            }
        }
    }

    /** Writes the context out for [conversationId] while it still holds it (#52). */
    private suspend fun saveConversationState(conversationId: String) {
        modelManager.activeId()?.let { modelId ->
            ConversationState.save(repository.appContext, engine, conversationId, modelId)
        }
    }

    /**
     * Throws the saved cache away, because the history it describes is no longer
     * the history that will be sent (#52).
     *
     * Editing an earlier message, switching mode, and changing instructions or
     * memory all change the prompt at or before a position the cache already
     * holds. Restoring it afterwards would decode against a prefix that no
     * longer exists, which is wrong output rather than a slow turn.
     */
    private fun invalidateState() {
        restoredFor = null
        ConversationState.clear(repository.appContext)
    }

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
        // Off is honoured inside relevantMemory rather than here, so no caller can
        // reintroduce the bug by forgetting to check (#123).
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
        // Recorded for the summary, which has to say which portion it covered
        // when the whole conversation did not fit (#90).
        lastPromptDroppedTurns = fitted.droppedForBudget

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
            // engine at the same time. Canceling the collector also trips
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
                    // The message stays in the transcript and is sent the moment
                    // a model is ready (#78). Holding it costs the user nothing
                    // and loses no intent; refusing would make them retype a
                    // thought they had already finished having.
                    //
                    // The alternative considered was explaining and refusing,
                    // with the time remaining. It is more precise and worse:
                    // somebody who has just typed a question does not want an
                    // estimate, they want the question asked.
                    pendingSend = conversationId
                    _notice.value = if (downloadInFlight()) {
                        "Still downloading the model. This will send as soon as it is ready."
                    } else {
                        "No model yet. Download one in Settings and this sends as soon " +
                            "as it is ready."
                    }
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

            // A conversation reopened in a new process starts with an empty
            // context and re-reads its whole history before the first new token
            // (#52). Restoring the saved cache once, on the first turn after
            // opening, makes that turn cost what an ongoing one costs.
            //
            // Failure is not handled because there is nothing to handle: no file,
            // a file for another model, a rejected blob, all mean the prompt is
            // built and prefilled exactly as it was before any of this existed.
            restorePersistedCache(conversationId)

            // Keeps the process alive while this answer is written, so switching
            // to another app does not stall the decode loop (#96). Started before
            // the first token rather than after, because the freeze can happen at
            // any point once the app is backgrounded.
            GenerationService.start(repository.appContext)

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

            // Guarded only for a fresh answer. A continuation is joined onto text
            // the user has already read, so discarding it would delete their reply
            // in front of them to fix a smaller problem.
            val guardEcho = continueFrom == null

            // What the user actually said, so an example answer landing on the
            // message it belongs to is left alone. Without this the guard threw
            // away a correct reply to "Remember that I always work in metric
            // units.", which is the one message that answer is right for.
            val lastUser = repository.messages(conversationId)
                .lastOrNull { it.role == Role.USER }?.content.orEmpty()

            // The instructions actually in force, so a reply that recites them is
            // caught without anyone having to have listed the line in advance.
            // Asked what model it ran on, the model answered with this text.
            // The boundary is part of what is sent, so it belongs in what the
            // guard compares against. Without it, the one sentence added to
            // separate the instructions from the message would be the one piece
            // of the prompt that could come back unnoticed.
            // Say once, when the phone has warmed enough to change how this
            // behaves, that answers will be slower. Until now nothing was said at
            // any thermal level below the one that stops generation outright, so
            // answers got slower and shorter with no explanation (#134).
            //
            // The watcher speaks only when the status rises above what it has
            // already announced, so this is once per episode rather than once per
            // turn. A warm phone stays warm for many turns and repeating it would
            // be nagging.
            engine.thermalNotice()?.let { _notice.value = it }

            val systemText = SystemPrompts.forMode(_mode.value) +
                com.kamsiob.kamai.llm.ChatFormat.SYSTEM_BOUNDARY

            suspend fun streamOnce(usePrompt: String = prompt) {
                engine.generate(usePrompt, _mode.value, onStop = { stopReason = it })
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
                        // Caught a few words in rather than at the end, so a copy
                        // is abandoned before it finishes streaming. Waiting for
                        // the whole answer would mean buffering every reply, which
                        // would give back the time to first token #38 won.
                        if (guardEcho &&
                            PromptEcho.couldBecomeBadReply(
                                builder.toString(), systemText, lastUser,
                            )
                        ) {
                            throw EchoDetected()
                        }
                        repository.updateMessage(
                            messageId, PromptBuilder.cleanOutput(builder.toString()), true,
                        )
                    }
            }

            try {
                // A reply that is really the prompt read back gets one more go.
                //
                // Sampling is seeded randomly, so a second attempt is genuinely a
                // different draw rather than the same tokens again. If that also
                // comes back a copy, the answer is written in code instead: the
                // worst instance of this was a message about a bereavement
                // answered with a line lifted from the instructions, and showing
                // that twice is not a risk worth carrying for the sake of letting
                // the model try a third time (#119).
                var retried = false
                while (true) {
                    try {
                        // The second attempt is told what to do rather than
                        // simply drawn again. On the input this defect is named
                        // for the model restates on most draws, so re-rolling the
                        // same prompt is a slow way to the same fallback, and
                        // that fallback asks somebody to rephrase a sentence that
                        // was perfectly clear.
                        //
                        // The nudge rides in the pending instruction, after the
                        // cached instruction block, so it costs nothing in prefix
                        // reuse.
                        val attempt = if (!retried) prompt else {
                            RestatementRetry.instruction(lastUser, _mode.value)
                                ?.let { buildPrompt(conversationId, pending = it) }
                                ?: prompt
                        }
                        streamOnce(attempt)
                        // Deliberately not gated on `retried`. It used to be, and
                        // that meant a second bad attempt was never checked and
                        // was shown as-is: the retry existed but its result was
                        // accepted unconditionally. The catch below is what knows
                        // the difference, falling back once one retry is spent.
                        if (guardEcho &&
                            PromptEcho.isBadReply(builder.toString(), systemText, lastUser)
                        ) {
                            throw EchoDetected()
                        }
                        // Brainstorm reading its method selection out loud is a
                        // formatting failure, so it is repaired rather than
                        // regenerated. The question after the announcement was
                        // built out of what the user actually said and is worth
                        // keeping; another draw costs them a minute and often
                        // comes back worse.
                        //
                        // Regenerating only when nothing usable is left, which is
                        // what strip returning null means: no question survived,
                        // and a Brainstorm reply without its question is not a
                        // shorter reply but a different mode.
                        if (_mode.value == Mode.BRAINSTORM) {
                            MethodAnnouncement.matched(builder.toString())?.let { hit ->
                                val fixed = MethodAnnouncement.strip(builder.toString())
                                android.util.Log.w(
                                    "KamMethod",
                                    "announced matched=$hit rewritten=${fixed != null} " +
                                        "draft=${builder.toString().take(160)}",
                                )
                                if (fixed == null) throw EchoDetected()
                                builder.setLength(0)
                                builder.append(fixed)
                                repository.updateMessage(messageId, fixed, true)
                            }
                        }
                        break
                    } catch (e: EchoDetected) {
                        // Every rejection says which check fired, on what text,
                        // and what the model had actually written. Without the
                        // draft there is no way to tell a correct rejection from
                        // a false positive after the fact, and a guard that
                        // regenerates silently cannot be debugged at all.
                        val why = PromptEcho.reasonFor(builder.toString(), systemText, lastUser)
                        android.util.Log.w(
                            "KamEcho",
                            "rejected retried=$retried check=${why?.check ?: "streaming"} " +
                                "matched=${why?.matched?.take(60)} draft=${builder.toString().take(160)}",
                        )
                        // Abandoning the stream trips the same path a user stop
                        // does, and the transcript then said "You stopped this
                        // one." with Continue and Retry beside a reply the user
                        // had nothing to do with. Nobody stopped anything.
                        stopReason = InferenceEngine.StopReason.Finished
                        builder.setLength(0)
                        if (retried) {
                            builder.append(ECHO_FALLBACK)
                            repository.updateMessage(messageId, ECHO_FALLBACK, true)
                            break
                        }
                        retried = true
                        repository.updateMessage(messageId, "", true)
                    }
                }
            } finally {
                // NonCancellable because this block is what makes a stopped or
                // abandoned answer honest, and every call in it suspends. If the
                // scope is canceled, by the screen going away mid-answer, an
                // unwrapped block throws at its first suspension point and the
                // message is left incomplete with no reason for ever. See #40.
                withContext(NonCancellable) {
                    // Stopped here, inside the block that already runs
                    // NonCancellable, so an answer abandoned by the screen going
                    // away still releases the service.
                    GenerationService.stop(repository.appContext)

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

                    // Written here, before anything else touches the context
                    // (#52). Titling and auto-extraction each run their own
                    // prompt through the same single sequence, so by the time
                    // they are done the cache holds their prompt and not this
                    // conversation's: measured, a saved state taken after titling
                    // was 268 tokens of titling instruction where the
                    // conversation was 1700 tokens long.
                    //
                    // Per turn rather than on leaving the screen, which is also
                    // what makes it reliable. The screen's own teardown races
                    // with the view model being cleared, and an encrypted eight
                    // megabyte write takes about sixty milliseconds at the end of
                    // a turn that took a minute.
                    saveConversationState(conversationId)

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
        val result = repository.remember(fact, conversationId, auto = false)
        // Say what was removed as well as what was kept. A fact quietly deleted
        // is one the user finds out about the next time it fails to come up.
        _notice.value = when {
            // A retraction: it removed something and stored nothing, which is
            // exactly what it was for.
            !result.stored && result.removed.isNotEmpty() ->
                "Forgotten: ${result.removed.first().take(60)}"
            // Already known. Saying "saved" would be a small lie, and the user
            // asked twice precisely because they were not sure it had stuck.
            !result.stored -> "Already in memory: ${fact.take(60)}"
            result.removed.isEmpty() -> "Saved to memory: ${fact.take(60)}"
            else ->
                "Saved to memory: ${fact.take(60)}. Replaced: ${result.removed.first().take(60)}"
        }
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
        // Same reason as titling: this runs on the conversation's own sequence,
        // so without a snapshot it leaves the extraction prompt in the cache and
        // the next message re-prefills the conversation (#71).
        engine.preservingCache {
            engine.generate(prompt, Mode.BENCH, maxTokens = AUTO_MEMORY_MAX_TOKENS).collect {
                builder.append(it.text)
            }
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

    /** Raised when a reply turns out to be prompt text, so it can be abandoned. */
    private class EchoDetected : RuntimeException("reply matched prompt text")

    companion object {
        /**
         * Written in code rather than by the model, for the case where two
         * attempts both came back as prompt text. It says what is true and asks
         * for the one thing that helps, without pretending an answer was given.
         */
        const val ECHO_FALLBACK =
            "That came out wrong. Say it again, or add a little more, and I will have another go."

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

        /**
         * How long a summary may run to (#86).
         *
         * A cap rather than a hope: a model that rambles turns a summary into
         * the thing it was meant to replace.
         *
         * Was 400, which is eighty seconds of decode on this device at around
         * five tokens a second, and half of the two minutes #90 reported. Three
         * short parts fit in 200, and the instruction now asks for that length
         * too, since a cap the model does not know about produces a summary cut
         * off mid sentence.
         */
        const val SUMMARY_MAX_TOKENS = 200

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
