package com.kamsiob.kamai.ui.followups

/**
 * How a saved item is split into a heading and the rest.
 *
 * A follow-up has one piece of text. The card shows a short heading and then the
 * snippet under it, which reads well for a saved paragraph and badly for a saved
 * sentence: "History of navigation" appeared as both the heading and the body of
 * the same card, the same words twice, looking like a rendering bug.
 */
object FollowUpText {

    private const val HEADING_CHARS = 60

    /** The heading: the first line, shortened. */
    fun heading(snippet: String): String =
        snippet.lineSequence().firstOrNull()?.take(HEADING_CHARS)?.trim().orEmpty()
            .ifBlank { "Bookmarked note" }

    /**
     * The text to show under the heading, or null when there is none worth
     * showing.
     *
     * Null when the heading already is the whole thing, which is the case for
     * every saved item short enough to fit in one line. Everything longer keeps
     * both, since then the heading really is a summary of something.
     */
    fun body(snippet: String): String? {
        val trimmed = snippet.trim()
        return trimmed.takeIf { it.isNotEmpty() && it != heading(snippet) }
    }
}
