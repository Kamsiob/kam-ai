package com.kamsiob.kamai.assist

import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.ChatFormat
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.Models
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the quick overlay: one question in, one short answer out, in Overlay
 * mode. It is deliberately not a conversation. Everything runs through the same
 * model manager and engine as the main app, so the overlay obeys the same
 * one-model-resident and memory rules.
 */
class OverlayViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KamRepository.get(app)
    private val engine: InferenceEngine = Models.engine(app)
    private val modelManager = Models.manager(app)

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var job: Job? = null

    // Voice input, the same transient-load STT used elsewhere.
    private val recorder = com.kamsiob.kamai.voice.AudioRecorder()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    // Reactive, so voice becoming available (or being installed while the overlay
    // is open) is reflected immediately, rather than a stale value from init that
    // left the microphone looking unavailable for no clear reason.
    val voiceAvailable: StateFlow<Boolean> =
        repository.observeActiveSttModel()
            .map { it != null }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    /** The user's chosen default input for the overlay: voice or text. */
    val defaultToVoice: StateFlow<Boolean> =
        repository.observeSetting(KamRepository.Keys.ASSISTANT_DEFAULT_VOICE)
            .map { it == "true" }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    fun setQuestion(text: String) { _question.value = text }

    fun ask(text: String = _question.value) {
        if (text.isBlank() || _streaming.value) return
        _question.value = text
        _answer.value = ""
        _streaming.value = true
        job = viewModelScope.launch {
            when (val status = modelManager.ensureLoaded()) {
                is com.kamsiob.kamai.llm.ModelManager.Status.Loaded -> Unit
                is com.kamsiob.kamai.llm.ModelManager.Status.NoModel -> {
                    _notice.value = "No model set up yet. Open Kam AI to download one."
                    _streaming.value = false; return@launch
                }
                is com.kamsiob.kamai.llm.ModelManager.Status.Refused -> {
                    _notice.value = status.reason; _streaming.value = false; return@launch
                }
                is com.kamsiob.kamai.llm.ModelManager.Status.Failed -> {
                    _notice.value = status.reason; _streaming.value = false; return@launch
                }
                else -> {
                    _notice.value = "The model is not ready yet. Try again in a moment."
                    _streaming.value = false; return@launch
                }
            }

            val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
            var system = SystemPrompts.forMode(Mode.OVERLAY)
            val now = java.util.Date()
            val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault())
            system = SystemPrompts.withDate(system, fmt.format(now))
            val turn = PromptBuilder.Turn(Role.USER, text)
            val prompt = PromptBuilder.build(format, system, listOf(turn))

            val builder = StringBuilder()
            var stop: InferenceEngine.StopReason = InferenceEngine.StopReason.Finished
            try {
                engine.generate(prompt, Mode.OVERLAY, maxTokens = 512, onStop = { stop = it })
                    .collect { chunk ->
                        builder.append(chunk.text)
                        _answer.value = PromptBuilder.cleanOutput(builder.toString())
                    }
            } finally {
                _answer.value = PromptBuilder.cleanOutput(builder.toString())
                when (val r = stop) {
                    is InferenceEngine.StopReason.OutOfRoom -> _notice.value = r.message
                    is InferenceEngine.StopReason.Overheating -> _notice.value = r.message
                    is InferenceEngine.StopReason.Failed -> if (_answer.value.isEmpty()) _notice.value = r.message
                    else -> Unit
                }
                _streaming.value = false
            }
        }
    }

    fun stop() {
        engine.requestStop()
        job?.cancel()
        _streaming.value = false
    }

    fun startRecording() {
        if (_recording.value || _transcribing.value) return
        if (recorder.start(viewModelScope)) _recording.value = true
        else _notice.value = "The microphone could not be opened."
    }

    fun stopAndTranscribe() {
        if (!_recording.value) return
        _recording.value = false
        val pcm = recorder.stop()
        _transcribing.value = true
        viewModelScope.launch {
            val model = repository.activeSttModel()
            if (model == null) { _transcribing.value = false; return@launch }
            val stt = com.kamsiob.kamai.voice.Voice.stt(getApplication())
            when (val r = stt.transcribe(repository.fileForStt(model), pcm)) {
                is com.kamsiob.kamai.voice.SttEngine.Result.Ok -> {
                    _question.value = r.text
                    ask(r.text)
                }
                is com.kamsiob.kamai.voice.SttEngine.Result.Error -> _notice.value = r.message
            }
            _transcribing.value = false
        }
    }

    fun cancelRecording() {
        if (_recording.value) { recorder.cancel(); _recording.value = false }
    }

    /**
     * Creates a full conversation from this exchange and hands it to the main app
     * to open. The question and answer become the first two messages, so the user
     * can keep going where the overlay left off.
     */
    fun handoff(onReady: (String) -> Unit) {
        val q = _question.value
        val a = _answer.value
        viewModelScope.launch {
            val id = repository.createConversation(Mode.GENERAL)
            if (q.isNotBlank()) repository.addMessage(id, Role.USER, q)
            if (a.isNotBlank()) repository.addMessage(id, Role.ASSISTANT, a, incomplete = false)
            // Title it here, through the shared path, so an overlay conversation
            // arrives in the chat list already named rather than blank until it is
            // opened. The overlay's own answer has finished, so this runs alone on
            // the engine. It is a brief one-shot before the app opens.
            runCatching {
                com.kamsiob.kamai.llm.ConversationTitler.titleIfNeeded(repository, engine, id)
            }
            Handoff.request(id)
            onReady(id)
        }
    }

    /** Flags the answer into Follow-ups, with the question as context. */
    fun flag(onDone: () -> Unit) {
        val q = _question.value
        val a = _answer.value
        if (a.isBlank()) return
        viewModelScope.launch {
            val id = repository.flag(a, Mode.OVERLAY, conversationId = null, messageId = null)
            if (q.isNotBlank()) repository.setFollowUpNote(id, "Asked: $q")
            onDone()
        }
    }
}
