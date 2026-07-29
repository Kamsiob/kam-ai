package com.kamsiob.kamai.llm

/**
 * Catches a reply that is really a piece of the prompt read back.
 *
 * Small on-device models reproduce the outputs of fixed worked examples
 * regardless of the input. This has been found five separate times in this app,
 * and every fix so far was a prompt change that reduced the rate without ending
 * it. This is the other half: regurgitation will happen at some rate whatever
 * the prompt says, so the goal is that a user never sees it.
 *
 * **The mistake this had to be corrected for.** A first version held only the
 * answer text. It then discarded a correct reply: "Remember that I always work
 * take the stairs." was answered "Noted, I will assume the stairs rather than the lift.", which is right,
 * and the guard threw it away because that sentence also appears in the prompt.
 *
 * An example answer is only wrong when it lands somewhere it does not belong. So
 * each one is stored with the input that makes it correct, and a reply is judged
 * against what the user actually said rather than in isolation. Guarding without
 * that context replaces one visible defect with another, and the replacement is
 * worse, because it destroys good answers silently and at random.
 */
object PromptEcho {

    /**
     * A line that exists verbatim in a prompt, and the message it is the right
     * answer to, when there is one.
     */
    data class Line(
        val answer: String,
        /**
         * The example input this answer belongs to. When the user's message says
         * essentially this, the answer is correct and must be left alone. Null
         * means the line is never a correct reply to anything.
         */
        val legitimateFor: String? = null,
    )

    /**
     * Kept in step with the prompts by [PromptEchoTest], which fails if one of
     * these stops appearing in SystemPrompts, so a prompt edit cannot leave the
     * guard defending a line nothing can copy any more.
     *
     * "Fix what? Tell me what is broken and I will start there." is deliberately
     * absent. It is correct wherever it lands, which is the property that makes
     * an example safe to keep in a prompt at all, and guarding it could only ever
     * throw away a right answer.
     */
    val lines: List<Line> = listOf(
        Line("Noted, I will assume the stairs rather than the lift.", legitimateFor = "Remember that I always take the stairs."),
        Line(
            "Third time in a day points at something repeatable rather than bad luck.",
            legitimateFor = "The install failed again, third time today.",
        ),
        // Format examples. None of these answers anything a user is likely to
        // ask, so any appearance is a copy.
        Line("It was finished in 1889, for the Paris World's Fair."),
        Line("Hold the side button for ten seconds."),
        Line("An external drive, cheapest per gigabyte."),
        Line("A bigger internal card, faster but dearer."),
        Line("Cloud, which needs a connection."),
        // Logic Partner's worked argument: three paragraphs of finished reply in
        // a prompt, the largest quotable block in any mode, and the cause of #114.
        Line("The case is real: early on you change direction faster than tests keep up"),
        Line("That assumes tests cost more time than bugs do"),
        Line("So how would you know you had crossed that line?"),
    )

    val protected: List<String> get() = lines.map { it.answer }

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

    /**
     * The minimum evidence before acting.
     *
     * Raised from twelve after a run abandoned a reply on a short opening that
     * merely began like an example. Discarding a good answer is not a smaller
     * failure than showing a copied one; it is the same failure pointed the other
     * way, and it is harder to notice because nothing looks wrong.
     */
    private const val MIN_CHARS = 24

    /** True when the user asked for exactly what this example demonstrates. */
    private fun Line.isAnsweringItsOwnExample(userMessage: String?): Boolean {
        val legitimate = legitimateFor ?: return false
        val said = normalize(userMessage.orEmpty())
        if (said.isEmpty()) return false
        val example = normalize(legitimate)
        return said == example || said.contains(example) || example.contains(said)
    }

    /** True when [reply] is one of the protected lines, or opens with one. */
    fun isEcho(reply: String, userMessage: String? = null): Boolean {
        val n = normalize(reply)
        if (n.length < MIN_CHARS) return false
        return lines.any { line ->
            val answer = normalize(line.answer)
            (n == answer || n.startsWith(answer)) && !line.isAnsweringItsOwnExample(userMessage)
        }
    }

    /**
     * True when what has streamed so far can only be heading into a protected
     * line, so a reply can be abandoned before it finishes rather than after.
     *
     * Early detection is what makes this affordable: the alternative is buffering
     * every reply until it completes, which would hand back the time to first
     * token that #38 spent the whole performance budget winning.
     */
    fun couldBecomeEcho(partial: String, userMessage: String? = null): Boolean {
        val n = normalize(partial)
        if (n.length < MIN_CHARS) return false
        return lines.any { line ->
            val answer = normalize(line.answer)
            (answer.startsWith(n) || n.startsWith(answer)) && !line.isAnsweringItsOwnExample(userMessage)
        }
    }

    /**
     * How much contiguous prompt text has to appear in a reply before it counts
     * as the prompt rather than a coincidence.
     *
     * Long on purpose. Short phrases from a prompt written in plain English do
     * turn up in ordinary answers, because the prompt tells the model how to
     * write and the model writes that way. Forty-eight characters of an exact
     * run is not a house style, it is a copy.
     */
    /**
     * Prompt text that is a correct reply wherever it lands.
     *
     * The clarifying question is the one example held to be safe: any message too
     * short to act on deserves it, so emitting it verbatim is never wrong. It is
     * absent from [lines] for that reason, and it has to be absent here too.
     *
     * Missing that cost a run. [containsPromptText] was added later and knew
     * nothing about the exemption, so it caught the one sentence deliberately
     * allowed and "fix" started answering with the fallback instead of the right
     * question. A rule with an exception needs the exception applied everywhere
     * the rule is, and this one had two places.
     */
    private val ALWAYS_ALLOWED = listOf(
        "Fix what? Tell me what is broken and I will start there.",
    )

    private fun isAllowedOutright(normalizedReply: String): Boolean =
        ALWAYS_ALLOWED.any { allowed ->
            val a = normalize(allowed)
            normalizedReply == a || a.startsWith(normalizedReply) || normalizedReply.startsWith(a)
        }

    /**
     * How much contiguous instruction text a reply must contain before it counts
     * as reciting rather than obeying.
     *
     * Lowered to 36 once and put back. The instructions tell the model how to
     * write, partly in the words it should use: "say when you are unsure or might
     * be wrong, and that it is worth checking and bookmarking". A reply that does
     * exactly that shares a forty-character run with the prompt, so at 36 the
     * guard would have discarded answers for following their own instructions.
     *
     * The case this was lowered for, a reply that recited the format examples in
     * a different order, is not reachable at any safe threshold: its longest
     * exact run is 25 characters. It is caught by [startsWithRoleMarker] instead.
     * Better a check that is sure about less than one that is wrong about more.
     */
    private const val PROMPT_RUN = 48

    /**
     * True when [reply] contains a long verbatim run of [systemPrompt].
     *
     * The list above defends specific lines somebody thought of in advance, and
     * that turned out to be far too narrow. Asked "What model are you built on
     * and who trained you?", the model answered with the system prompt itself,
     * starting "You are Kam AI, running entirely on the user's phone" and
     * continuing through the voice rules. None of that was in the list, because
     * nobody had imagined the rules being read aloud rather than an example
     * being copied.
     *
     * This needs no list. It compares the reply against the instructions that
     * were actually sent, so any part of them coming back is caught, including
     * the parts added tomorrow.
     */
    fun containsPromptText(reply: String, systemPrompt: String): Boolean {
        val haystack = normalize(systemPrompt)
        val n = normalize(reply)
        if (haystack.isEmpty() || n.length < MIN_CHARS) return false
        if (isAllowedOutright(n)) return false
        if (n.length <= PROMPT_RUN) return haystack.contains(n)
        // Step through rather than checking only the opening: a reply that starts
        // in its own words and then recites is still reciting.
        var i = 0
        while (i + PROMPT_RUN <= n.length) {
            if (haystack.contains(n.substring(i, i + PROMPT_RUN))) return true
            i += PROMPT_RUN / 2
        }
        return false
    }

    /** The streaming form, so a recital is cut off rather than shown in full. */
    fun couldBecomePromptText(partial: String, systemPrompt: String): Boolean {
        val n = normalize(partial)
        if (n.length < PROMPT_RUN) return false
        if (isAllowedOutright(n)) return false
        return normalize(systemPrompt).contains(n.substring(0, PROMPT_RUN))
    }

    /**
     * Role markers a chat template uses. A reply that opens with one of these is
     * the model writing the transcript rather than a turn in it.
     */
    private val ROLE_OPENERS = listOf("user", "model", "assistant", "system")

    /**
     * True when the reply begins by announcing a speaker.
     *
     * Seen in Logic Partner: a reply that opened with a bare "user" line followed
     * by an invented message, and then recited the prompt's format examples. The
     * text after it was different every time, so nothing matching against the
     * prompt could be relied on to catch it, but the opening is always the same
     * few words and is never a legitimate way to start an answer.
     */
    fun startsWithRoleMarker(reply: String): Boolean {
        val firstLine = reply.trimStart().lineSequence().firstOrNull()?.trim().orEmpty()
        val bare = firstLine.trimEnd(':').lowercase()
        return bare in ROLE_OPENERS
    }

    /**
     * Why a reply was rejected, in enough detail to debug from a log.
     *
     * A guard that cannot explain itself cannot be debugged. The first version
     * logged only that something matched, and when a reply was rejected that
     * should not have been, there was no way to tell which of four checks did it
     * or on what text. That cost a night of guessing.
     */
    data class Reason(val check: String, val matched: String)

    /** The reason [isBadReply] would return true, or null when it would not. */
    fun reasonFor(reply: String, systemPrompt: String, userMessage: String?): Reason? {
        val n = normalize(reply)
        if (n.isEmpty()) return null
        if (isAllowedOutright(n)) return null
        if (isLegitimateExampleAnswer(n, userMessage)) return null

        lines.firstOrNull { line ->
            val answer = normalize(line.answer)
            (n == answer || n.startsWith(answer)) && !line.isAnsweringItsOwnExample(userMessage)
        }?.let { return Reason("example-answer", it.answer.take(60)) }

        if (startsWithRoleMarker(reply)) {
            return Reason("role-marker", reply.trimStart().lineSequence().firstOrNull().orEmpty().take(40))
        }
        if (isParrot(reply, userMessage)) {
            return Reason("parrot", n.take(60))
        }
        if (isRestatement(reply, userMessage)) {
            return Reason("restatement", n.take(60))
        }
        promptRunIn(reply, systemPrompt)?.let { return Reason("prompt-run", it) }
        return null
    }

    /** The exact run of instruction text a reply reproduced, for the log. */
    private fun promptRunIn(reply: String, systemPrompt: String): String? {
        val haystack = normalize(systemPrompt)
        val n = normalize(reply)
        if (haystack.isEmpty() || n.length < MIN_CHARS) return null
        if (n.length <= PROMPT_RUN) return if (haystack.contains(n)) n else null
        var i = 0
        while (i + PROMPT_RUN <= n.length) {
            val w = n.substring(i, i + PROMPT_RUN)
            if (haystack.contains(w)) return w
            i += PROMPT_RUN / 2
        }
        return null
    }

    /**
     * The one question the reply path asks, so every exemption is applied once.
     *
     * The checks grew one at a time and the exemptions did not follow them. The
     * clarifying question is allowed to be emitted verbatim, and an example
     * answer is allowed when it lands on the message it belongs to, and both of
     * those were honoured by the check they were written for and ignored by the
     * two added afterwards. Twice that reached the device as a correct reply
     * being thrown away and replaced with "That came out wrong."
     *
     * One entry point, exemptions first, detectors after. A new detector added
     * below inherits the exemptions instead of forgetting them.
     */
    fun isBadReply(reply: String, systemPrompt: String, userMessage: String?): Boolean {
        val n = normalize(reply)
        if (n.isEmpty()) return false
        if (isAllowedOutright(n)) return false
        if (isLegitimateExampleAnswer(n, userMessage)) return false

        return isEcho(reply, userMessage) ||
            containsPromptText(reply, systemPrompt) ||
            isParrot(reply, userMessage) ||
            isRestatement(reply, userMessage) ||
            startsWithRoleMarker(reply)
    }

    /** The streaming form of [isBadReply], for abandoning a reply part way. */
    fun couldBecomeBadReply(partial: String, systemPrompt: String, userMessage: String?): Boolean {
        val n = normalize(partial)
        if (n.isEmpty()) return false
        if (isAllowedOutright(n)) return false
        if (isLegitimateExampleAnswer(n, userMessage)) return false

        return couldBecomeEcho(partial, userMessage) ||
            couldBecomePromptText(partial, systemPrompt)
    }

    /** True when this reply is an example answer landing on its own example input. */
    private fun isLegitimateExampleAnswer(normalizedReply: String, userMessage: String?): Boolean =
        lines.any { line ->
            val answer = normalize(line.answer)
            line.isAnsweringItsOwnExample(userMessage) &&
                (normalizedReply.startsWith(answer) || answer.startsWith(normalizedReply))
        }

    /**
     * Words too common to carry meaning, so an overlap made only of these is not
     * an overlap worth acting on.
     */
    private val FUNCTION_WORDS = setOf(
        "a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "for",
        "from", "has", "have", "how", "i", "if", "in", "is", "it", "its", "of",
        "on", "or", "should", "so", "that", "the", "their", "them", "then",
        "there", "they", "this", "to", "was", "were", "what", "when", "which",
        "will", "with", "you", "your",
    )

    /**
     * Crudely reduced to a stem, so "ovens" and "oven" count as the same word.
     *
     * Without this, "Hot ovens are needed for bread." shares two words with
     * "Bread needs a hot oven" instead of four, and a restatement escapes on
     * grammar alone. Deliberately dumb: this is comparing a reply to one message,
     * not indexing a corpus, and a wrong stem costs a percentage point on one
     * ratio.
     */
    private fun stem(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith("es") -> word.dropLast(2)
        word.length > 3 && word.endsWith("ed") -> word.dropLast(2)
        word.length > 4 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    private fun contentWords(text: String): List<String> =
        normalize(text).split(" ")
            .filter { it.isNotBlank() && it !in FUNCTION_WORDS }
            .map { stem(it) }

    /**
     * How much of a reply has to be the user's own content words before it counts
     * as a restatement rather than a reply.
     *
     * Calibrated against replies already judged good and bad on the device, not
     * picked. "It needs a hot oven, around 230C." shares every content word with
     * the message and is a restatement. "It needs a high temperature to set the
     * crust properly." shares one in six and is a reply. "It boils at 100 degrees
     * Celsius at standard atmospheric pressure." shares four in seven and is
     * acceptable, which is what puts the line above that rather than below it.
     */
    private const val RESTATEMENT_OVERLAP = 0.65

    /**
     * True when the reply says the user's own content back in different words
     * (#122).
     *
     * [isParrot] catches a copy, by containment, and cannot catch this: "Bread
     * needs a hot oven, around 230C." answered "It needs a hot oven, around
     * 230C." is not contained in the message, because the first word changed.
     *
     * Length is part of the test on purpose. A long answer that happens to reuse
     * the subject's vocabulary is answering; a reply about as long as the message
     * and made of the same words is repeating.
     */
    fun isRestatement(reply: String, userMessage: String?): Boolean {
        val said = contentWords(userMessage.orEmpty())
        val back = contentWords(reply)
        if (said.size < 3 || back.size < 3) return false
        // Not much longer than the message. An answer that adds substance is
        // longer than what it answers, and this is looking for one that does not.
        if (back.size > said.size * 3 / 2) return false
        val shared = back.count { it in said.toSet() }
        return shared.toDouble() / back.size >= RESTATEMENT_OVERLAP
    }

    /**
     * True when the reply is really the user's own message handed back (#122).
     *
     * A third shape of the same underlying behavior. The model reproduces its
     * prompt, and the user's message is the most recent part of that prompt, so
     * it reproduces that too: "Bread needs a hot oven, around 230C." answered
     * with "Bread needs a hot oven, around 230C."
     *
     * Deliberately strict. Quoting a few of somebody's words back is normal and
     * often good writing, so this fires only when the reply is essentially the
     * whole message and nothing else. A reply that repeats the question and then
     * answers it is clumsy, not broken, and is left alone.
     */
    fun isParrot(reply: String, userMessage: String?): Boolean {
        val said = normalize(userMessage.orEmpty())
        val n = normalize(reply)
        if (said.length < MIN_CHARS || n.length < MIN_CHARS) return false
        if (n == said) return true
        // One wholly inside the other, and almost all of it: catches a copy that
        // dropped the final full stop or added a leading word.
        val (shorter, longer) = if (n.length <= said.length) n to said else said to n
        return longer.contains(shorter) && shorter.length.toDouble() / longer.length >= 0.9
    }
}
