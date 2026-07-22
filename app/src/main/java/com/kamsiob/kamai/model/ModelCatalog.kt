package com.kamsiob.kamai.model

import com.kamsiob.kamai.llm.ChatFormat

/**
 * The models Kam AI offers.
 *
 * The default lineup is Gemma 4 across every tier, which is the family this app
 * is built around. Gemma 4 was released on 2 April 2026, and unlike the older
 * Gemma Terms of Use it is Apache 2.0, so redistribution and in-app download are
 * straightforward with no licence asterisk on any tier. It is purpose-built for
 * on-device use, ships instruction-tuned GGUF variants, and its size range fills
 * the 7 to 8 billion band that previously forced a different family at the top
 * tier. All of this was verified against the live Hugging Face API at build
 * time, not taken on faith. See DECISIONS.md.
 *
 * - **Basic**, 8 GB phones: Gemma 4 E2B Instruct, 3.1 GB at Q4_K_M.
 * - **Balanced**, 12 GB phones: Gemma 4 E4B Instruct, 5.0 GB at Q4_K_M.
 * - **Best Available**, 16 GB phones: Gemma 4 12B Instruct, 7.1 GB at Q4_K_M.
 *
 * There is no Qwen anywhere any more. Gemma 4 covers all three tiers cleanly, so
 * the app ships one family, one licence, and one prompt format.
 *
 * Q4_K_M throughout. Below Q4 these models start losing the thing this app leans
 * on hardest, which is following instructions carefully, and the guardrails in
 * SystemPrompts are instructions.
 *
 * The [defaults] are what onboarding and the tier picker offer. The wider
 * [advanced] set is browsable from Settings for people who want to reason about
 * models themselves, but nothing about it is required reading. Every entry, in
 * either list, is a genuine instruction-tuned GGUF with a verified size and
 * hash.
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
        description = "The strongest model that fits comfortably on a 16 GB phone.",
    )

    /** One per tier. What onboarding and the tier picker offer. */
    val defaults = listOf(basic, balanced, best)

    /**
     * The Advanced section: other compatible models a curious user can browse
     * and switch between. Kept small and high-signal rather than a dump of every
     * quantisation. Each says its size plainly and is gated by the same RAM
     * logic as the tiers.
     */
    val advanced = listOf(
        TierModel(
            id = "gemma-4-e4b-it-q5km",
            tier = Tier.BALANCED,
            displayName = "Gemma 4 E4B, higher quality",
            parameterLabel = "E4B",
            quantisation = "Q5_K_M",
            downloadBytes = 5_481_798_784L,
            contextTokens = 6144,
            licence = "Apache-2.0",
            sourceUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q5_K_M.gguf",
            sha256 = "a5d6e634db151368d2caa4270fd32ab604d96c2d1291950586989f46d8e17d36",
            format = ChatFormat.GEMMA,
            description = "The same E4B at a heavier quantisation. A little sharper, a little larger.",
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
