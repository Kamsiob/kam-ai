package com.kamsiob.kamai.llm

/**
 * What to tell the model on the second attempt, after the first came back as the
 * user's own sentence (#122).
 *
 * The retry used to be the same prompt drawn again. Sampling is seeded randomly
 * so that is a genuinely different draw, but on the input this defect is named
 * for the model restates on most attempts, which makes a plain re-roll a slow way
 * of arriving at the same place. Two draws and then a fallback asking somebody to
 * rephrase is honest and useless: they said a perfectly clear thing and are being
 * asked to say it again.
 *
 * So the second attempt is told what to do instead. The instruction is a shape
 * rather than a prohibition, which is the lever that has repeatedly worked on
 * this model where prohibitions have not: see DECISIONS.md, "a small model
 * follows a shape and ignores a condition".
 *
 * It goes in the pending instruction, after the cached instruction block, so it
 * costs nothing in prefix reuse. The system prompt is the KV cache prefix and
 * varying it per request would cost far more than this defect does.
 */
object RestatementRetry {

    /**
     * The nudge, or null when there is nothing to nudge about.
     *
     * Null for an empty message, because the instruction talks about "what they
     * said" and there is no sense in saying that about nothing.
     *
     * **Null for the modes that have their own reply shape**, which was found by
     * measuring rather than by thinking about it. The first version applied
     * everywhere, and Logic Partner's replies to a weak argument collapsed into
     * one bland line: "Productivity differences are often tied to the specific
     * nature of the work and the individual's environment." That is this
     * instruction being obeyed exactly, and it is not what Logic Partner is for.
     * Its contract is the argument at its strongest, then its weakest link, then
     * the question that would settle it, and no single additive sentence can be
     * all three.
     *
     * So this belongs only where a one line reply to a statement is the right
     * answer, which is General and the quick panel.
     */
    fun instruction(userMessage: String, mode: com.kamsiob.kamai.data.Mode): String? {
        if (mode != com.kamsiob.kamai.data.Mode.GENERAL &&
            mode != com.kamsiob.kamai.data.Mode.OVERLAY
        ) {
            return null
        }
        if (userMessage.isBlank()) return null
        return "They stated something rather than asking a question. Reply with one " +
            "sentence carrying information their sentence did not: why it is so, what " +
            "follows from it, what it rules out, or what to watch for. Their own words " +
            "back, in any arrangement, is not a reply."
    }
}
