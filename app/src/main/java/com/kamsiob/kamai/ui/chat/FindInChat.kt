package com.kamsiob.kamai.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/**
 * Finding something said earlier in an open conversation (#85).
 *
 * Searching across conversations has always existed. Searching inside one did
 * not, which is where it is most needed: a long conversation is exactly the
 * place something gets lost, and it is what anybody expects from a chat.
 *
 * Everything about which message matches and where lives here, pure, so the
 * matching, the ordering and the wrap-around can be tested without a screen.
 */
object FindInChat {

    /**
     * How strongly a match is marked.
     *
     * Both are the accent at low alpha rather than a surface colour, because a
     * match has to read on two different backgrounds: an answer sits on `surface`
     * and your own message sits on `tonalFill`. The first attempt used
     * `tonalFill` for every match, which was invisible on exactly the bubbles
     * that are already that colour.
     */
    const val MARK_ALPHA: Float = 0.22f
    const val ACTIVE_MARK_ALPHA: Float = 0.45f

    /** One match: which message, and where in that message's plain text. */
    data class Match(val messageIndex: Int, val start: Int, val end: Int)

    /**
     * Every match, in reading order.
     *
     * Case-insensitive, because nobody searching their own conversation is
     * thinking about capitals. Overlapping matches are not a thing worth
     * supporting: the search advances past each hit.
     */
    fun matches(texts: List<String>, query: String): List<Match> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val found = mutableListOf<Match>()
        texts.forEachIndexed { messageIndex, text ->
            var from = 0
            while (from <= text.length - needle.length) {
                val at = text.indexOf(needle, from, ignoreCase = true)
                if (at < 0) break
                found += Match(messageIndex, at, at + needle.length)
                from = at + needle.length
            }
        }
        return found
    }

    /**
     * The next match after [current], wrapping round to the first.
     *
     * Wrapping rather than stopping at the end, because a search that goes dead
     * at the last hit makes somebody scroll back to the top by hand to carry on,
     * and the count already tells them where they are.
     */
    fun step(current: Int, total: Int, forward: Boolean): Int {
        if (total <= 0) return 0
        return if (forward) (current + 1) % total else (current - 1 + total) % total
    }

    /** "3 of 12", or a plain nothing-found rather than a zero. */
    fun countLabel(current: Int, total: Int): String =
        if (total == 0) "No matches" else "${current + 1} of $total"

    /**
     * Adds the highlight to every match in [text], with the one at [activeStart]
     * picked out more strongly.
     *
     * Two weights on purpose. Marking every match tells you how dense the term
     * is in a long answer; marking the current one differently is what stops the
     * next and previous buttons feeling like they do nothing on a message that
     * contains the word four times.
     */
    fun highlight(
        text: AnnotatedString,
        query: String,
        activeStart: Int?,
        allColor: Color,
        activeColor: Color,
    ): AnnotatedString {
        val needle = query.trim()
        if (needle.isEmpty()) return text
        val plain = text.text
        val builder = AnnotatedString.Builder(text)
        var from = 0
        while (from <= plain.length - needle.length) {
            val at = plain.indexOf(needle, from, ignoreCase = true)
            if (at < 0) break
            val isActive = activeStart != null && at == activeStart
            builder.addStyle(
                SpanStyle(background = if (isActive) activeColor else allColor),
                at,
                at + needle.length,
            )
            from = at + needle.length
        }
        return builder.toAnnotatedString()
    }
}
