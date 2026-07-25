package com.kamsiob.kamai.ui.followups

/**
 * Splits a saved item into what to show as its heading and what to show under
 * it, without printing the same words twice.
 *
 * Found on the phone twice. First a follow-up reading "History of navigation"
 * appeared as both the heading and the body of its own card, because the heading
 * was the first sixty characters and the body was everything. That was fixed by
 * dropping the body when it equalled the heading, which left the second case:
 * save a paragraph and the card shows its first sixty characters in bold and
 * then those same sixty characters again at the start of the body. A saved
 * excerpt from an answer is always a paragraph, so that case became the common
 * one as soon as excerpts could be saved.
 *
 * The rule is that a heading has to be a real one. A short first line is a
 * title, and what follows it is the body. A paragraph that simply starts is not
 * a title however it is cut, so it gets no heading and is shown as itself.
 */
object FollowUpText {

    /**
     * The longest a first line can be and still read as a title rather than as
     * a sentence somebody has been cut off in the middle of.
     */
    private const val HEADING_CHARS = 60

    /**
     * The line to show in bold at the top, or null when the saved text has no
     * natural title and should be shown as itself.
     */
    fun heading(snippet: String): String? {
        val trimmed = snippet.trim()
        if (trimmed.isEmpty()) return "Bookmarked note"
        val first = trimmed.lineSequence().first().trim()
        // Longer than a title, so it is a paragraph. Truncating it would put a
        // bold half-sentence directly above the same half-sentence in the body.
        return first.takeIf { it.length <= HEADING_CHARS }
    }

    /**
     * The text under the heading, the whole thing when there is no heading, or
     * null when the heading already said everything.
     */
    fun body(snippet: String): String? {
        val trimmed = snippet.trim()
        if (trimmed.isEmpty()) return null
        val heading = heading(snippet) ?: return trimmed
        // Everything after the title line. Blank for a one-line item, which is
        // the "History of navigation" case: the heading is the whole thing.
        return trimmed.removePrefix(heading).trim().ifBlank { null }
    }
}
