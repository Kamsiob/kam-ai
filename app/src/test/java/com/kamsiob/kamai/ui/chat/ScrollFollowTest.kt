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
    fun followingIsOnByDefaultAtTheBottom() {
        val latch = FollowLatch()
        assertThat(latch.userTookControl).isFalse()
        assertThat(latch.shouldFollow(atBottom = true)).isTrue()
    }

    @Test
    fun followingIsOffWhenTheUserIsReadingElsewhere() {
        val latch = FollowLatch()
        assertThat(latch.shouldFollow(atBottom = false)).isFalse()
    }

    @Test
    fun aDragStopsFollowingForTheRestOfTheResponse() {
        val latch = FollowLatch()
        latch.userDragged()
        // Even back at the bottom, this response does not resume following on its
        // own. That is the whole point of the latch: no snapping back.
        assertThat(latch.shouldFollow(atBottom = true)).isFalse()
    }

    @Test
    fun aDragLatchesInEitherDirection() {
        // The issue asks for this explicitly: scrolling down is taking control
        // just as much as scrolling up.
        val latch = FollowLatch()
        latch.userDragged()
        latch.userDragged()
        assertThat(latch.userTookControl).isTrue()
    }

    @Test
    fun returningToTheBottomResumesFollowing() {
        val latch = FollowLatch()
        latch.userDragged()
        latch.returnedToBottom()
        assertThat(latch.shouldFollow(atBottom = true)).isTrue()
    }

    @Test
    fun tappingJumpToLatestResumesFollowing() {
        val latch = FollowLatch()
        latch.userDragged()
        latch.jumpTapped()
        assertThat(latch.shouldFollow(atBottom = true)).isTrue()
    }

    @Test
    fun theNextResponseFollowsAgain() {
        // The latch is per-response, so having read at your own pace through one
        // answer does not turn following off for good.
        val latch = FollowLatch()
        latch.userDragged()
        latch.newResponseStarted()
        assertThat(latch.shouldFollow(atBottom = true)).isTrue()
    }

    @Test
    fun aLatchedResponseStaysLatchedThroughFastTokenArrival() {
        // Tokens arriving quickly must not shake the latch loose. Nothing about
        // content growth touches it; only the four explicit events do.
        val latch = FollowLatch()
        latch.userDragged()
        repeat(200) { assertThat(latch.shouldFollow(atBottom = false)).isFalse() }
        assertThat(latch.userTookControl).isTrue()
    }
}
