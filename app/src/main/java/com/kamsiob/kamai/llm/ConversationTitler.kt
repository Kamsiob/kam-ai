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

        // The model writes the title only at the refresh milestone. Before that,
        // an honest excerpt of the first question is used, even when the model is
        // sitting right there and could do better.
        //
        // That is a deliberate trade against title quality, and it is the whole
        // remaining cost of #38. A titling run does not share a prefix with the
        // conversation, so it overwrites the KV cache, and the user's next message
        // then re-prefills the entire history: measured at fourteen seconds on a
        // short conversation and thirty on a longer one. Paying that on the very
        // first exchange is paying it at the worst possible moment, right when
        // somebody is going back and forth quickly.
        //
        // So the first title is instant and free and made of the user's own words,
        // and the model replaces it once at message TITLE_REFRESH_AT, by which
        // point the conversation is long enough that one re-prefill is a smaller
        // share of the whole and the user is likely mid-read rather than mid-flow.
        //
        // This becomes unnecessary once titling runs on its own KV sequence, which
        // is the proper fix and a native change. Revert this branch then.
        val useModel = engine.isLoaded && history.size >= TITLE_REFRESH_AT
        val title = if (useModel) {
            val format = repository.activeModel()?.format ?: ChatFormat.GEMMA
            val transcript = history.take(2).joinToString("\n\n") { m ->
                val who = if (m.role == Role.USER) "Them" else "You"
                "$who: ${m.content.take(TITLE_SOURCE_CHARS)}"
            }
            val prompt = PromptBuilder.oneShot(format, SystemPrompts.TITLE_INSTRUCTION, transcript)
            val builder = StringBuilder()
            // Snapshot the conversation's cache around this, or the title prompt
            // is what stays in it and the user's next message re-prefills the
            // whole conversation (#71).
            engine.preservingCache {
                engine.generate(prompt, Mode.BENCH, maxTokens = TITLE_MAX_TOKENS)
                    .collect { builder.append(it.text) }
            }
            val generated = clean(builder.toString())
            // A blank, generic, or invented answer is worse than the excerpt
            // fallback, which is always at least true (#125).
            if (isUsable(generated) && titleIsAbout(generated, history)) {
                generated
            } else {
                fallback(history)
            }
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

    /**
     * Words too common to say what a conversation is about. Kept short and dull
     * on purpose: this list exists to stop "the" counting as a subject, not to
     * judge writing.
     */
    private val COMMON = setOf(
        "about", "after", "again", "also", "another", "any", "anything", "because",
        "been", "being", "between", "both", "could", "does", "doing", "done",
        "down", "each", "even", "ever", "every", "from", "gets", "give", "going",
        "have", "here", "into", "just", "know", "like", "make", "many", "more",
        "most", "much", "need", "never", "next", "only", "other", "over", "part",
        "really", "same", "should", "some", "such", "sure", "take", "than", "that",
        "their", "them", "then", "there", "these", "they", "thing", "things",
        "think", "this", "those", "time", "very", "want", "well", "were", "what",
        "when", "where", "which", "while", "will", "with", "without", "would",
        "your", "yours",
    )

    private fun subjectWords(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 && it !in COMMON }
            .toSet()

    /**
     * True when the title is about the conversation it names (#125).
     *
     * A conversation about the Roman empire, potato storage, a broken printer and
     * a dentist appointment was titled "Capital of Australia City Name". Nothing
     * in it mentioned Australia, a capital, or a city.
     *
     * The cause was asking for a title at all: titling reads the first exchange,
     * that exchange was "?" answered by a clarifying question, and a model given
     * nothing to name will name something. `isUsable` cannot catch it, because
     * the invention is neither blank nor generic; it is simply about a different
     * conversation.
     *
     * So the title is checked against the thing it claims to describe. Sharing
     * one substantial word is a low bar, and it is exactly the bar an invented
     * title fails. A wrong title is worse than a dull one: the excerpt fallback
     * is always at least true, and this was confidently false.
     */
    fun titleIsAbout(title: String, history: List<MessageEntity>): Boolean {
        val fromTitle = subjectWords(title)
        // A title made only of common words says nothing about anything, which is
        // what a model produces when it has nothing to name.
        if (fromTitle.isEmpty()) return false
        val fromConversation = history.flatMap { subjectWords(it.content) }.toSet()
        // Nothing substantial anywhere in the conversation means there is nothing
        // to check against, and refusing every title then would be wrong.
        if (fromConversation.isEmpty()) return true
        // Most of the title has to be about this conversation, not one word of it.
        // "Capital of Australia City Name" shares exactly one word with a
        // conversation about the Roman empire, and it is "city", from
        // "city-state" in a sentence about something else entirely. One
        // coincidence is not aboutness.
        val shared = fromTitle.count { it in fromConversation }
        return shared * 2 >= fromTitle.size
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
