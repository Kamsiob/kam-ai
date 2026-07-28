package com.kamsiob.kamai.model

/**
 * The two ratings a model shows before it is downloaded.
 *
 * A size in gigabytes and a parameter count tell somebody nothing about what
 * they will feel. The two questions actually being asked are how fast will this
 * be and how good will it be, and the picker should answer both in the same
 * units for every choice on offer.
 *
 * **Speed comes from this phone.** A figure measured on someone else's device is
 * worthless: phones differ by more than these models do. But a model that has
 * never run here has no measurement, and that is precisely the moment somebody is
 * choosing, so speed is estimated from what this phone has actually done with a
 * model it *has* run, scaled by relative cost. That is an estimate and is
 * labelled as one.
 *
 * **Quality is relative to this lineup.** Not to models the app does not offer,
 * and not to anything on a leaderboard. The comparison a user needs is between
 * the choices in front of them, so the best model here is five stars by
 * definition and the rest are placed against it.
 */
object ModelRatings {

    /** Five stars, filled and empty, for a rating from 1 to 5. */
    fun bar(stars: Int): String = "★".repeat(stars.coerceIn(1, 5)) +
        "☆".repeat((5 - stars.coerceIn(1, 5)))

    /**
     * How good this model is compared with the others in the lineup.
     *
     * Placed by parameter count first and quantisation second, which is the
     * ordering these models actually fall in. Deliberately coarse: a five point
     * scale invites a reading of "roughly where does this sit", which is what is
     * known, rather than a precision nobody has.
     */
    fun quality(model: TierModel): Int = when {
        model.parameterLabel.startsWith("12B") -> 5
        model.parameterLabel.startsWith("8B") -> 5
        model.parameterLabel.startsWith("E4B") && model.quantisation.startsWith("Q6") -> 5
        model.parameterLabel.startsWith("E4B") && model.quantisation.startsWith("Q5") -> 4
        model.parameterLabel.startsWith("E4B") -> 4
        model.parameterLabel.startsWith("4B") -> 3
        model.parameterLabel.startsWith("E2B") -> 2
        else -> 3
    }

    /**
     * How fast this model will feel here, from what this phone has measured.
     *
     * [measuredTokensPerSecond] is this model's own measurement when it has one.
     * [referenceTokensPerSecond] and [referenceCost] describe some other model
     * this phone has run, used to estimate when this one has never run.
     *
     * Null when the phone has measured nothing at all, because the honest answer
     * on a fresh install is that we do not know yet, and inventing a number here
     * would be the thing this app is built not to do.
     */
    fun speed(
        model: TierModel,
        measuredTokensPerSecond: Double?,
        referenceTokensPerSecond: Double? = null,
        referenceCost: Double? = null,
    ): Int? {
        val tps = measuredTokensPerSecond
            ?: estimate(model, referenceTokensPerSecond, referenceCost)
            ?: return null
        // Decode speed, in tokens a second, as a person experiences it. Reading
        // pace is about four words a second, so anything at or above that reads
        // as instant and below about two is visibly waiting.
        return when {
            tps >= 12 -> 5
            tps >= 8 -> 4
            tps >= 5 -> 3
            tps >= 3 -> 2
            else -> 1
        }
    }

    private fun estimate(
        model: TierModel,
        referenceTokensPerSecond: Double?,
        referenceCost: Double?,
    ): Double? {
        val reference = referenceTokensPerSecond ?: return null
        val from = referenceCost ?: return null
        if (from <= 0) return null
        // Decode is dominated by reading the weights, so speed scales roughly
        // with how much weight there is to read. Rough is the right level here:
        // the output is one of five stars, not a number of seconds.
        return reference * (from / cost(model))
    }

    /** Relative cost of one token, proportional to the bytes read per token. */
    fun cost(model: TierModel): Double = model.downloadBytes.toDouble()

    /** True when [speed] would be an estimate rather than this model's own figure. */
    fun isEstimated(measuredTokensPerSecond: Double?): Boolean = measuredTokensPerSecond == null

    /**
     * Speed stars for [model] given everything this phone has measured, keyed by
     * model id.
     *
     * Uses this model's own figure when there is one, and otherwise estimates
     * from another model this phone has run. The reference is chosen by id rather
     * than by whichever happens to be first, so the picker does not change its
     * mind between openings.
     */
    fun speedStars(model: TierModel, measuredByModelId: Map<String, Double>): Int? {
        measuredByModelId[model.id]?.let { return speed(model, it) }
        val reference = measuredByModelId.entries.sortedBy { it.key }
            .firstNotNullOfOrNull { (id, tps) -> ModelCatalog.byId(id)?.let { it to tps } }
            ?: return null
        return speed(model, null, reference.second, cost(reference.first))
    }
}
