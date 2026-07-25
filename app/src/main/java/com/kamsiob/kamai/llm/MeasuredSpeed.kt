package com.kamsiob.kamai.llm

/**
 * The speed a model actually runs at on *this* phone (item 22).
 *
 * The model picker could tell people a size and a name and nothing about what
 * they would feel. A number measured on the developer's device and shipped in a
 * table would be worse than nothing, because phones differ by more than the
 * models do: the same tier that decodes at eleven tokens a second here might do
 * half that elsewhere, and a confident wrong number is the thing this app is
 * built not to do.
 *
 * So it is measured where it matters. Every generation long enough to mean
 * something updates a running average for the model that produced it, and the
 * picker says nothing at all about a model nobody has run yet.
 */
object MeasuredSpeed {

    /** Stored as "tokensPerSecond|samples". */
    fun encode(tokensPerSecond: Double, samples: Int): String = "$tokensPerSecond|$samples"

    fun decode(stored: String?): Pair<Double, Int>? {
        val parts = stored?.split("|") ?: return null
        if (parts.size != 2) return null
        val tps = parts[0].toDoubleOrNull() ?: return null
        val samples = parts[1].toIntOrNull() ?: return null
        if (tps <= 0 || samples <= 0) return null
        return tps to samples
    }

    /**
     * Folds a new measurement into the stored average.
     *
     * A plain running mean over the last [CAP] samples' worth of weight, so a
     * single throttled run cannot dominate and a phone that has warmed up over
     * months still reflects how it behaves now.
     */
    fun fold(stored: String?, tokensPerSecond: Double): String {
        val previous = decode(stored)
            ?: return encode(tokensPerSecond, 1)
        val (average, samples) = previous
        val weight = samples.coerceAtMost(CAP)
        val next = (average * weight + tokensPerSecond) / (weight + 1)
        return encode(next, (samples + 1).coerceAtMost(CAP))
    }

    /**
     * How to say it, or null when there is nothing honest to say yet.
     *
     * Words rather than tokens, because nobody outside this codebase thinks in
     * tokens. An English token averages about three quarters of a word, which is
     * an approximation and is why the sentence says "about".
     *
     * One sample is a measurement, not a speed: it could have been a cold start
     * or a moment of throttling. The picker stays quiet until there are two.
     */
    fun describe(stored: String?): String? {
        val (tps, samples) = decode(stored) ?: return null
        if (samples < 2) return null
        val words = (tps * WORDS_PER_TOKEN).toInt().coerceAtLeast(1)
        return "About $words words a second on this phone"
    }

    private const val WORDS_PER_TOKEN = 0.75
    private const val CAP = 20
}
