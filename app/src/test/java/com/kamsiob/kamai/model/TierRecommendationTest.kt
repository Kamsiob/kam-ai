package com.kamsiob.kamai.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tier boundaries at 8, 12 and 16 GB, tested from both sides. Getting this
 * wrong means either recommending a model a phone cannot hold, or quietly
 * denying a capable phone the better model, and neither shows up as a crash.
 */
class TierRecommendationTest {

    @Test
    fun belowEightGigabytesNothingRuns() {
        listOf(2, 4, 6, 7).forEach { ram ->
            assertThat(TierRecommendation.available(ram)).isEmpty()
            assertThat(TierRecommendation.recommended(ram)).isNull()
        }
    }

    @Test
    fun eightGigabytesUnlocksBasicOnly() {
        assertThat(TierRecommendation.available(8)).containsExactly(Tier.BASIC)
        assertThat(TierRecommendation.isLocked(Tier.BASIC, 8)).isFalse()
        assertThat(TierRecommendation.isLocked(Tier.BALANCED, 8)).isTrue()
        assertThat(TierRecommendation.isLocked(Tier.BEST, 8)).isTrue()
    }

    @Test
    fun twelveGigabytesUnlocksBalancedButIsRecommendedBasic() {
        assertThat(TierRecommendation.available(12))
            .containsExactly(Tier.BASIC, Tier.BALANCED).inOrder()
        // Balanced runs, but 12 GB does not clear its requirement plus headroom,
        // so the recommendation stays at Basic rather than maxing the device.
        assertThat(TierRecommendation.recommended(12)).isEqualTo(Tier.BASIC)
    }

    @Test
    fun sixteenGigabytesUnlocksEverythingButIsRecommendedBalanced() {
        assertThat(TierRecommendation.available(16))
            .containsExactly(Tier.BASIC, Tier.BALANCED, Tier.BEST).inOrder()
        assertThat(TierRecommendation.recommended(16)).isEqualTo(Tier.BALANCED)
    }

    @Test
    fun twentyGigabytesFinallyRecommendsBestAvailable() {
        assertThat(TierRecommendation.recommended(20)).isEqualTo(Tier.BEST)
    }

    @Test
    fun theRecommendationIsNeverALockedTier() {
        (8..32).forEach { ram ->
            val tier = TierRecommendation.recommended(ram)
            assertThat(tier).isNotNull()
            assertThat(TierRecommendation.isLocked(tier!!, ram)).isFalse()
        }
    }

    @Test
    fun theRecommendationNeverGoesBackwardsAsMemoryGrows() {
        var previousOrdinal = -1
        (8..32).forEach { ram ->
            val ordinal = TierRecommendation.recommended(ram)!!.ordinal
            assertThat(ordinal).isAtLeast(previousOrdinal)
            previousOrdinal = ordinal
        }
    }

    @Test
    fun eightGigabytesGetsBasicEvenThoughItDoesNotClearHeadroom() {
        // The fallback branch: nothing clears the headroom bar, so the smallest
        // runnable tier is offered rather than nothing at all.
        assertThat(TierRecommendation.recommended(8)).isEqualTo(Tier.BASIC)
        assertThat(TierRecommendation.recommended(9)).isEqualTo(Tier.BASIC)
    }

    @Test
    fun lockedNotesNameTheRealRequirement() {
        assertThat(TierRecommendation.lockedNote(Tier.BALANCED)).isEqualTo("Needs 12 GB")
        assertThat(TierRecommendation.lockedNote(Tier.BEST)).isEqualTo("Needs 16 GB")
    }

    @Test
    fun theExplanationNamesTheDeviceMemoryAndTheRecommendedTier() {
        val text = TierRecommendation.explain(16)
        assertThat(text).contains("16 GB")
        assertThat(text).contains("Balanced")
        assertThat(text).doesNotContain("—")
    }

    @Test
    fun anUnderpoweredPhoneIsToldPlainlyWhy() {
        val text = TierRecommendation.explain(6)
        assertThat(text).contains("at least 8 GB")
    }

    @Test
    fun sizesReadAsPlainLabels() {
        assertThat(formatBytes(1_200_000_000)).isEqualTo("1.2 GB")
        assertThat(formatBytes(850_000_000)).isEqualTo("850 MB")
    }
}

/**
 * Android never reports the memory a phone is sold with. Since every tier gate
 * sits exactly on a marketed size, getting this wrong locks the top tier on
 * hardware that plainly qualifies, which is what happened on a real Pixel 10
 * Pro XL before this existed.
 */
class MarketedRamTest {

    private fun gb(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    @Test
    fun realDeviceReportsSnapUpToTheSizeTheyAreSoldAs() {
        // Measured on the Pixel 10 Pro XL used to build this: 15948992 kB.
        assertThat(marketedRamGb(15_948_992L * 1024)).isEqualTo(16)

        assertThat(marketedRamGb(gb(15.2))).isEqualTo(16)
        assertThat(marketedRamGb(gb(11.3))).isEqualTo(12)
        assertThat(marketedRamGb(gb(7.5))).isEqualTo(8)
        assertThat(marketedRamGb(gb(5.6))).isEqualTo(6)
        assertThat(marketedRamGb(gb(3.7))).isEqualTo(4)
    }

    @Test
    fun anExactFigureIsLeftAlone() {
        assertThat(marketedRamGb(gb(16.0))).isEqualTo(16)
        assertThat(marketedRamGb(gb(8.0))).isEqualTo(8)
    }

    @Test
    fun aPhoneThatGenuinelyHasLessIsNeverPromoted() {
        // Well below 16, so it must not be treated as a 16 GB device.
        assertThat(marketedRamGb(gb(13.0))).isNotEqualTo(16)
        // 13 sits between sizes and is not within reach of 16.
        assertThat(marketedRamGb(gb(13.0))).isEqualTo(13)
        assertThat(marketedRamGb(gb(10.0))).isEqualTo(10)
    }

    @Test
    fun theFlagshipCaseEndToEnd() {
        // The bug: a 16 GB phone reporting 15 had Best Available locked and was
        // recommended the smallest model in the catalogue.
        val ram = marketedRamGb(15_948_992L * 1024)
        assertThat(TierRecommendation.isLocked(Tier.BEST, ram)).isFalse()
        assertThat(TierRecommendation.recommended(ram)).isEqualTo(Tier.BALANCED)
    }
}
