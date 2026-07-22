package com.kamsiob.kamai.model

/**
 * The models Kam AI offers, one per tier.
 *
 * All three are Qwen3 at Q4_K_M, downloaded from their official Hugging Face
 * repositories. The reasoning, recorded here because it is the kind of choice
 * that looks arbitrary a year later:
 *
 * - **One family, one licence, one prompt format.** Qwen3 is Apache 2.0, which
 *   permits redistribution outright, so there is no licence asterisk on any
 *   tier. Mixing families would have meant carrying two or three chat templates
 *   and testing the guardrails separately against each.
 * - **Q4_K_M rather than a smaller quantisation.** Below Q4 these models start
 *   losing exactly the thing this app leans on, which is following instructions
 *   carefully, and the guardrails in [SystemPrompts] are instructions.
 * - **Thinking is off.** Qwen3 can emit a reasoning block before answering. On a
 *   phone that means a long wait staring at nothing before the first visible
 *   token, which fights the streaming response the design calls for. The prompt
 *   builder closes the thinking block immediately so answers start straight
 *   away.
 *
 * Sizes and hashes were read from the Hugging Face API at build time, not
 * copied from documentation. The hash is verified before a download is ever
 * activated.
 */
object ModelCatalog {

    val basic = TierModel(
        id = "qwen3-1.7b-q4km",
        tier = Tier.BASIC,
        displayName = "Qwen3 1.7B",
        parameterLabel = "1.7B",
        quantisation = "Q4_K_M",
        downloadBytes = 1_107_409_472L,
        contextTokens = 4096,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
        sha256 = "b139949c5bd74937ad8ed8c8cf3d9ffb1e99c866c823204dc42c0d91fa181897",
    )

    val balanced = TierModel(
        id = "qwen3-4b-q4km",
        tier = Tier.BALANCED,
        displayName = "Qwen3 4B",
        parameterLabel = "4B",
        quantisation = "Q4_K_M",
        downloadBytes = 2_497_281_312L,
        contextTokens = 6144,
        licence = "Apache-2.0",
        sourceUrl = "https://huggingface.co/unsloth/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
        sha256 = "f6f851777709861056efcdad3af01da38b31223a3ba26e61a4f8bf3a2195813a",
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
    )

    val all = listOf(basic, balanced, best)

    fun forTier(tier: Tier): TierModel = when (tier) {
        Tier.BASIC -> basic
        Tier.BALANCED -> balanced
        Tier.BEST -> best
    }

    fun byId(id: String): TierModel? = all.firstOrNull { it.id == id }
}
