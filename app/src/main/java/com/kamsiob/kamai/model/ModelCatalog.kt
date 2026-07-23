package com.kamsiob.kamai.model

import com.kamsiob.kamai.llm.ChatFormat

/**
 * The models Kam AI offers, assigned to tiers from measured reality on device,
 * not from file size on paper.
 *
 * The correction that produced this lineup: the 12B was briefly the Best tier
 * default, and on a real 16 GB Pixel it passed an over-optimistic memory check
 * and was then killed by the kernel out-of-memory while loading. Loading a GGUF
 * touches essentially the whole file plus the context buffers, so a 7 GB model
 * needs on the order of 8 GB free, which a 16 GB phone running an operating
 * system and other apps does not reliably have. A model that cannot load
 * comfortably on its tier's device class does not belong on that tier.
 *
 * So the default tiers are the Gemma 4 on-device (E) line, which is what that
 * line is for, and the 12B moves to Advanced with an honest warning:
 *
 * - **Basic**, 8 GB phones: Gemma 4 E2B, 3.1 GB at Q4_K_M. Measured to load and
 *   generate on device.
 * - **Balanced**, 12 GB phones: Gemma 4 E4B, 5.0 GB at Q4_K_M.
 * - **Best Available**, 16 GB phones: Gemma 4 E4B at Q5_K_M, 5.5 GB. The same
 *   capable on-device model as Balanced at higher precision, which is the honest
 *   ceiling for a model that runs comfortably on a phone.
 *
 * All Apache 2.0, all instruction-tuned GGUF, sizes and hashes read from the
 * live Hugging Face API. See DECISIONS.md for the measured figures.
 */
object ModelCatalog {

    val basic = TierModel(
        id = "gemma-4-e2b-it-q4km",
        tier = Tier.BASIC,
        displayName = "Gemma 4 E2B",
        parameterLabel = "E2B",
        quantisation = "Q4_K_M",
        downloadBytes = 3_106_738_272L,
        contextTokens = 4096,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
        sha256 = "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8",
        format = ChatFormat.GEMMA,
        description = "The lightest Gemma 4. Runs well on an 8 GB phone.",
    )

    val balanced = TierModel(
        id = "gemma-4-e4b-it-q4km",
        tier = Tier.BALANCED,
        displayName = "Gemma 4 E4B",
        parameterLabel = "E4B",
        quantisation = "Q4_K_M",
        downloadBytes = 4_977_171_584L,
        contextTokens = 6144,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
        sha256 = "85a896a047553e842f25297ee5b031d64ff30147d9c4af17b1e4b394cd1fab87",
        format = ChatFormat.GEMMA,
        description = "The sweet spot for a 12 GB phone. Noticeably sharper than E2B.",
    )

    val best = TierModel(
        id = "gemma-4-e4b-it-q5km",
        tier = Tier.BEST,
        displayName = "Gemma 4 E4B, higher quality",
        parameterLabel = "E4B",
        quantisation = "Q5_K_M",
        downloadBytes = 5_481_798_784L,
        contextTokens = 8192,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q5_K_M.gguf",
        sha256 = "a5d6e634db151368d2caa4270fd32ab604d96c2d1291950586989f46d8e17d36",
        format = ChatFormat.GEMMA,
        description = "E4B at higher precision. The best that runs comfortably on a 16 GB phone.",
    )

    /** One per tier. What onboarding and the tier picker offer. */
    val defaults = listOf(basic, balanced, best)

    /**
     * The Advanced section: heavier models for the curious, each honest about the
     * memory it needs. The 12B lives here, not on a tier, because it cannot load
     * comfortably on a typical 16 GB phone. See DECISIONS.md.
     */
    val advanced = listOf(
        TierModel(
            id = "gemma-4-e4b-it-q6k",
            tier = Tier.BEST,
            displayName = "Gemma 4 E4B, maximum quality",
            parameterLabel = "E4B",
            quantisation = "Q6_K",
            downloadBytes = 7_074_929_792L,
            contextTokens = 8192,
            licence = "Apache-2.0",
            sourceUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q6_K.gguf",
            sha256 = "fd83f3ef44d22e00ff0f638753dde9a66a83e8ccf79c11024f47d383c489531b",
            format = ChatFormat.GEMMA,
            description = "E4B at Q6. A little sharper again, and heavier.",
            warning = "Needs about 8 GB free to load. On a busy 16 GB phone it may refuse until you close some apps.",
        ),
        TierModel(
            id = "gemma-4-12b-it-q4km",
            tier = Tier.BEST,
            displayName = "Gemma 4 12B",
            parameterLabel = "12B",
            quantisation = "Q4_K_M",
            downloadBytes = 7_121_861_440L,
            contextTokens = 8192,
            licence = "Apache-2.0",
            sourceUrl = "https://huggingface.co/unsloth/gemma-4-12b-it-GGUF/resolve/main/gemma-4-12b-it-Q4_K_M.gguf",
            sha256 = "0a270ec9fe6b34f4a0d33992b6135117b484ebc4766ab76b51d4ae8c457e4c42",
            format = ChatFormat.GEMMA,
            description = "A much larger model. Desktop-class.",
            warning = "Measured to need more memory than a 16 GB phone can reliably spare, and it may refuse to load. For very high memory devices only.",
        ),
    )

    /** Everything the app knows about, defaults first. */
    val all: List<TierModel> = defaults + advanced

    fun forTier(tier: Tier): TierModel = when (tier) {
        Tier.BASIC -> basic
        Tier.BALANCED -> balanced
        Tier.BEST -> best
    }

    fun byId(id: String): TierModel? = all.firstOrNull { it.id == id }
}
