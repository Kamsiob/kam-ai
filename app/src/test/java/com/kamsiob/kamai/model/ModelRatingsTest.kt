package com.kamsiob.kamai.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The two ratings shown before a download (item 22).
 *
 * The rule this file mostly defends: nothing is claimed about a phone that has
 * measured nothing. A confident wrong number in the picker is worse than an
 * absent one, because it is the number somebody chooses on.
 */
class ModelRatingsTest {

    @Test
    fun aPhoneThatHasMeasuredNothingIsToldNothing() {
        assertThat(ModelRatings.speed(ModelCatalog.basic, null)).isNull()
    }

    @Test
    fun aModelWithItsOwnMeasurementUsesIt() {
        assertThat(ModelRatings.speed(ModelCatalog.basic, 12.5)).isEqualTo(5)
        assertThat(ModelRatings.speed(ModelCatalog.basic, 9.0)).isEqualTo(4)
        assertThat(ModelRatings.speed(ModelCatalog.basic, 1.5)).isEqualTo(1)
    }

    @Test
    fun anUnrunModelIsEstimatedFromOneThisPhoneHasRun() {
        // The case the picker exists for: choosing a model that has never run
        // here, on a phone that has run something else. E4B measured at 9 tok/s
        // implies the smaller E2B is faster, because there is less weight to read.
        val stars = ModelRatings.speed(
            ModelCatalog.basic,
            measuredTokensPerSecond = null,
            referenceTokensPerSecond = 9.0,
            referenceCost = ModelRatings.cost(ModelCatalog.balanced),
        )
        assertThat(stars).isNotNull()
        assertThat(stars!!).isAtLeast(ModelRatings.speed(ModelCatalog.balanced, 9.0)!!)
    }

    @Test
    fun theEstimateGoesTheRightWayForABiggerModel() {
        // Estimating a larger model from a smaller one must not make it look
        // faster, which is the way round that would mislead somebody into a
        // download they regret.
        val bigger = ModelRatings.speed(
            ModelCatalog.best,
            measuredTokensPerSecond = null,
            referenceTokensPerSecond = 9.0,
            referenceCost = ModelRatings.cost(ModelCatalog.basic),
        )
        assertThat(bigger!!).isAtMost(ModelRatings.speed(ModelCatalog.basic, 9.0)!!)
    }

    @Test
    fun qualityIsRelativeToThisLineup() {
        // The comparison a user needs is between the choices in front of them, so
        // the strongest model offered is the top of the scale.
        assertThat(ModelRatings.quality(ModelCatalog.balanced))
            .isGreaterThan(ModelRatings.quality(ModelCatalog.basic))
        assertThat(ModelRatings.quality(ModelCatalog.best))
            .isAtLeast(ModelRatings.quality(ModelCatalog.balanced))
    }

    @Test
    fun theBarIsAlwaysFiveCharactersWide() {
        // It sits in a column beside other models, and a rating that changes
        // width reads as a different kind of thing rather than a different score.
        (1..5).forEach { assertThat(ModelRatings.bar(it)).hasLength(5) }
        assertThat(ModelRatings.bar(0)).hasLength(5)
        assertThat(ModelRatings.bar(9)).hasLength(5)
    }

    @Test
    fun anEstimateKnowsItIsOne() {
        assertThat(ModelRatings.isEstimated(null)).isTrue()
        assertThat(ModelRatings.isEstimated(8.0)).isFalse()
    }
}
