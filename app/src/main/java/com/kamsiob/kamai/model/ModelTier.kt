package com.kamsiob.kamai.model

/**
 * The three model tiers, and the logic that decides which one a phone should be
 * offered. This file has no Android dependencies so the boundaries at 8, 12 and
 * 16 GB can be tested directly.
 */
enum class Tier(
    val displayName: String,
    /** Total device RAM, in GB, at or above which this tier can run at all. */
    val minimumRamGb: Int,
) {
    BASIC("Basic", 8),
    BALANCED("Balanced", 12),
    BEST("Best Available", 16),
}

/**
 * A model the app can download for a tier.
 *
 * @param contextTokens the context window the app actually runs it at, which is
 *   smaller than the model's trained maximum so that memory stays predictable
 *   on a phone that is also running everything else.
 */
data class TierModel(
    val id: String,
    val tier: Tier,
    val displayName: String,
    val parameterLabel: String,
    val quantisation: String,
    val downloadBytes: Long,
    val contextTokens: Int,
    val licence: String,
    val sourceUrl: String,
    val sha256: String,
) {
    /** "1.2 GB", for the mono size labels. */
    val downloadLabel: String get() = formatBytes(downloadBytes)
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) {
        "%.1f GB".format(gb)
    } else {
        "%.0f MB".format(bytes / 1_000_000.0)
    }
}

object TierRecommendation {

    /**
     * How much room the app insists on leaving free above the model's own needs.
     * The recommendation deliberately does not max out the device: a phone that
     * technically fits a bigger model but then thrashes, or gets the app killed
     * in the background every time, is a worse experience than a smaller model
     * that always works.
     */
    const val HEADROOM_GB = 4

    /** Tiers this device has enough memory to run at all. */
    fun available(totalRamGb: Int): List<Tier> =
        Tier.entries.filter { totalRamGb >= it.minimumRamGb }

    fun isLocked(tier: Tier, totalRamGb: Int): Boolean = totalRamGb < tier.minimumRamGb

    /**
     * The tier to recommend, leaving comfortable headroom.
     *
     * A tier is recommended only when the device has its requirement plus the
     * headroom, so a 12 GB phone is pointed at Basic rather than Balanced, and
     * a 16 GB phone at Balanced rather than Best Available. When nothing clears
     * the headroom bar, the largest tier that runs at all is recommended, which
     * on an 8 GB phone means Basic.
     */
    fun recommended(totalRamGb: Int): Tier? {
        val runnable = available(totalRamGb)
        if (runnable.isEmpty()) return null

        val comfortable = runnable.filter { totalRamGb >= it.minimumRamGb + HEADROOM_GB }
        return comfortable.lastOrNull() ?: runnable.first()
    }

    /** The plain sentence under the tier cards on onboarding slide 4. */
    fun explain(totalRamGb: Int): String {
        val tier = recommended(totalRamGb)
            ?: return "This phone has ${totalRamGb} GB of memory, which is not enough to run " +
                "a model well. Kam AI needs at least 8 GB."
        return "This phone has ${totalRamGb} GB of memory. Bigger models give better answers " +
            "but need more room. ${tier.displayName} fits comfortably here."
    }

    /** The amber note on a locked tier card. Colour is never the only carrier. */
    fun lockedNote(tier: Tier): String = "Needs ${tier.minimumRamGb} GB"
}
