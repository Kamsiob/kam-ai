package com.kamsiob.kamai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.llm.ChatFormat
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> =
        _conversationId
            .flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(conversationId: String) {
        _conversationId.value = conversationId
        viewModelScope.launch {
            repository.conversation(conversationId)?.let { _mode.value = it.mode }
        }
    }

    fun setMode(mode: Mode) {
        _mode.value = mode
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _streaming.value) return

        viewModelScope.launch {
            val id = _conversationId.value ?: repository.createConversation(_mode.value).also {
                _conversationId.value = it
            }
            repository.addMessage(id, Role.USER, trimmed)
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

    private suspend fun buildPrompt(conversationId: String): String {
        val conversation = repository.conversation(conversationId)
        val history = repository.messages(conversationId)

        var system = SystemPrompts.forMode(_mode.value)

        conversation?.projectId?.let { projectId ->
            repository.project(projectId)?.let {
                system = SystemPrompts.withProject(system, it.instructions)
            }
        }

        val memories = repository.recentMemory(MEMORY_LIMIT)
        system = SystemPrompts.withMemory(system, memories)

        // Leave room for the reply itself, not just the prompt.
        val contextSize = engine.contextSize.takeIf { it > 0 } ?: DEFAULT_CONTEXT
        val budget = contextSize - engine.countTokens(system) - RESERVED_FOR_REPLY

        val turns = history.map { PromptBuilder.Turn(it.role, it.content) }
        val fitted = PromptBuilder.fitToBudget(turns, budget.coerceAtLeast(256)) {
            PromptBuilder.roughTokenCount(it)
        }

        return PromptBuilder.build(chatFormat(), system, fitted)
    }

    /** The layout the loaded model wants. Falls back to Gemma, the default tier. */
    private suspend fun chatFormat(): ChatFormat =
        repository.activeModel()?.format ?: ChatFormat.GEMMA

    private fun respond(conversationId: String) {
        generation?.cancel()
        _streaming.value = true

        generation = viewModelScope.launch {
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
            }
        }
    }

    /**
     * Titles the conversation after the first exchange, as its own one-shot
     * request. Asking the chat model to title itself mid-conversation confuses
     * small models into answering the instruction instead.
     */
    private suspend fun maybeTitle(conversationId: String) {
        val conversation = repository.conversation(conversationId) ?: return
        if (conversation.title != null) return

        val history = repository.messages(conversationId)
        if (history.size < 2) return

        val transcript = history.take(2).joinToString("\n\n") { message ->
            val who = if (message.role == Role.USER) "Them" else "You"
            "$who: ${message.content.take(TITLE_SOURCE_CHARS)}"
        }

        val prompt = PromptBuilder.oneShot(chatFormat(), SystemPrompts.TITLE_INSTRUCTION, transcript)
        val builder = StringBuilder()
        engine.generate(prompt, Mode.BENCH, maxTokens = TITLE_MAX_TOKENS).collect {
            builder.append(it.text)
        }

        val title = PromptBuilder.cleanOutput(builder.toString())
            .lineSequence().firstOrNull().orEmpty()
            .trim().trim('"', '\'', '.')
            .take(TITLE_MAX_CHARS)

        if (title.isNotBlank()) repository.setTitle(conversationId, title)
    }

    private companion object {
        const val MEMORY_LIMIT = 12
        const val RESERVED_FOR_REPLY = 768
        const val DEFAULT_CONTEXT = 4096
        const val TITLE_SOURCE_CHARS = 400
        const val TITLE_MAX_TOKENS = 24
        const val TITLE_MAX_CHARS = 60
    }
}
