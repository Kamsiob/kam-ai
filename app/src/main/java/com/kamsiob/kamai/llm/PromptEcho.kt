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
 * in metric units." was answered "Noted, I will keep to metric.", which is right,
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
        Line("Noted, I will keep to metric.", legitimateFor = "Remember that I always work in metric units."),
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
}
