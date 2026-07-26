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

    // --- the three rules (#89) ---

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

    private val viewport = 1000

    @Test
    fun `rule 1, an untouched reader is followed however far the answer runs past the fold`() {
        // The regression #74's fix introduced: following was made conditional on
        // being near the bottom, which is false at exactly the moment following
        // is needed.
        val latch = FollowLatch()
        assertThat(latch.shouldFollow()).isTrue()
        // Nothing about how far past the fold the text has gone changes this.
        assertThat(distance(4, 4000, 5)).isEqualTo(3000)
        assertThat(latch.shouldFollow()).isTrue()
    }

    @Test
    fun `rule 2, one scroll stops following for the rest of the response`() {
        val latch = FollowLatch()
        latch.userScrolled()
        assertThat(latch.shouldFollow()).isFalse()
        // However many tokens arrive afterwards.
        repeat(50) { assertThat(latch.shouldFollow()).isFalse() }
    }

    @Test
    fun `rule 2, returning to the bottom themselves resumes following`() {
        val latch = FollowLatch()
        latch.userScrolled()
        latch.returnedToBottom()
        assertThat(latch.shouldFollow()).isTrue()
    }

    @Test
    fun `rule 2, tapping jump to latest resumes following`() {
        val latch = FollowLatch()
        latch.userScrolled()
        latch.jumpTapped()
        assertThat(latch.shouldFollow()).isTrue()
    }

    @Test
    fun `a scroll in one response does not disable following in the next`() {
        // The other half of what #74 got wrong: a latch that survives into the
        // next response disables following for the rest of the conversation.
        val latch = FollowLatch()
        latch.userScrolled()
        latch.newResponseStarted()
        assertThat(latch.shouldFollow()).isTrue()
    }

    @Test
    fun `returning is judged generously, since the target keeps moving`() {
        // A reader scrolling back down during a fast stream is chasing text that
        // is still arriving. Demanding they land within a few pixels of the end
        // would make resuming feel broken.
        assertThat(ScrollFollow.hasReturnedToBottom(distance(4, 1200, 5), viewport)).isTrue()
        assertThat(ScrollFollow.hasReturnedToBottom(distance(4, 1900, 5), viewport)).isFalse()
    }

    @Test
    fun `returning is never judged from an unmeasured list`() {
        assertThat(ScrollFollow.hasReturnedToBottom(0, 0)).isFalse()
    }

    @Test
    fun `a reader miles up the transcript has not returned`() {
        assertThat(ScrollFollow.hasReturnedToBottom(distance(1, 900, 5), viewport)).isFalse()
    }

    @Test
    fun `an empty list counts as at the bottom and is followed`() {
        assertThat(distance(null, 0, 0)).isEqualTo(0)
        assertThat(ScrollFollow.hasReturnedToBottom(distance(null, 0, 0), viewport)).isTrue()
    }

    @Test
    fun `the end being exactly on screen is distance zero, not negative`() {
        assertThat(distance(4, 900, 5)).isEqualTo(0)
    }
}
