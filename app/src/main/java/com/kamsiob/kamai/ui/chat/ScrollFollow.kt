package com.kamsiob.kamai.ui.chat

/**
 * Whether a streaming response should keep scrolling itself into view.
 *
 * Four attempts now, and the history is worth keeping because each fix was real
 * and each was wrong about something else.
 *
 * #35 followed only while at the bottom. #43 found that re-engaged the moment a
 * growing message drifted the reader back near the bottom. #74 found two more:
 * an animated scroll that every token canceled, and a latch that any drag closed
 * and only the last message's end coming back on screen could reopen, which
 * during a long answer never happens.
 *
 * #74's fix then removed the latch entirely and followed on distance from the
 * bottom, which broke it the other way and is what this file now corrects. Two
 * holes in that reasoning:
 *
 * - **Sending did not scroll at all.** The follow effect keyed on `streaming`, so
 *   the first evaluation happened with the reader wherever they were. If they had
 *   been reading mid-transcript the distance check failed and it never moved.
 * - **Following stopped once the answer grew more than a third of a viewport past
 *   the fold**, and whether it recovered depended on every single scroll landing
 *   exactly at the content end. That is not something to rest a behavior on.
 *
 * So the three rules are now explicit, and each is here rather than spread
 * through a composable:
 *
 * 1. While a response streams and the user has not touched the list, follow.
 * 2. If they scroll during a response, stop for the rest of that response.
 * 3. When the user sends, scroll to the bottom unconditionally.
 *
 * The latch is back, because rule 2 needs one. What makes it correct this time is
 * that it reopens on two events rather than one: a new response starting, and the
 * reader arriving back at the bottom under their own steam. The failure in #74
 * was a latch with only one way out, not the idea of a latch.
 */
object ScrollFollow {

    /**
     * How close to the bottom still counts as being at it, in pixels.
     *
     * Rounding in the layout leaves the last item ending a fraction past the
     * viewport even when it is fully shown, and without a little slack the view
     * would call itself scrolled-away while sitting still.
     */
    const val BOTTOM_TOLERANCE_PX: Int = 8

    /**
     * How much of the viewport a reader may be past the bottom and still count as
     * having returned to it, for the purpose of resuming.
     *
     * Deliberately generous, and deliberately used only for *resuming*. A reader
     * scrolling back down during a fast stream is chasing a target that keeps
     * moving, and demanding they land within eight pixels of it would make
     * resuming feel broken. Nothing about following depends on this.
     */
    const val RESUME_VIEWPORT_FRACTION: Float = 1f / 4f

    /**
     * Whether the newest message's end is on screen.
     *
     * Drives the jump-to-latest control. Asking only whether the last item is
     * visible is wrong while that item is growing: a long answer is taller than
     * the screen, so its index stays the last index no matter how far below the
     * fold its newest text has gone. This asks where the item actually *ends*.
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
     * How far below the viewport the content continues, in pixels, or null when
     * that cannot be told from what is visible.
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
     * Whether the reader is close enough to the bottom to count as having come
     * back, which is what reopens the latch.
     *
     * Only ever asked while the latch is closed. It answers "have they returned",
     * never "should we follow".
     */
    fun hasReturnedToBottom(distanceFromBottomPx: Int?, viewportHeightPx: Int): Boolean {
        val distance = distanceFromBottomPx ?: return false
        if (viewportHeightPx <= 0) return false
        return distance <= viewportHeightPx * RESUME_VIEWPORT_FRACTION
    }
}

/**
 * The per-response record of the user having taken over scrolling (#89).
 *
 * Rule 2 needs somewhere to remember that they did, because the whole point is
 * that a single scroll settles the matter for the rest of that response however
 * fast the tokens arrive. Rules 1 and 3 do not consult it in the same way: rule 1
 * asks it, and rule 3 ignores it entirely.
 *
 * Plain state rather than Compose state. Nothing recomposes when this moves: the
 * effect that follows the stream reads it when it runs, and the jump-to-latest
 * control's visibility is driven by `atBottom`, which is already observable.
 * Keeping it plain is also what lets it be tested on any machine.
 */
class FollowLatch {

    var userTookControl: Boolean = false
        private set

    /** The user scrolled the list themselves. This is the latch closing. */
    fun userScrolled() {
        userTookControl = true
    }

    /**
     * A new response is starting, so following begins again.
     *
     * The other half of what #74 got wrong. A latch that survives into the next
     * response makes one scroll in one answer disable following for the rest of
     * the conversation.
     */
    fun newResponseStarted() {
        userTookControl = false
    }

    /** They came back to the bottom themselves, which is consent to resume. */
    fun returnedToBottom() {
        userTookControl = false
    }

    /** Jump-to-latest was tapped, which is the same consent stated explicitly. */
    fun jumpTapped() {
        userTookControl = false
    }

    /**
     * Rule 1: follow while streaming unless the user has taken over.
     *
     * Deliberately does **not** consult how far from the bottom the reader is.
     * That was #74's mistake in the other direction: following exists precisely
     * for the case where the newest text has grown past the bottom of the screen,
     * so making it conditional on being near the bottom means it stops working at
     * the exact moment it is needed.
     */
    fun shouldFollow(): Boolean = !userTookControl
}
