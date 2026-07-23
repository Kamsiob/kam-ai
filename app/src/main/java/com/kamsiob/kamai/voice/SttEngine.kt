package com.kamsiob.kamai.voice

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Speech to text over whisper.cpp.
 *
 * The memory discipline here is deliberate and is the voice half of the app's
 * one-model-at-a-time rule. A whisper model is loaded only for the moment it is
 * transcribing and unloaded immediately afterwards, so it never sits resident
 * alongside a language model that is generating. Transcription happens while the
 * user is talking and the language model is idle; generation happens after, when
 * whisper is already gone. The two peaks never overlap.
 *
 * @param onBeforeLoad a hook the caller uses to make room before whisper loads,
 *   for example releasing the language model's KV cache. Kept injectable so the
 *   coordination with the model manager is explicit and testable.
 */
class SttEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val threadCount: () -> Int = {
        (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
    },
    private val onBeforeLoad: suspend () -> Unit = {},
) {

    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Error(val message: String) : Result
    }

    /**
     * Transcribes [pcm] (16 kHz mono float) using the model file [modelFile].
     * Loads, transcribes, and unloads within this call, so nothing stays
     * resident afterwards.
     */
    suspend fun transcribe(
        modelFile: File,
        pcm: FloatArray,
        language: String = "auto",
    ): Result = withContext(dispatcher) {
        WhisperBridge.ensureLibraryLoaded()?.let { return@withContext Result.Error(it) }

        if (!modelFile.exists()) {
            return@withContext Result.Error(
                "The voice model is not installed. Download it in Settings, Voice.",
            )
        }
        if (pcm.isEmpty()) {
            return@withContext Result.Error("Nothing was recorded. Try again.")
        }

        onBeforeLoad()

        val loadError = WhisperBridge.nativeLoad(modelFile.absolutePath)
        if (loadError.isNotEmpty()) {
            return@withContext Result.Error(loadError)
        }

        try {
            // whisper.cpp uses "auto" for language detection via an empty code.
            val lang = if (language == "auto") "" else language
            val text = WhisperBridge.nativeTranscribe(pcm, threadCount(), lang).trim()
            if (text.isEmpty()) {
                Result.Error("That did not come through clearly. Try again in a quieter spot.")
            } else {
                Result.Ok(text)
            }
        } finally {
            // Always unload, even on failure, so a whisper model never lingers in
            // memory next to the language model.
            WhisperBridge.nativeFree()
        }
    }

    val isLoaded: Boolean
        get() = WhisperBridge.ensureLibraryLoaded() == null && WhisperBridge.nativeIsLoaded()
}
