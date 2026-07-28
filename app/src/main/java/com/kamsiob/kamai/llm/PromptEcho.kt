package com.kamsiob.kamai.llm

/**
 * Catches a reply that is really a piece of the prompt read back.
 *
 * Small on-device models reproduce the outputs of fixed worked examples
 * regardless of the input. This has now been found five separate times in this
 * app, and the fixes so far have all been prompt changes: remove the quotable
 * sentence, remove the paragraph quoting it, keep only examples that are
 * harmless if copied. Each one reduced the rate without ending it.
 *
 * This is the other half, and it is deliberately not clever. Regurgitation will
 * happen at some rate no matter how the prompt is written, so the goal here is
 * that a user never sees it. The worst instance was a message about a
 * bereavement answered with a line lifted from the instructions, and no amount
 * of prompt tuning makes that acceptable to ship on a maybe.
 *
 * Only the answer halves of examples are listed. The input halves are things a
 * user might legitimately type, and "Fix what? Tell me what is broken and I will
 * start there." is deliberately absent: it is the one example whose answer is
 * correct whenever it appears, which is exactly the property that makes an
 * example safe to keep in a prompt at all.
 */
object PromptEcho {

    /**
     * Answer text that exists verbatim in a prompt and must never be a reply.
     *
     * Kept in step with the prompts by [PromptEchoTest], which fails if one of
     * these stops appearing in SystemPrompts, so a prompt edit cannot quietly
     * leave the guard defending a line nobody sends any more.
     */
    val protected: List<String> = listOf(
        "Noted, I will keep to metric.",
        "Third time in a day points at something repeatable rather than bad luck.",
        "It was finished in 1889, for the Paris World's Fair.",
        "Hold the side button for ten seconds.",
        "An external drive, cheapest per gigabyte.",
        "A bigger internal card, faster but dearer.",
        "Cloud, which needs a connection.",
    )

    /**
     * Compared without punctuation or case, because the copies are rarely exact.
     * The bereavement reply was "I am Kam AI." where the prompt held "I am Kam
     * AI, an assistant running on this phone": shortened, repunctuated, and still
     * unmistakably the same sentence.
     */
    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val normalized: List<String> by lazy { protected.map { normalize(it) } }

    /** The minimum evidence before acting, so a reply is never discarded on one word. */
    private const val MIN_CHARS = 12

    /** True when [reply] is one of the protected lines, or opens with one. */
    fun isEcho(reply: String): Boolean {
        val n = normalize(reply)
        if (n.length < MIN_CHARS) return false
        return normalized.any { n == it || n.startsWith(it) }
    }

    /**
     * True when what has streamed so far can only be heading into a protected
     * line, so a reply can be abandoned early rather than after it completes.
     *
     * Checked against the start of each protected line rather than the whole, so
     * this fires a few words in. That matters: the alternative is buffering every
     * reply until it finishes, which would cost the time to first token that
     * issue #38 spent this whole project's performance budget winning back.
     */
    fun couldBecomeEcho(partial: String): Boolean {
        val n = normalize(partial)
        if (n.length < MIN_CHARS) return false
        return normalized.any { it.startsWith(n) || n.startsWith(it) }
    }
}
