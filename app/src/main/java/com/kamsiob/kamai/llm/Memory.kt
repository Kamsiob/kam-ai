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
}
