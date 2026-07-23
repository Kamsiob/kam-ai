package com.kamsiob.kamai.voice

import com.kamsiob.kamai.model.formatBytes

/**
 * The text-to-speech voices Kam AI can download, run through the sherpa-onnx
 * runtime (never the Android system voice). Two quality tiers: Piper voices as
 * the standard tier, good and light; and Kokoro as the premium tier, more
 * expressive but larger and offered only where the phone can run it responsively.
 *
 * Piper voices all share one espeak-ng phonemiser data set and one tokens file,
 * bundled once in the app, so a voice download is just its single model file.
 *
 * No Android dependencies, so the tiering is testable.
 */
enum class TtsKind { PIPER, KOKORO }

data class TtsVoice(
    val id: String,
    val displayName: String,
    val kind: TtsKind,
    /** "Female" or "Male", stated plainly rather than implied by a name. */
    val gender: String,
    val tier: String,
    val downloadBytes: Long,
    val sourceUrl: String,
    val sha256: String,
    val fileName: String,
    val description: String,
) {
    val downloadLabel: String get() = formatBytes(downloadBytes)
}

object TtsCatalog {

    /** Piper and its models are MIT; the espeak-ng data is GPL-3.0. */
    const val PIPER_LICENCE = "MIT, with GPL-3.0 espeak-ng data"

    private const val PIPER_BASE = "https://huggingface.co/csukuangfj"

    val AMY = TtsVoice(
        id = "piper-amy",
        displayName = "Amy",
        kind = TtsKind.PIPER,
        gender = "Female",
        tier = "Standard",
        downloadBytes = 63_104_657L,
        sourceUrl = "$PIPER_BASE/vits-piper-en_US-amy-low/resolve/main/en_US-amy-low.onnx",
        sha256 = "8275b02c37c4ce6483b26a91704807e6de0c3f0bf8068e5d3b3598ab0da4253e",
        fileName = "piper-amy.onnx",
        description = "A clear, even female voice.",
    )

    val RYAN = TtsVoice(
        id = "piper-ryan",
        displayName = "Ryan",
        kind = TtsKind.PIPER,
        gender = "Male",
        tier = "Standard",
        downloadBytes = 63_104_657L,
        sourceUrl = "$PIPER_BASE/vits-piper-en_US-ryan-low/resolve/main/en_US-ryan-low.onnx",
        sha256 = "2ad52f13bf0cbfa3bf0cc9afbab91e71485a6a3f6aee507cbc6c2c753b30d0d1",
        fileName = "piper-ryan.onnx",
        description = "A warm, steady male voice.",
    )

    val ALL = listOf(AMY, RYAN)

    fun byId(id: String): TtsVoice? = ALL.firstOrNull { it.id == id }
}
