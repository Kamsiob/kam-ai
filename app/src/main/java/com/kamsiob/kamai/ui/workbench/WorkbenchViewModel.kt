package com.kamsiob.kamai.ui.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.ChatFormat
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.ModelManager
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Workbench: paste text, apply a transformation, get a result you can
 * copy, flag, or transform again. It is not a conversation, so it does not use
 * the messages store; instead it keeps a single input and a single output, both
 * persisted so they survive rotation and process death.
 *
 * Every transformation runs through the language model in Workbench mode, whose
 * fixed instructions return only the transformed text.
 */
class WorkbenchViewModel(
    private val repository: KamRepository,
    private val engine: InferenceEngine,
    private val modelManager: ModelManager,
) : ViewModel() {

    /** The plain transformations offered as buttons, each with the instruction it
     *  sends. A free instruction field covers everything else. */
    enum class Action(val label: String, val instruction: String) {
        TIGHTEN("Tighten", "Tighten this text. Cut the padding and repetition, keep every point and the meaning."),
        REWRITE("Rewrite", "Rewrite this text so it reads more clearly and naturally, keeping the meaning and the voice."),
        POINTS("Into points", "Reorganize this text into a short list of clear points."),
        SUMMARIZE("Summarize", "Summarize this text in a few plain sentences."),
        GRAMMAR("Fix grammar", "Fix the spelling, grammar, and punctuation in this text. Change nothing else."),
        FORMAL("More formal", "Rewrite this text in a more formal register, keeping the meaning."),
        CASUAL("More casual", "Rewrite this text in a more casual, everyday register, keeping the meaning."),
    }

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var job: Job? = null

    // Voice input, the same transient-load discipline as chat.
    private val recorder = com.kamsiob.kamai.voice.AudioRecorder()
    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private val _transcribing = MutableStateFlow(false)
    val transcribing: StateFlow<Boolean> = _transcribing.asStateFlow()

    fun startRecording() {
        if (_recording.value || _transcribing.value) return
        if (recorder.start(viewModelScope)) _recording.value = true
        else _notice.value = "The microphone could not be opened."
    }

    fun stopAndTranscribe(stt: com.kamsiob.kamai.voice.SttEngine, modelFile: java.io.File) {
        if (!_recording.value) return
        _recording.value = false
        val pcm = recorder.stop()
        _transcribing.value = true
        viewModelScope.launch {
            when (val r = stt.transcribe(modelFile, pcm)) {
                is com.kamsiob.kamai.voice.SttEngine.Result.Ok ->
                    setInput(if (_input.value.isBlank()) r.text else "${_input.value.trimEnd()} ${r.text}")
                is com.kamsiob.kamai.voice.SttEngine.Result.Error -> _notice.value = r.message
            }
            _transcribing.value = false
        }
    }

    fun cancelRecording() {
        if (_recording.value) { recorder.cancel(); _recording.value = false }
    }

    init {
        viewModelScope.launch {
            _input.value = repository.setting(KamRepository.Keys.WORKBENCH_INPUT).orEmpty()
            _output.value = repository.setting(KamRepository.Keys.WORKBENCH_OUTPUT).orEmpty()
        }
    }

    fun setInput(text: String) {
        _input.value = text
        viewModelScope.launch { repository.putSetting(KamRepository.Keys.WORKBENCH_INPUT, text) }
    }

    fun dismissNotice() { _notice.value = null }

    /** Runs [action] on the current input. */
    fun run(action: Action) = transform(action.instruction, _input.value)

    /** Runs a free-form [instruction] on the current input. */
    fun runCustom(instruction: String) {
        if (instruction.isBlank()) return
        transform(instruction, _input.value)
    }

    /** Chains another transformation onto the current output, making it the new
     *  input so the source and result stay honest about what was transformed. */
    fun chain(action: Action) {
        val current = _output.value
        if (current.isBlank()) return
        setInput(current)
        transform(action.instruction, current)
    }

    fun stop() {
        engine.requestStop()
        job?.cancel()
        _running.value = false
    }

    private fun transform(instruction: String, source: String) {
        if (source.isBlank()) {
            _notice.value = "Paste some text in first."
            return
        }
        job?.cancel()
        _running.value = true
        _output.value = ""

        job = viewModelScope.launch {
            when (val status = modelManager.ensureLoaded()) {
                is ModelManager.Status.Loaded -> Unit
                is ModelManager.Status.NoModel -> {
                    _notice.value = "No model is set up yet. Download one in Settings to start."
                    _running.value = false
                    return@launch
                }
                is ModelManager.Status.Refused -> {
                    _notice.value = status.reason; _running.value = false; return@launch
                }
                is ModelManager.Status.Failed -> {
                    _notice.value = status.reason; _running.value = false; return@launch
                }
                else -> {
                    _notice.value = "The model is not ready yet. Try again in a moment."
                    _running.value = false
                    return@launch
                }
            }

            val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
            val system = SystemPrompts.forMode(Mode.BENCH)
            val turn = PromptBuilder.Turn(Role.USER, "$instruction\n\nThe text:\n\n$source")
            val prompt = PromptBuilder.build(format, system, listOf(turn))

            val builder = StringBuilder()
            var stop: InferenceEngine.StopReason = InferenceEngine.StopReason.Finished
            try {
                engine.generate(prompt, Mode.BENCH, onStop = { stop = it }).collect { chunk ->
                    builder.append(chunk.text)
                    _output.value = PromptBuilder.cleanOutput(builder.toString())
                }
            } finally {
                val text = PromptBuilder.cleanOutput(builder.toString())
                _output.value = text
                repository.putSetting(KamRepository.Keys.WORKBENCH_OUTPUT, text)
                when (val r = stop) {
                    is InferenceEngine.StopReason.OutOfRoom -> _notice.value = r.message
                    is InferenceEngine.StopReason.Overheating -> _notice.value = r.message
                    is InferenceEngine.StopReason.Failed -> if (text.isEmpty()) _notice.value = r.message
                    else -> Unit
                }
                _running.value = false
            }
        }
    }

    companion object {
        fun factory(
            repository: KamRepository,
            engine: InferenceEngine,
            modelManager: ModelManager,
        ) = viewModelFactory {
            initializer { WorkbenchViewModel(repository, engine, modelManager) }
        }
    }
}
