package com.kamsiob.kamai.llm

/**
 * Catches Brainstorm reading its own method selection out to the user (#58,
 * #114, #130).
 *
 * The mode picks one of eleven methods silently and then runs it. Twice on the
 * device it announced the choice instead: "Only a topic or problem, no idea yet,
 * or one vague idea. I'll use STARBURSTING." That is selection rule 2 recited,
 * condition and label together.
 *
 * **Why this is in code and not in the prompt.** The prompt taught the shape with
 * two worked openings, and a small model copies a worked example whether or not
 * the moment is right, which is #130: the openings came back verbatim on
 * unrelated problems. Both cannot be fixed in the prompt, because the examples
 * are simultaneously what prevents announcing and what causes copying. So the
 * half that can be repaired after the fact is repaired after the fact.
 * Announcing a method is a formatting failure, visible in the text and
 * removable from it. Copying is a content failure that no amount of
 * post-processing can turn into an answer.
 *
 * **Why this does not reuse the echo guard's matching.** [PromptEcho] compares a
 * reply against the prompt by overlap, which is the right tool for "this is the
 * instructions read aloud" and the wrong one here: Brainstorm is *required* to
 * say in one sentence what it is about to do, so "describing what it is about to
 * do" cannot be the trigger without rejecting the mode's own contract. What is
 * banned is narrower and closed: naming the method, or shouting it in capitals.
 * So this matches a finite list of names, and only inside a construction that
 * announces one.
 */
object MethodAnnouncement {

    /**
     * The eleven method labels as the prompt writes them, plus the named
     * techniques a model reaches for once it has decided to announce something.
     *
     * A closed list is the point. Anything open enough to catch a method nobody
     * listed is open enough to catch a reply that is doing its job.
     */
    private val LABELS = listOf(
        "timed dump", "six questions", "core and branches", "systematic variation",
        "eight fast ideas", "inversion", "assumption reversal", "deliberately bad ideas",
        "one lens at a time", "structural analogy", "the ideal, worked back",
        "starbursting", "scamper", "six thinking hats", "mind map", "mind-map",
        "brainwriting", "five whys", "lateral thinking", "fishbone", "swot analysis",
    )

    /**
     * Phrases that turn a name into an announcement.
     *
     * Requiring one of these is what keeps ordinary usage alive. "Inversion" is a
     * real word, and a reply that happens to contain it while doing the work is
     * not announcing anything. "Let's use inversion" is.
     */
    private val ANNOUNCERS = listOf(
        "i'll use", "i will use", "we'll use", "we will use", "let's use", "lets use",
        "i'm going to use", "i am going to use", "we can use", "we're going to use",
        "we are going to use", "i'll try", "let's try", "i'll run", "we'll run",
        "let's run", "i'll do", "let's do", "we'll do", "using the", "apply the",
        "this is called", "a technique called", "the method is", "the technique is",
        "known as", "i'll apply", "called the",
    )

    /** A method label shouted, which the prompt bans in those words. */
    private val SHOUTED = Regex("\\b[A-Z][A-Z]{3,}\\b")

    /** Acronyms that are not method names and must not trip [SHOUTED]. */
    private val NOT_SHOUTING = setOf("HTML", "JSON", "HTTP", "HTTPS", "JPEG", "WIFI", "USB", "PDF")

    /**
     * The offending text when this reply announces a method, otherwise null.
     *
     * Returns what matched rather than a boolean so every rejection can say what
     * it was, which is the thing missing from guards that regenerate silently.
     */
    fun matched(reply: String): String? {
        val lower = reply.lowercase()
        for (label in LABELS) {
            if (!lower.contains(label)) continue
            for (announcer in ANNOUNCERS) {
                val at = lower.indexOf(announcer)
                if (at < 0) continue
                // Same sentence, roughly: an announcement and its name sit
                // together, and a label paragraphs away from an announcer is a
                // coincidence rather than a construction.
                if (kotlin.math.abs(lower.indexOf(label) - at) <= 60) return "$announcer ... $label"
            }
        }
        SHOUTED.findAll(reply).forEach { m ->
            if (m.value !in NOT_SHOUTING) return m.value
        }
        return null
    }

    /**
     * The reply with the announcing sentence removed, or null when nothing
     * usable is left.
     *
     * Rewriting is preferred to regenerating because the rest of the reply is
     * usually sound: the announcement is one sentence in front of a question
     * built out of what the user actually said, and throwing that away to roll
     * the dice again costs the user a minute and often returns something worse.
     *
     * Null when the remainder carries no question, because Brainstorm's contract
     * is one thing you are doing and one question. A reply that has lost its
     * question is not a shorter reply, it is a different mode.
     */
    fun strip(reply: String): String? {
        val sentences = split(reply)
        val kept = sentences.filter { matched(it) == null }
        if (kept.isEmpty()) return null
        val rebuilt = kept.joinToString(" ") { it.trim() }.trim()
        if (rebuilt.isEmpty() || !rebuilt.contains("?")) return null
        return rebuilt
    }

    /**
     * Sentences, keeping their terminators.
     *
     * Newlines end a sentence too. The announcement is often its own line rather
     * than its own sentence, as in "Only a topic, no idea yet.\nI'll use
     * STARBURSTING.\nWhat do you want to achieve?"
     */
    private fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            current.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                if (current.isNotBlank()) out.add(current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotBlank()) out.add(current.toString())
        return out
    }
}
