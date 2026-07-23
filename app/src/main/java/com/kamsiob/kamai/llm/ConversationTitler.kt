package com.kamsiob.kamai.llm

import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.data.MessageEntity
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.data.KamRepository

/**
 * The one place that titles a conversation, so it happens the same way no matter
 * where the conversation was created: an in-app chat, the power button overlay, a
 * Discover discussion, or a share or selection handoff. Titling is a property of a
 * conversation gaining content, not of one particular screen, so every entry point
 * calls this rather than each rolling its own (which is how overlay conversations
 * ended up with no title at all).
 *
 * It is idempotent and cheap to over-call: it no-ops on a hand-set title, on a
 * conversation with fewer than two messages, and after the first title until the
 * refresh milestone.
 */
object ConversationTitler {

    const val TITLE_REFRESH_AT = 8
    private const val TITLE_SOURCE_CHARS = 400
    private const val TITLE_MAX_TOKENS = 24
    private const val TITLE_MAX_CHARS = 60

    /** Generic filler a title must never be; the first user message is used instead. */
    private val GENERIC = setOf(
        "title", "conversation", "new conversation", "new chat", "untitled",
        "chat", "response", "answer", "reply", "here is the title", "a title",
    )

    suspend fun titleIfNeeded(
        repository: KamRepository,
        engine: InferenceEngine,
        conversationId: String,
    ) {
        val conversation = repository.conversation(conversationId) ?: return
        if (conversation.titleIsManual) return

        // Ignore display-only mode markers when judging content and building the source.
        val history = repository.messages(conversationId).filter { it.role != Role.SYSTEM }
        if (history.size < 2) return
        val isFirstTitle = conversation.title == null
        val isRefresh = history.size == TITLE_REFRESH_AT
        if (!isFirstTitle && !isRefresh) return

        // Title with the model when it is already resident, for the best result.
        // Never load a multi-gigabyte model just to title a conversation on open:
        // that would be a large, pointless cost. When the model is not loaded, an
        // honest excerpt of the first question is a good, instant title, and a
        // model-written one can still replace it at the refresh milestone.
        val title = if (engine.isLoaded) {
            val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
            val transcript = history.take(2).joinToString("\n\n") { m ->
                val who = if (m.role == Role.USER) "Them" else "You"
                "$who: ${m.content.take(TITLE_SOURCE_CHARS)}"
            }
            val prompt = PromptBuilder.oneShot(format, SystemPrompts.TITLE_INSTRUCTION, transcript)
            val builder = StringBuilder()
            engine.generate(prompt, Mode.BENCH, maxTokens = TITLE_MAX_TOKENS).collect { builder.append(it.text) }
            val generated = clean(builder.toString())
            // A blank or generic answer is worse than the excerpt fallback.
            if (isUsable(generated)) generated else fallback(history)
        } else {
            fallback(history)
        }
        if (title.isNotBlank()) repository.autoTitle(conversationId, title)
    }

    /** First non-empty line, stripped of quotes, markdown, and stray punctuation. */
    private val STRIP = charArrayOf('"', '\'', '#', '*', '`', '.', ',', ':', ';', ' ', '\t')

    fun clean(raw: String): String =
        PromptBuilder.cleanOutput(raw)
            .lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            .trim(*STRIP)
            .take(TITLE_MAX_CHARS)

    fun isUsable(title: String): Boolean {
        val t = title.trim()
        if (t.length < 2) return false
        if (t.lowercase() in GENERIC) return false
        return true
    }

    /** A specific title from the first user message, e.g. "How tall is the Eiffel Tower". */
    fun fallback(history: List<MessageEntity>): String {
        val firstUser = history.firstOrNull { it.role == Role.USER }?.content?.trim().orEmpty()
        val line = firstUser.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isBlank()) return ""
        return line.split(Regex("\\s+")).take(8).joinToString(" ")
            .take(TITLE_MAX_CHARS).trimEnd('.', ',', '!', '?', ':', ';')
    }
}
