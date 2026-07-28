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
    /**
     * How much of [next] is list punctuation before any words start.
     *
     * A continuation often resumes by starting the bullet again, so the repeat
     * arrives as "- Inspect the engine bay" against text ending "Inspect the".
     * The overlap check only looks at the first character of [next], so the
     * marker hid the repeat entirely and the reader got "Inspect the - Inspect
     * the engine bay". Seen on the phone after stopping mid-list and tapping
     * Continue.
     */
    fun leadingMarker(next: String): Int {
        var i = 0
        while (i < next.length && (next[i].isWhitespace() || next[i] in MARKERS)) i++
        // Only a marker if words follow it. A continuation that is nothing but
        // punctuation is not a restart, and dropping it would be a guess.
        return if (i in 1 until next.length) i else 0
    }

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
        // Try past a leading bullet or dash first, and only accept that reading if
        // it actually reveals a repeat. Otherwise the marker is real formatting
        // and stays.
        val marker = leadingMarker(next)
        val afterMarker = if (marker > 0) overlap(existing, next.substring(marker)) else 0
        val (dropped, repeated) = if (afterMarker > 0) {
            marker + afterMarker to afterMarker
        } else {
            0 to overlap(existing, next)
        }
        val rest = next.substring(if (afterMarker > 0) dropped else repeated)
        if (rest.isEmpty()) return existing
        return if (needsSpace(existing, rest)) "$existing $rest" else existing + rest
    }

    /** Characters a list item can start with, before its words. */
    private const val MARKERS = "-*\u2022\u00b7"

    private const val MAX_OVERLAP = 40
    private const val MIN_OVERLAP = 2
}
