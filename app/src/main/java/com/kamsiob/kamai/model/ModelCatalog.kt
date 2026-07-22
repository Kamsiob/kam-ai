package com.kamsiob.kamai.model

import com.kamsiob.kamai.llm.ChatFormat

/**
 * The models Kam AI offers, one per tier.
 *
 * Family preference is Gemma first, then Qwen, taking whichever current variant
 * actually fits the tier's parameter band. That works out as:
 *
 * - **Basic**, 1 to 2B: Gemma 3 1B Instruct.
 * - **Balanced**, 3 to 4B: Gemma 3 4B Instruct.
 * - **Best Available**, 7 to 8B: Qwen3 8B. Gemma 3 is published at 1B, 4B, 12B
 *   and 27B with nothing in between, so there is no Gemma variant in the 7 to 8B
 *   band at all. Reaching up to 12B was rejected: at Q4_K_M it is around 7.3 GB,
 *   which on the 16 GB phone this tier targets leaves nothing like the headroom
 *   the tier logic insists on, and the app would get killed in the background
 *   constantly. Qwen3 8B sits exactly in the band.
 *
 * Licences differ across the tiers and that is worth being straight about.
 * Gemma models are under the Gemma Terms of Use, which permit redistribution
 * and commercial use but attach conditions including a use policy, so the terms
 * travel with the model. Qwen3 is Apache-2.0 outright. Nothing is bundled into
 * the app; every model is downloaded by the user from its official repository,
 * and both licences allow that plainly. Both appear on the Licenses screen.
 *
 * Q4_K_M throughout. Below Q4 these models start losing the thing this app
 * leans on hardest, which is following instructions carefully, and the
 * guardrails in SystemPrompts are instructions.
 *
 * Sizes and SHA-256 hashes were read from the Hugging Face API at build time,
 * not copied from documentation, and the hash is verified before a download is
 * ever activated.
 */
object ModelCatalog {

    val basic = TierModel(
        id = "gemma-3-1b-it-q4km",
        tier = Tier.BASIC,
        displayName = "Gemma 3 1B",
        parameterLabel = "1B",
        quantisation = "Q4_K_M",
        downloadBytes = 806_058_272L,
        contextTokens = 4096,
        licence = "Gemma Terms of Use",
        sourceUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
        format = ChatFormat.GEMMA,
    )

    val balanced = TierModel(
        id = "gemma-3-4b-it-q4km",
        tier = Tier.BALANCED,
        displayName = "Gemma 3 4B",
        parameterLabel = "4B",
        quantisation = "Q4_K_M",
        downloadBytes = 2_489_894_016L,
        contextTokens = 6144,
        licence = "Gemma Terms of Use",
        sourceUrl = "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
        sha256 = "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19",
        format = ChatFormat.GEMMA,
    )

    val best = TierModel(
        id = "qwen3-8b-q4km",
        tier = Tier.BEST,
        displayName = "Qwen3 8B",
        parameterLabel = "8B",
        quantisation = "Q4_K_M",
        downloadBytes = 5_027_784_512L,
        contextTokens = 8192,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q4_K_M.gguf",
        sha256 = "120307ba529eb2439d6c430d94104dabd578497bc7bfe7e322b5d9933b449bd4",
        format = ChatFormat.QWEN,
    )

    val all = listOf(basic, balanced, best)

    fun forTier(tier: Tier): TierModel = when (tier) {
        Tier.BASIC -> basic
        Tier.BALANCED -> balanced
        Tier.BEST -> best
    }

    fun byId(id: String): TierModel? = all.firstOrNull { it.id == id }
}
