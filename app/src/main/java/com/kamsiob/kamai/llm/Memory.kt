package com.kamsiob.kamai.llm

/**
 * How the app decides what to remember. PART 7. The user is fully in control.
 *
 * - [MANUAL] is the safe, predictable default: nothing is remembered unless the
 *   user says so, for example "remember that ...".
 * - [AUTO] lets the app also keep durable, high-signal facts it notices.
 * - [OFF] remembers nothing at all.
 */
enum class MemoryMode { MANUAL, AUTO, OFF }

/**
 * Pulls durable facts out of what the user says. Two paths: a plain manual
 * trigger the user types, and an auto extraction the model runs when the user
 * has opted into it.
 */
object MemoryExtractor {

    // "remember that I ...", "remember: ...", "remember to ..." (kept),
    // "remember I prefer ...". The captured remainder is the fact.
    private val MANUAL = Regex(
        """^\s*(?:please\s+)?remember(?:\s+that|\s*[:,-])?\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A durable fact the user explicitly asked to remember, or null. Works even
     * in Manual mode, because the whole point of Manual is that this still fires.
     */
    fun manualFact(userMessage: String): String? {
        val match = MANUAL.find(userMessage.trim()) ?: return null
        val fact = match.groupValues[1].trim().trimEnd('.', ' ')
        // Rewrite a first-person "remember I like tea" into a stored "likes tea"
        // where it is trivial, otherwise keep the user's words.
        return fact.takeIf { it.length in 2..280 }
    }

    /**
     * The one-shot instruction that asks the model to surface durable facts from
     * an exchange, used only in Auto mode. The rules are strict on purpose: a
     * small, high-signal store is worth far more than a large noisy one.
     */
    val AUTO_INSTRUCTION = """
        Look at the exchange below and decide whether it contains a durable fact
        about the user that would help you help them later. Only keep:
        stated preferences, ongoing projects, recurring context, and personal
        facts the user clearly volunteered about themselves.

        Do not keep: one-off trivia, transient details ("I am tired today"),
        anything sensitive the user did not clearly offer as durable, the topic
        of a single question, or anything you are unsure about.

        Reply with each fact on its own line, in a short third-person form such as
        "prefers plain language" or "is learning Spanish". If there is nothing
        worth keeping, reply with exactly NONE and nothing else.
    """.trimIndent()

    /**
     * Parses the model's auto-extraction reply into facts to store. Defensive
     * against the model rambling: drops anything that looks like a refusal, a
     * NONE, or an over-long line, and caps how many are taken from one exchange.
     */
    fun parseAutoReply(reply: String, maxPerExchange: Int = 2): List<String> {
        val cleaned = reply.trim()
        if (cleaned.isEmpty() || cleaned.equals("NONE", ignoreCase = true)) return emptyList()

        return cleaned.lineSequence()
            .map { it.trim().trimStart('-', '*', '•', ' ').trim() }
            // Drop chat-template tokens the model sometimes emits, for example
            // "NONE</start_of_turn>" or a bare "<end_of_turn>", so they never get
            // stored as junk memories. A real fact never contains a '<'.
            .map { it.substringBefore('<').trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.equals("NONE", ignoreCase = true) &&
                    line.length in 3..200 &&
                    // A model that decided there was nothing sometimes says so in
                    // a sentence rather than the literal token.
                    !line.contains("no durable", ignoreCase = true) &&
                    !line.contains("nothing worth", ignoreCase = true)
            }
            .distinct()
            .take(maxPerExchange)
            .toList()
    }

    /** A normalised form for near-duplicate detection: lowercase, alphanumerics
     *  and single spaces only, so "Likes tea." and "likes  tea" collapse. */
    fun normalise(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Chooses which memories to inject for a given message. Dumping the whole store
 * into every prompt is the fastest way to wreck a small model, so this scores each
 * entry by how much it overlaps the current message and how recent it is, then
 * fills a small character budget with the best. On-device embeddings are deferred,
 * so this is practical keyword-and-recency matching, with a clean seam to swap in
 * semantic retrieval later. Pure and unit-tested.
 */
object MemoryRetrieval {

    data class Item(val text: String, val updatedAt: Long)

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "to", "of",
        "in", "on", "for", "with", "you", "it", "my", "me", "that", "this", "do", "does",
        "how", "what", "why", "when", "where", "who", "can", "could", "should", "would",
        "will", "about", "as", "at", "be", "been", "have", "has", "had", "im", "your",
    )

    fun tokens(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOP }
            .toSet()

    /** Two tokens count as the same if equal or one is a prefix of the other with
     *  at least four shared characters, so "peanut" matches "peanuts" and simple
     *  plurals or tense variants line up without a full stemmer. */
    private fun matches(a: String, b: String): Boolean {
        if (a == b) return true
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        return short.length >= 4 && long.startsWith(short)
    }

    /**
     * The memories most worth injecting for [query], within [budgetChars] and at
     * most [max] entries. Overlap dominates so a directly relevant fact wins;
     * recency breaks ties and lets a few standing facts ride along on leftover
     * budget even with no overlap (a name or job matters regardless of the
     * question).
     */
    fun select(
        items: List<Item>,
        query: String,
        now: Long,
        budgetChars: Int,
        max: Int,
    ): List<String> {
        if (items.isEmpty() || budgetChars <= 0 || max <= 0) return emptyList()
        val q = tokens(query)
        val ranked = items.sortedByDescending { item ->
            val overlap = tokens(item.text).count { mt -> q.any { qt -> matches(mt, qt) } }
            val ageDays = (now - item.updatedAt).coerceAtLeast(0L) / 86_400_000.0
            val recency = 1.0 / (1.0 + ageDays)
            overlap * 10.0 + recency
        }
        val chosen = ArrayList<String>()
        var used = 0
        for (item in ranked) {
            if (chosen.size >= max) break
            val cost = item.text.length + 1
            if (used + cost > budgetChars) continue
            chosen += item.text
            used += cost
        }
        return chosen
    }
}
