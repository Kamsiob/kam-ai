package com.kamsiob.kamai.llm

/**
 * Whether a continued answer needs a space where it was joined back together.
 *
 * An answer interrupted by process death is stored through `cleanOutput`, which
 * trims it, so "...from inside the box to the " loses its trailing space.
 * Continuing then appends the next generation directly and the reader gets
 * "to theoutside". Seen on the phone after killing the app mid-answer and
 * tapping Continue.
 *
 * The stored text cannot tell us whether it stopped between words or inside one,
 * because the space that would have said so is exactly what was trimmed. So this
 * is a judgement, and it goes the way that is right far more often: a model told
 * to "carry straight on" begins at a word, not in the middle of one.
 */
object ContinuationJoin {

    /** Punctuation that attaches to the word before it, so must not be pushed off. */
    private const val ATTACHING = ",.;:!?)]}'\"’”…%"

    fun needsSpace(existing: String, next: String): Boolean {
        if (existing.isEmpty() || next.isEmpty()) return false
        val end = existing.last()
        val start = next.first()
        if (end.isWhitespace() || start.isWhitespace()) return false
        // Closing punctuation, and an opening bracket or quote is fine to push
        // off the previous word but reads wrong glued to it, so it gets a space.
        if (start in ATTACHING) return false
        return true
    }

    /**
     * The overlap to drop, when the continuation starts by repeating how the
     * previous text ended.
     *
     * A model told to carry on from "...sea levels. They" tends to read the
     * dangling word as a false start and begin again: "They're caused by...".
     * Joined naively that reads "sea levels. They They're caused by...". Seen on
     * the phone.
     *
     * The overlap has to start at a word boundary in [existing] and run to the
     * end of it, so this only fires on a genuine restart of the last word or
     * words, not on any old coincidence of letters.
     */
    fun overlap(existing: String, next: String): Int {
        if (existing.isEmpty() || next.isEmpty()) return 0
        val tail = existing.takeLast(MAX_OVERLAP)
        // Longest first, so "They" wins over "T" when both match.
        for (length in tail.length downTo MIN_OVERLAP) {
            val candidate = tail.takeLast(length)
            val startsAtWordBoundary = existing.length == length ||
                !existing[existing.length - length - 1].isLetterOrDigit()
            if (!startsAtWordBoundary) continue
            if (next.startsWith(candidate, ignoreCase = true)) return length
        }
        return 0
    }

    /** [existing] and [next] joined the way a reader would expect. */
    fun join(existing: String, next: String): String {
        val repeated = overlap(existing, next)
        val rest = next.substring(repeated)
        if (rest.isEmpty()) return existing
        return if (needsSpace(existing, rest)) "$existing $rest" else existing + rest
    }

    private const val MAX_OVERLAP = 40
    private const val MIN_OVERLAP = 2
}
