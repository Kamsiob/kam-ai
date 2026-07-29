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

    /** A normalized form for near-duplicate detection: lowercase, alphanumerics
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
     * The words a fact can be "about the user" in a way that governs every reply,
     * as opposed to being about a thing, a place, or an event they mentioned once.
     *
     * Anchored at the start of the fact rather than searched for anywhere in it,
     * and that anchoring is the whole reason this is safe. Stored facts are
     * third-person predicates about the person: "works as a nurse", "lives in
     * Leeds", "name is Kam". A topical fact that happens to contain the same words
     * does not begin with them: "the user's rowing club is called Verity Quay" is
     * about a club, and a contains-check on "is called" would have called it an
     * identity fact and injected it into every message forever. That memory is a
     * real one from the #133 probes, and it is the case this list is tested against.
     */
    private val STANDING_PREFIXES = listOf(
        // Naming. "name is" and "is named" require the naming word; "is called"
        // deliberately is not here, because it attaches to anything.
        "name is", "is named", "goes by", "prefers to be called",
        // Occupation and study, by explicit marker only. "is a ..." is not a
        // marker: "is a vegetarian" and "is a big cricket fan" are topical.
        "works as", "works at", "works in", "job is", "is retired",
        "is a student", "is studying", "studies", "teaches",
        // Where they are, which bears on dates, units, spelling and services.
        "lives in", "based in", "is from",
        // What they read and write in.
        "speaks", "first language", "native language",
    )

    /**
     * Facts that identify the person or govern how they must be addressed. Safe to
     * look for anywhere in the text, because none of these can be about a rowing
     * club: no topical fact contains "they/them" or "screen reader" by accident.
     */
    private val STANDING_ANYWHERE = listOf(
        "pronoun", "they/them", "she/her", "he/him",
        "dyslexi", "screen reader", "hard of hearing", "low vision",
        "colour blind", "color blind", "autistic", "adhd",
        // How every answer has to be expressed, whatever it is about. These are
        // safe anywhere because a stored fact does not mention the units or the
        // spelling it wants unless that is what the fact is for. The real memory
        // "the user always works in metric units" is why the verb list alone is
        // not enough: it is a standing fact phrased without a preference verb.
        "metric", "imperial", "celsius", "fahrenheit", "24-hour clock",
        "american spelling", "british spelling",
    )

    /**
     * A preference is standing only when it is a preference about *the answer*.
     * "prefers plain language" changes every reply; "prefers oat milk" changes a
     * reply about coffee and nothing else. The verb alone is not enough, and
     * treating it as enough is how a floor stops being a floor.
     */
    private val ANSWER_STYLE = listOf(
        "plain", "simple", "short", "brief", "concise", "detail", "direct",
        "blunt", "formal", "informal", "casual", "bullet", "list", "jargon",
        "technical", "example", "analog", "metric", "imperial", "celsius",
        "fahrenheit", "mile", "kilometre", "kilometer", "24-hour", "swear",
        "emoji", "spelling", "american", "british",
    )
    private val PREFERENCE_VERBS = listOf(
        "prefers", "likes", "dislikes", "hates", "wants", "does not like",
        "doesn't like", "asks for", "needs",
    )

    /**
     * Whether a memory bears on a reply regardless of what the message is about.
     *
     * This is the rule that lets standing facts ride along with no word overlap,
     * expressed as code so it can be tested rather than left to the model (#133).
     * Deliberately narrow: over-firing here quietly restores the old
     * inject-everything behavior, and under-firing loses a name on an unrelated
     * question, so the tests cover both directions.
     */
    fun isStanding(text: String): Boolean {
        val t = text.lowercase().trim()
        if (STANDING_ANYWHERE.any { it in t }) return true
        // Strip a leading subject so "the user's name is Kam" and "name is Kam"
        // are the same fact. Only the subject goes: the possessive in "the user's
        // rowing club" leaves "rowing club is called ...", which is the point.
        var body = t
            .removePrefix("the user's ").removePrefix("the user ")
            .removePrefix("user's ").removePrefix("user ")
            .removePrefix("they ").removePrefix("i ").removePrefix("my ")
            .trim()
        // "always works in metric units" and "usually prefers short answers" are
        // the same facts as without the adverb. Stripped after the subject so
        // "the user always ..." reaches the same place.
        for (adverb in listOf("always ", "usually ", "generally ", "normally ", "often ")) {
            body = body.removePrefix(adverb)
        }
        if (STANDING_PREFIXES.any { body.startsWith(it) }) return true
        return PREFERENCE_VERBS.any { body.startsWith(it) } &&
            ANSWER_STYLE.any { it in body }
    }

    /**
     * The memories worth injecting for [query], within [budgetChars] and at most
     * [max] entries.
     *
     * **There is a floor, and a memory below it is not injected at all** (#133).
     * A memory qualifies two ways and no others: it shares a word with the message,
     * or [isStanding] recognizes it as a fact that bears on any reply. Everything
     * else is dropped even when there is room for it.
     *
     * This used to rank and never filter, so twelve stored facts meant twelve facts
     * in front of the model on every message. That cost ten percent of a 4096 token
     * window on the tier that can least afford it, and it made the app tell a user
     * it had used something it remembered about them underneath an answer about a
     * coffee stain. The prompt already asked the model to "use them only when
     * relevant", which is the wrong way round: the cheap deterministic filter was
     * skipped in favor of the expensive unreliable one.
     *
     * Relevant memories fill the budget first, best overlap first. Standing facts
     * then ride along on what is left, which keeps the behavior that was decided
     * on deliberately: a name or a job matters regardless of the question.
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
        fun overlapOf(item: Item) =
            tokens(item.text).count { mt -> q.any { qt -> matches(mt, qt) } }
        fun recencyOf(item: Item): Double {
            val ageDays = (now - item.updatedAt).coerceAtLeast(0L) / 86_400_000.0
            return 1.0 / (1.0 + ageDays)
        }

        val relevant = items.filter { overlapOf(it) > 0 }
            .sortedByDescending { overlapOf(it) * 10.0 + recencyOf(it) }
        // Standing facts are the fallback, not competition for the relevant ones,
        // so they are appended rather than merged into one ranking. A standing
        // fact that also overlaps is already in the first list; excluding it here
        // is what stops it being injected twice.
        val standing = items.filter { overlapOf(it) == 0 && isStanding(it.text) }
            .sortedByDescending { recencyOf(it) }
        val ranked = relevant + standing

        val chosen = ArrayList<Item>()
        var used = 0
        for (item in ranked) {
            if (chosen.size >= max) break
            val cost = item.text.length + 1
            if (used + cost > budgetChars) continue
            chosen += item
            used += cost
        }
        // Ranking decides *which* memories go in. It must not decide the order
        // they are written in, and that distinction is the whole of this line.
        //
        // These are injected into the system block, and the system block is the
        // KV cache prefix that time to first token depends on being byte
        // identical between turns (#38, #52): about 35 tokens of prefill against
        // 863. Ranking depends on word overlap with the current message, so two
        // turns of one conversation presented the same memories in a different
        // order, the block was different text, and the prefix was missed every
        // turn. Nothing measured it because the batteries open a fresh
        // conversation per message, and a fresh conversation has no prefix to
        // reuse.
        //
        // Newest first, which is stable for a given set and is the order somebody
        // would expect if they ever saw it.
        return chosen.sortedByDescending { it.updatedAt }.map { it.text }
    }
}
