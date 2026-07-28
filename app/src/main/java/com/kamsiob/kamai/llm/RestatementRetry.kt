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
     */
    fun instruction(userMessage: String): String? {
        if (userMessage.isBlank()) return null
        return "They stated something rather than asking a question. Reply with one " +
            "sentence carrying information their sentence did not: why it is so, what " +
            "follows from it, what it rules out, or what to watch for. Their own words " +
            "back, in any arrangement, is not a reply."
    }
}
