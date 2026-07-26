package com.kamsiob.kamai.ui.chat

/**
 * Whether a streaming response should keep scrolling itself into view.
 *
 * Reported three times and fixed twice without being fixed. The two earlier
 * attempts were both real bugs: an animated scroll that every token cancelled,
 * and a `shouldFollow` that required being at the bottom, which is false exactly
 * when following is needed. Neither was the whole story.
 *
 * The remaining fault was a latch. Following stopped the moment the user dragged
 * the list at all, and only resumed when the last message's end came back on
 * screen. During a long answer the text keeps growing past the fold, so that
 * never happened: one incidental touch turned following off until the answer
 * finished, and the only way back was the jump-to-latest arrow. The arrow is for
 * returning after deliberately scrolling up. It is not meant to be required.
 *
 * So there is no latch now. Following is decided by one question asked fresh
 * every time: how far is the reader from the bottom?
 *
 * That works because growth only ever pushes the reader *away* from the bottom,
 * never towards it. The worry behind the original latch — that a message growing
 * underneath somebody would drift them back into following without their asking —
 * has the direction backwards. Nothing can carry a reader towards the bottom
 * except their own scrolling, which is exactly the intent that should resume it.
 */
object ScrollFollow {

    /**
     * How close to the bottom still counts as being at it, in pixels.
     *
     * Rounding in the layout leaves the last item ending a fraction past the
     * viewport even when it is fully shown, and without a little slack the view
     * would call itself scrolled-away while sitting still. This one is for
     * "is the newest text visible", which drives the jump-to-latest arrow.
     */
    const val BOTTOM_TOLERANCE_PX: Int = 8

    /**
     * How much of the viewport a reader may be above the bottom and still be
     * followed.
     *
     * Generous on purpose. A tight threshold makes following stop the instant a
     * token pushes a line past the fold, which is the failure this whole file
     * exists to prevent. A third of a screen is more than any single token and
     * far less than a deliberate scroll up to re-read something.
     */
    const val FOLLOW_VIEWPORT_FRACTION: Float = 1f / 3f

    /**
     * Whether the newest message's end is on screen.
     *
     * Drives the jump-to-latest control. Asking only whether the last item is
     * visible is wrong while that item is growing: a long answer is taller than
     * the screen, so its index stays the last index no matter how far below the
     * fold its newest text has gone. This asks where the item actually *ends*.
     *
     * @param lastVisibleIndex index of the last visible item, or null when the
     *   list is empty, which counts as being at the bottom so a first message
     *   follows normally.
     * @param lastVisibleItemEnd where that item ends, as an offset from the start
     *   of the viewport.
     * @param viewportEnd where the viewport ends, in the same coordinates.
     */
    fun isAtBottom(
        lastVisibleIndex: Int?,
        lastVisibleItemEnd: Int,
        totalItems: Int,
        viewportEnd: Int,
        tolerancePx: Int = BOTTOM_TOLERANCE_PX,
    ): Boolean {
        if (lastVisibleIndex == null) return true
        if (lastVisibleIndex < totalItems - 1) return false
        return lastVisibleItemEnd - viewportEnd <= tolerancePx
    }

    /**
     * How far below the viewport the content continues, in pixels.
     *
     * Zero when the end of the content is on screen. Only ever measured against
     * the last item, because a list whose last item is visible has nothing after
     * it, and one whose last item is not visible is by definition a long way from
     * the bottom.
     *
     * @return the distance, or null when it cannot be told from what is visible,
     *   which the caller should treat as far away rather than guess.
     */
    fun distanceFromBottom(
        lastVisibleIndex: Int?,
        lastVisibleItemEnd: Int,
        totalItems: Int,
        viewportEnd: Int,
    ): Int? {
        if (lastVisibleIndex == null) return 0
        if (lastVisibleIndex < totalItems - 1) return null
        return (lastVisibleItemEnd - viewportEnd).coerceAtLeast(0)
    }

    /**
     * Whether streaming text should scroll itself into view right now.
     *
     * The whole decision, in one place, asked fresh on every token rather than
     * remembered. A reader within [FOLLOW_VIEWPORT_FRACTION] of the bottom is
     * reading the newest text and wants to keep reading it. A reader further up
     * has gone somewhere on purpose and is left alone until they come back, at
     * which point this starts returning true again on its own, with nothing to
     * reset and no arrow to tap.
     *
     * @param distanceFromBottomPx from [distanceFromBottom]; null means the end
     *   of the content is not even in view, which is not a reader to drag around.
     */
    fun shouldFollow(distanceFromBottomPx: Int?, viewportHeightPx: Int): Boolean {
        val distance = distanceFromBottomPx ?: return false
        // A viewport of zero means the list has not been measured yet. Following
        // then would scroll a list that does not know its own size, which is how
        // a restored scroll position used to get overwritten on open.
        if (viewportHeightPx <= 0) return false
        return distance <= viewportHeightPx * FOLLOW_VIEWPORT_FRACTION
    }
}
