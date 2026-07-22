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
    /** Models come from two families, so the prompt layout travels with them. */
    val format: com.kamsiob.kamai.llm.ChatFormat,
    /** One plain line for the Advanced model list. Empty for the tier defaults. */
    val description: String = "",
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

/**
 * Turns the memory figure Android reports into the memory the phone is sold
 * with.
 *
 * `ActivityManager.MemoryInfo.totalMem` is always meaningfully lower than the
 * marketed size, because the kernel and the bootloader carve out their share
 * before Android ever sees it. A Pixel 10 Pro XL sold as 16 GB reports about
 * 15.2. A 12 GB phone reports about 11.3, an 8 GB phone about 7.5.
 *
 * Taken literally, that is not a rounding curiosity, it is a real bug: every
 * tier boundary sits exactly on a marketed size, so a genuine 16 GB flagship
 * reports 15, fails the 16 GB gate, has Best Available locked, and gets
 * recommended the smallest model in the catalogue. Snapping up to the nearest
 * size a phone is actually sold with is what makes the tier gates mean what
 * they say.
 *
 * Only snaps upward, and only across a gap small enough to be the kernel's
 * share, so a device that really does have less is never promoted past what it
 * can hold.
 */
fun marketedRamGb(reportedBytes: Long): Int {
    val reported = reportedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val sizes = intArrayOf(2, 3, 4, 6, 8, 12, 16, 24, 32, 48, 64)

    sizes.forEach { size ->
        // Roughly 12 percent covers the reservation on every device checked,
        // and is far narrower than the gap between any two adjacent sizes.
        if (reported <= size && reported >= size * 0.88) return size
    }
    return reported.toInt()
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
