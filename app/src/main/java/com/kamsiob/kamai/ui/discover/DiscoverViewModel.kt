package com.kamsiob.kamai.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamsiob.kamai.data.KamRepository
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.FollowUpEntity
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.data.QuizStatsEntity
import com.kamsiob.kamai.discover.Moment
import com.kamsiob.kamai.discover.PackInfo
import com.kamsiob.kamai.download.Downloader
import com.kamsiob.kamai.llm.ChatFormat
import com.kamsiob.kamai.llm.InferenceEngine
import com.kamsiob.kamai.llm.Models
import com.kamsiob.kamai.llm.PromptBuilder
import com.kamsiob.kamai.llm.SystemPrompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives Discover: the single dealt card, the reader, saved moments, pack
 * management, the grounded and open chat handoffs, and the quiz. Reads moment
 * content from the downloaded pack files and keeps the user's own state (seen,
 * saved, quiz tallies) in the encrypted app database.
 */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KamRepository.get(app)
    private val engine: InferenceEngine = Models.engine(app)
    private val modelManager = Models.manager(app)

    private val _manifest = MutableStateFlow<List<PackInfo>>(emptyList())
    val manifest: StateFlow<List<PackInfo>> = _manifest.asStateFlow()

    private val _installedIds = MutableStateFlow<Set<String>>(emptySet())
    val installedIds: StateFlow<Set<String>> = _installedIds.asStateFlow()

    private val _current = MutableStateFlow<Moment?>(null)
    val current: StateFlow<Moment?> = _current.asStateFlow()

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _readerOpen = MutableStateFlow(false)
    val readerOpen: StateFlow<Boolean> = _readerOpen.asStateFlow()

    private val _currentSaved = MutableStateFlow(false)
    val currentSaved: StateFlow<Boolean> = _currentSaved.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val downloads: StateFlow<List<com.kamsiob.kamai.download.Downloads.Item>> =
        com.kamsiob.kamai.download.Downloads.items

    val saved: StateFlow<List<FollowUpEntity>> =
        repository.observeSavedMoments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<List<QuizStatsEntity>> =
        repository.observeQuizStats()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val packArtifacts = repository.observePackArtifacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            _installedIds.value = repository.installedPackIds().toSet()
            if (_installedIds.value.isNotEmpty() && _current.value == null) deal()
            // Manifest is best-effort and only for discovering new packs.
            _manifest.value = repository.fetchDiscoverManifest()
        }
    }

    fun deal() {
        viewModelScope.launch {
            val moment = repository.dealMoment()
            if (moment == null) {
                _exhausted.value = _installedIds.value.isNotEmpty()
                _current.value = null
            } else {
                repository.markDrawn(moment.packId, moment.id)
                _current.value = moment
                _exhausted.value = false
                _readerOpen.value = false
                _currentSaved.value = repository.isMomentSaved(moment.packId, moment.id)
            }
        }
    }

    fun openReader() {
        val m = _current.value ?: return
        _readerOpen.value = true
        viewModelScope.launch { repository.markReaderOpened(m.packId, m.id) }
    }

    fun closeReader() { _readerOpen.value = false }

    fun toggleSave() {
        val m = _current.value ?: return
        viewModelScope.launch {
            if (_currentSaved.value) {
                repository.unsaveMoment(m.packId, m.id)
                _currentSaved.value = false
            } else {
                repository.saveMoment(m)
                _currentSaved.value = true
            }
        }
    }

    /** Opens a saved moment as a grounded discussion, since the pack card for it
     *  may no longer be the dealt one. */
    fun openSaved(packId: String, momentId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.openMomentDiscussion(packId, momentId)
            if (id == null) {
                _notice.value = "That moment's pack is not installed. Get it again from Packs."
                return@launch
            }
            onReady(id)
        }
    }

    fun unsave(packId: String, momentId: String) {
        viewModelScope.launch {
            repository.unsaveMoment(packId, momentId)
            if (_current.value?.let { it.packId == packId && it.id == momentId } == true) {
                _currentSaved.value = false
            }
        }
    }

    fun reshuffle() {
        viewModelScope.launch {
            repository.reshuffleAll()
            _exhausted.value = false
            deal()
        }
    }

    fun dismissNotice() { _notice.value = null }

    // Packs

    fun downloadPack(pack: PackInfo) {
        com.kamsiob.kamai.download.Downloads.start(
            getApplication(), repository.downloader,
            com.kamsiob.kamai.download.Downloads.Spec(
                id = pack.id,
                displayName = pack.name,
                kind = "pack",
                url = pack.downloadUrl,
                destination = repository.fileForPack(pack.fileName),
                sizeBytes = pack.sizeBytes,
                sha256 = pack.sha256,
                onInstalled = { file ->
                    repository.registerPack(pack, file)
                    _installedIds.value = repository.installedPackIds().toSet()
                    if (_current.value == null) deal()
                    _notice.value = "${pack.name} is ready. ${pack.moments} moments to read."
                },
            ),
        )
    }

    fun pauseDownload(id: String) = com.kamsiob.kamai.download.Downloads.pause(getApplication(), id)
    fun resumeDownload(id: String) =
        com.kamsiob.kamai.download.Downloads.resume(getApplication(), repository.downloader, id)
    fun cancelDownload(id: String) = com.kamsiob.kamai.download.Downloads.cancel(getApplication(), id)

    fun removePack(packId: String) {
        viewModelScope.launch {
            repository.deleteArtifact(packId)
            _installedIds.value = repository.installedPackIds().toSet()
            if (_current.value?.packId == packId) { _current.value = null; deal() }
        }
    }

    // Quiz. Questions come strictly from the full passage. Grading is honest
    // self-assessment (the passage's answer is shown), which avoids a small model
    // unreliably marking free text.

    data class QuizQuestion(val question: String, val answer: String, val quote: String)

    sealed interface QuizState {
        data object Idle : QuizState
        data object NeedsReader : QuizState // tapped Quiz me before opening the reader
        data object Generating : QuizState
        data class Asking(
            val questions: List<QuizQuestion>,
            val index: Int,
            val revealed: Boolean,
            val right: Int,
        ) : QuizState
        data class Done(val right: Int, val total: Int) : QuizState
    }

    private val _quiz = MutableStateFlow<QuizState>(QuizState.Idle)
    val quiz: StateFlow<QuizState> = _quiz.asStateFlow()

    private var quizJob: kotlinx.coroutines.Job? = null

    /** Tapped Quiz me. If the reader was not opened for this card, prompt first. */
    fun quizMe(force: Boolean = false) {
        val m = _current.value ?: return
        quizJob?.cancel()
        quizJob = viewModelScope.launch {
            if (!force && !repository.wasReaderOpened(m.packId, m.id)) {
                _quiz.value = QuizState.NeedsReader
                return@launch
            }
            _quiz.value = QuizState.Generating
            val questions = generateQuiz(m)
            _quiz.value = if (questions.isEmpty()) {
                _notice.value = "A quiz could not be made for this one. Try another moment."
                QuizState.Idle
            } else {
                QuizState.Asking(questions, 0, revealed = false, right = 0)
            }
        }
    }

    /**
     * Stops any quiz, including one still being generated, so it never keeps
     * running in the background and surfaces unexpectedly when the user comes back
     * to Discover. Called on dismiss and when leaving the screen.
     */
    fun cancelQuiz() {
        quizJob?.cancel()
        quizJob = null
        engine.requestStop()
        _quiz.value = QuizState.Idle
    }

    fun revealAnswer() {
        val s = _quiz.value as? QuizState.Asking ?: return
        _quiz.value = s.copy(revealed = true)
    }

    fun markQuizAnswer(correct: Boolean) {
        val s = _quiz.value as? QuizState.Asking ?: return
        val right = s.right + if (correct) 1 else 0
        if (s.index + 1 >= s.questions.size) {
            _quiz.value = QuizState.Done(right, s.questions.size)
            val m = _current.value
            if (m != null) viewModelScope.launch { repository.recordQuiz(m.packId, s.questions.size, right) }
        } else {
            _quiz.value = s.copy(index = s.index + 1, revealed = false, right = right)
        }
    }

    /** One-tap flag on a missed question. */
    fun flagMissed(question: QuizQuestion, onFlagged: () -> Unit) {
        val m = _current.value ?: return
        viewModelScope.launch {
            val text = "From \"${m.title}\": ${question.question}\nAnswer: ${question.answer}"
            repository.flag(text, Mode.DISCOVER, conversationId = null, messageId = null)
            onFlagged()
        }
    }

    private suspend fun generateQuiz(m: Moment): List<QuizQuestion> {
        when (modelManager.ensureLoaded()) {
            is com.kamsiob.kamai.llm.ModelManager.Status.Loaded -> Unit
            else -> return emptyList()
        }
        val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
        val instruction = """
            Write 4 short quiz questions that test whether someone read and
            understood the passage below. Use only facts stated in the passage.

            Write each question on its own line, then the answer on the next line.
            Use exactly this shape and nothing else:

            Q: a question about the passage
            A: the answer, taken from the passage

            Q: another question
            A: its answer
        """.trimIndent()
        val turn = PromptBuilder.Turn(Role.USER, "$instruction\n\nPassage:\n\n${m.passage}")
        val prompt = PromptBuilder.build(format, SystemPrompts.forMode(Mode.BENCH), listOf(turn))
        val out = StringBuilder()
        engine.generate(prompt, Mode.BENCH, maxTokens = 700).collect { out.append(it.text) }
        return parseQuiz(PromptBuilder.cleanOutput(out.toString()))
    }

    private fun parseQuiz(text: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()

        // Preferred: pipe-delimited "question ||| answer ||| quote" lines.
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("-").trim().replace(Regex("^\\d+[).:]\\s*"), "")
            if ("|||" !in line) continue
            val parts = line.split("|||").map { it.trim() }
            if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) continue
            if (isPlaceholder(parts[0], parts[1], parts.getOrElse(2) { "" })) continue
            questions.add(QuizQuestion(parts[0].removePrefix("Q:").trim(), parts[1].removePrefix("A:").trim(), parts.getOrElse(2) { "" }))
        }
        if (questions.isNotEmpty()) return questions.take(5)

        // Fallback: Q:/A: pairs across lines, the small-model-friendly shape.
        var pendingQ: String? = null
        for (raw in text.lines()) {
            val line = raw.trim().removePrefix("-").trim()
            if (line.isEmpty()) continue
            val qMatch = Regex("^(?:Q\\d*[).:]|Question\\s*\\d*[).:]?|\\d+[).:])\\s*(.+)", RegexOption.IGNORE_CASE).find(line)
            val aMatch = Regex("^(?:A\\d*[).:]|Answer\\s*\\d*[).:]?)\\s*(.+)", RegexOption.IGNORE_CASE).find(line)
            when {
                aMatch != null && pendingQ != null -> {
                    val ans = aMatch.groupValues[1].trim()
                    if (ans.isNotBlank() && !isPlaceholder(pendingQ!!, ans, "")) {
                        questions.add(QuizQuestion(pendingQ!!, ans, ""))
                    }
                    pendingQ = null
                }
                qMatch != null -> pendingQ = qMatch.groupValues[1].trim()
                line.endsWith("?") -> pendingQ = line
            }
            if (questions.size >= 5) break
        }
        return questions
    }

    private fun isPlaceholder(q: String, a: String, quote: String) =
        q.equals("QUESTION", true) || q.equals("a question about the passage", true) ||
            a.equals("ANSWER", true) || a.equals("the answer, taken from the passage", true) ||
            quote.equals("SUPPORTING SENTENCE", true)

    /**
     * Grounded chat: "Discuss this passage". Creates a conversation confined to the
     * passage and hands it to the main app to open.
     */
    fun discuss(onReady: (String) -> Unit) {
        val m = _current.value ?: return
        viewModelScope.launch {
            val id = repository.createConversation(Mode.DISCOVER)
            repository.addMessage(
                id, Role.ASSISTANT,
                "Let's talk about \"${m.title}\". Ask me anything about this passage.",
                incomplete = false,
            )
            repository.setDiscoverGrounding(id, m.passage)
            onReady(id)
        }
    }

    /**
     * Open chat: "Explore this topic". A normal chat seeded to explore the subject
     * beyond the passage, with the usual honesty about a small model's recall.
     */
    fun explore(onReady: (String) -> Unit) {
        val m = _current.value ?: return
        viewModelScope.launch {
            val id = repository.createConversation(Mode.CHAT)
            repository.addMessage(id, Role.USER, "Tell me more about ${m.title}.")
            onReady(id)
        }
    }
}
