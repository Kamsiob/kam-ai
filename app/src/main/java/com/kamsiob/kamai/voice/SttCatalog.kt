package com.kamsiob.kamai.voice

import com.kamsiob.kamai.model.formatBytes

/**
 * The speech-to-text models Kam AI can download, tiered by device capability the
 * same way the language models are: a stronger phone is offered the larger, more
 * accurate whisper model. These are the multilingual whisper.cpp GGML models, so
 * speech in languages other than English still transcribes.
 *
 * No Android dependencies, so the tiering can be tested directly.
 */
data class SttModel(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    val downloadBytes: Long,
    val sourceUrl: String,
    val sha256: String,
    val fileName: String,
    /** One plain line about the tradeoff. */
    val description: String,
) {
    val downloadLabel: String get() = formatBytes(downloadBytes)
}

object SttCatalog {

    /** MIT (whisper.cpp) with the OpenAI Whisper models under MIT. */
    const val LICENCE = "MIT"

    private const val BASE_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main"

    val STANDARD = SttModel(
        id = "whisper-base",
        displayName = "Standard voice typing",
        sizeLabel = "148 MB",
        downloadBytes = 147_951_465L,
        sourceUrl = "$BASE_URL/ggml-base.bin",
        sha256 = "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
        fileName = "ggml-base.bin",
        description = "Good accuracy, quick, light on memory. Fits any phone.",
    )

    val BETTER = SttModel(
        id = "whisper-small",
        displayName = "Better voice typing",
        sizeLabel = "488 MB",
        downloadBytes = 487_601_967L,
        sourceUrl = "$BASE_URL/ggml-small.bin",
        sha256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
        fileName = "ggml-small.bin",
        description = "More accurate, especially in noise, but larger and slower.",
    )

    val ALL = listOf(STANDARD, BETTER)

    fun byId(id: String): SttModel? = ALL.firstOrNull { it.id == id }

    /**
     * The model to recommend for a phone with [totalRamGb] gigabytes. The better
     * model is only recommended where there is comfortable room for it alongside
     * a language model; smaller phones get the standard one.
     */
    fun recommendedFor(totalRamGb: Int): SttModel =
        if (totalRamGb >= 12) BETTER else STANDARD
}
