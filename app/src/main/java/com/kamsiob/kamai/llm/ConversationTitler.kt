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

    /**
     * Whether a titling pass should run at all.
     *
     * Pure, and separate from the work, because this is the rule that decides how
     * often a multi-second model run happens, and getting it wrong is expensive
     * rather than merely wrong. Extracted after a conversation sitting at exactly
     * the refresh milestone was found re-titling itself on every open (#38).
     *
     * @param messageCount real messages, with display-only mode markers already
     *   removed.
     */
    fun shouldTitle(hasTitle: Boolean, messageCount: Int, allowRefresh: Boolean): Boolean {
        // Nothing to go on yet: one message is a question with no answer.
        if (messageCount < 2) return false
        // Never titled, so anything is better than nothing.
        if (!hasTitle) return true
        // Already titled. Only the refresh milestone may replace it, and only
        // when the caller is one that can actually have moved the conversation
        // past that milestone.
        return allowRefresh && messageCount == TITLE_REFRESH_AT
    }

    /** Generic filler a title must never be; the first user message is used instead. */
    private val GENERIC = setOf(
        "title", "conversation", "new conversation", "new chat", "untitled",
        "chat", "response", "answer", "reply", "here is the title", "a title",
    )

    /**
     * @param allowRefresh whether this call may also *replace* an existing title
     *   at the refresh milestone. False for the safety net that runs when a
     *   conversation is merely opened.
     *
     *   That distinction matters more than it looks. The refresh fires when the
     *   history is exactly [TITLE_REFRESH_AT] long, and opening a conversation
     *   does not change its length, so a conversation sitting at exactly that
     *   many messages re-titled itself **every single time it was opened**. The
     *   user watched the title change under them for no reason, and each one was
     *   a model run that overwrote the KV cache and cost the next real turn a full
     *   re-prefill. See #38.
     */
    suspend fun titleIfNeeded(
        repository: KamRepository,
        engine: InferenceEngine,
        conversationId: String,
        allowRefresh: Boolean = true,
    ) {
        val conversation = repository.conversation(conversationId) ?: return
        if (conversation.titleIsManual) return

        // Ignore display-only mode markers when judging content and building the source.
        val history = repository.messages(conversationId).filter { it.role != Role.SYSTEM }
        if (history.size < 2) return
        if (!shouldTitle(
                hasTitle = conversation.title != null,
                messageCount = history.size,
                allowRefresh = allowRefresh,
            )
        ) {
            return
        }

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
