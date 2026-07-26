package com.kamsiob.kamai.ui.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Issue #43: scrolling was fought during a long streaming response, because
 * "follow when at the bottom" re-engaged the moment the user drifted near it,
 * and a growing message could put them there without their asking.
 */
class ScrollFollowTest {

    // Viewport is 0 to 1000 in every case below, so a last item ending at 1000 is
    // exactly full, below 1000 is short of the fold and above it runs past.
    private val viewportEnd = 1000

    private fun atBottom(
        lastVisibleIndex: Int?,
        lastVisibleItemEnd: Int,
        totalItems: Int,
    ) = ScrollFollow.isAtBottom(
        lastVisibleIndex = lastVisibleIndex,
        lastVisibleItemEnd = lastVisibleItemEnd,
        totalItems = totalItems,
        viewportEnd = viewportEnd,
    )

    @Test
    fun anEmptyListCountsAsAtTheBottom() {
        // So a first message follows normally rather than being held back.
        assertThat(atBottom(lastVisibleIndex = null, lastVisibleItemEnd = 0, totalItems = 0))
            .isTrue()
    }

    @Test
    fun theLastMessageFullyOnScreenIsAtTheBottom() {
        assertThat(atBottom(lastVisibleIndex = 4, lastVisibleItemEnd = 900, totalItems = 5))
            .isTrue()
    }

    @Test
    fun anEarlierMessageBeingLastVisibleIsNotAtTheBottom() {
        assertThat(atBottom(lastVisibleIndex = 2, lastVisibleItemEnd = 900, totalItems = 5))
            .isFalse()
    }

    @Test
    fun aGrowingLastMessageRunningPastTheFoldIsNotAtTheBottom() {
        // The regression this predicate exists for. The last item is still the
        // last item, so the old index-only check said "at the bottom" and the
        // view sprang back. Its text now ends 400px below the viewport.
        assertThat(atBottom(lastVisibleIndex = 4, lastVisibleItemEnd = 1400, totalItems = 5))
            .isFalse()
    }

    @Test
    fun aFewPixelsOfRoundingStillCountsAsTheBottom() {
        // Layout rounding leaves the last item ending a fraction past the
        // viewport even when it is fully shown. Without slack the view would
        // call itself scrolled-away while sitting perfectly still.
        assertThat(atBottom(lastVisibleIndex = 4, lastVisibleItemEnd = 1006, totalItems = 5))
            .isTrue()
    }

    @Test
    fun theThinkingIndicatorCountsAsARowBelowTheLastMessage() {
        // Found on the phone, not here. While the model is thinking there is one
        // more row than there are messages, so being at the bottom of the last
        // *message* is not being at the bottom of the list. Code that scrolls to
        // messages.lastIndex stops a row short, atBottom stays false, and the
        // response never starts following once it arrives. Five messages plus the
        // indicator is six rows, and index 4 is not the last of them.
        assertThat(atBottom(lastVisibleIndex = 4, lastVisibleItemEnd = 900, totalItems = 6))
            .isFalse()
        // Scrolling to the real last row, the indicator itself, does reach bottom.
        assertThat(atBottom(lastVisibleIndex = 5, lastVisibleItemEnd = 900, totalItems = 6))
            .isTrue()
    }

    // --- following, decided by distance rather than by a latch (#74) ---

    private fun distance(
        lastVisibleIndex: Int?,
        lastVisibleItemEnd: Int,
        totalItems: Int,
    ) = ScrollFollow.distanceFromBottom(
        lastVisibleIndex = lastVisibleIndex,
        lastVisibleItemEnd = lastVisibleItemEnd,
        totalItems = totalItems,
        viewportEnd = viewportEnd,
    )

    /** Viewport height, matching the 0..1000 viewport used throughout. */
    private val viewport = 1000

    @Test
    fun `an untouched reader is followed however long the answer gets`() {
        // The report, three times over: a long reply stops scrolling itself. The
        // reader has not moved, so each token leaves them a line or two from the
        // bottom, which is well inside the threshold.
        assertThat(ScrollFollow.shouldFollow(distance(4, 1040, 5), viewport)).isTrue()
        assertThat(ScrollFollow.shouldFollow(distance(4, 1200, 5), viewport)).isTrue()
    }

    @Test
    fun `a reader who has scrolled up is left alone`() {
        // Deliberately gone back to re-read something. A third of a screen is far
        // more than any single token moves the text.
        assertThat(ScrollFollow.shouldFollow(distance(4, 1400, 5), viewport)).isFalse()
        assertThat(ScrollFollow.shouldFollow(distance(4, 3000, 5), viewport)).isFalse()
    }

    @Test
    fun `scrolling back down resumes following with nothing to reset`() {
        // The whole point of dropping the latch. There is no state to clear and
        // no arrow to tap: the same question asked again simply answers yes.
        val away = distance(4, 2000, 5)
        assertThat(ScrollFollow.shouldFollow(away, viewport)).isFalse()
        val back = distance(4, 1050, 5)
        assertThat(ScrollFollow.shouldFollow(back, viewport)).isTrue()
    }

    @Test
    fun `growth can only push a reader away from the bottom, never towards it`() {
        // The worry behind the original latch, which had the direction backwards.
        // Text arriving increases the distance; nothing but the user's own
        // scrolling decreases it.
        val before = distance(4, 1100, 5)!!
        val afterMoreText = distance(4, 1300, 5)!!
        assertThat(afterMoreText).isGreaterThan(before)
    }

    @Test
    fun `a reader miles up the transcript is not dragged anywhere`() {
        // The end of the content is not even in view, so there is no distance to
        // measure and no case for moving them.
        assertThat(ScrollFollow.shouldFollow(distance(1, 900, 5), viewport)).isFalse()
    }

    @Test
    fun `an unmeasured list is never scrolled`() {
        // Viewport zero means the layout has not run. Following then would move a
        // list that does not know its own size, which is how a restored scroll
        // position used to get overwritten on open.
        assertThat(ScrollFollow.shouldFollow(0, 0)).isFalse()
    }

    @Test
    fun `an empty list is at distance zero and is followed`() {
        assertThat(distance(null, 0, 0)).isEqualTo(0)
        assertThat(ScrollFollow.shouldFollow(distance(null, 0, 0), viewport)).isTrue()
    }

    @Test
    fun `the end being exactly on screen is distance zero, not negative`() {
        assertThat(distance(4, 900, 5)).isEqualTo(0)
    }

}
