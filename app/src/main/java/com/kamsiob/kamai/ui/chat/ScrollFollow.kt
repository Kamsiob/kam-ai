package com.kamsiob.kamai.ui.chat

/**
 * Whether a streaming response should keep scrolling itself into view.
 *
 * #35 landed "follow only when the user is at the bottom", driven by an
 * `atBottom` predicate. That is necessary and not sufficient, which is issue
 * #43: it re-engages the moment the user drifts back near the bottom, and a
 * message growing underneath them can put them there without their asking. The
 * result is a view that springs back mid-response and cannot be read at the
 * user's own pace.
 *
 * Two things fix it, and both live here so they can be tested without a device.
 *
 * The first is an honest `atBottom`. Asking only whether the last item is
 * visible is wrong while that item is growing: a long answer is taller than the
 * screen, so its index stays the last index no matter how far below the fold its
 * newest text has gone, and the old predicate kept answering "yes, at the
 * bottom". This one asks where the item actually *ends*.
 *
 * The second is [FollowLatch], a per-response record of the user having taken
 * over.
 */
object ScrollFollow {

    /**
     * How close to the bottom still counts as being at it, in pixels. Rounding in
     * the layout leaves the last item ending a fraction past the viewport even
     * when it is fully shown, and without a little slack the view would call
     * itself scrolled-away while sitting still.
     */
    const val BOTTOM_TOLERANCE_PX: Int = 8

    /**
     * Whether the newest message's end is on screen.
     *
     * @param lastVisibleIndex index of the last visible item, or null when the
     *   list is empty, which counts as being at the bottom so a first message
     *   follows normally.
     * @param lastVisibleItemEnd where that item ends, as an offset from the start
     *   of the viewport. This is the part the old predicate ignored.
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
}

/**
 * A per-response record of the user having taken control of scrolling.
 *
 * Once they drag the list during a response, following stops for the rest of
 * that response, in either direction and however fast the tokens arrive. It
 * comes back only when they return to the bottom themselves or tap
 * jump-to-latest, or when the next response begins.
 *
 * Deliberately a plain object rather than Compose state. Nothing needs to
 * recompose when the latch moves: the effect that follows the stream reads it
 * when it runs, and the jump-to-latest control's visibility is driven by
 * `atBottom`, which is already observable. Keeping it plain is also what lets it
 * be tested on any machine.
 */
class FollowLatch {

    var userTookControl: Boolean = false
        private set

    /** The user dragged the list. This is the latch closing. */
    fun userDragged() {
        userTookControl = true
    }

    /** A new message means a new response, which follows from the start again. */
    fun newResponseStarted() {
        userTookControl = false
    }

    /** They came back to the bottom under their own steam, so following resumes. */
    fun returnedToBottom() {
        userTookControl = false
    }

    /** Jump-to-latest was tapped, which is the same intent stated explicitly. */
    fun jumpTapped() {
        userTookControl = false
    }

    /** Whether streaming text should scroll itself into view right now. */
    fun shouldFollow(atBottom: Boolean): Boolean = atBottom && !userTookControl
}
