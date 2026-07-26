package com.kamsiob.kamai.llm

/**
 * The Kotlin side of the llama.cpp bridge.
 *
 * Every method here maps one to one onto a function in `kamai_llama.cpp`. The
 * generation loop deliberately lives above this class rather than inside the
 * native layer, so that streaming, stopping, and thermal backoff are all
 * decided in Kotlin alongside the rest of the app's behavior.
 *
 * This object is not thread safe by itself. The native side holds a mutex so
 * calls cannot corrupt each other, but callers should still funnel work through
 * a single dispatcher. [com.kamsiob.kamai.llm.InferenceEngine] does that.
 */
internal object LlamaBridge {

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var loadFailure: String? = null

    /**
     * Whether libkamai.so is already loaded, without trying to load it.
     *
     * For callers that only want to tidy up. Every `native*` method below throws
     * `UnsatisfiedLinkError` when the library was never loaded, and a teardown
     * path asking generation to stop in a process that never started any has
     * nothing to gain from loading a 100 MB library to find that out.
     */
    val isLibraryLoaded: Boolean get() = libraryLoaded

    /**
     * Loads libkamai.so. Returns null on success, or a plain-language reason on
     * failure. A missing native library is not recoverable, but it should still
     * say something a person can act on rather than crashing.
     */
    fun ensureLibraryLoaded(): String? {
        if (libraryLoaded) return null
        loadFailure?.let { return it }

        synchronized(this) {
            if (libraryLoaded) return null
            loadFailure?.let { return it }
            return try {
                System.loadLibrary("kamai")
                libraryLoaded = true
                null
            } catch (e: UnsatisfiedLinkError) {
                val reason =
                    "This build of Kam AI does not have the parts it needs for your phone's processor. " +
                        "Reinstalling from the official release should fix it."
                loadFailure = reason
                reason
            }
        }
    }

    external fun nativeSystemInfo(): String

    /** Returns an empty string on success, or a plain reason on failure. */
    external fun nativeLoad(path: String, nCtx: Int, nThreads: Int, nThreadsBatch: Int, nGpuLayers: Int): String

    external fun nativeConfigureSampler(
        temperature: Float,
        topP: Float,
        minP: Float,
        topK: Int,
        repeatPenalty: Float,
        repeatLastN: Int,
        seed: Int,
    )

    /** Tokens consumed, or negative on failure. -3 means the text does not fit. */
    external fun nativeIngest(text: String, addSpecial: Boolean): Int

    /** The next piece of text, or null at end of generation. */
    external fun nativeNextToken(): String?

    external fun nativeRequestStop()
    external fun nativeResetContext()
    external fun nativeContextUsed(): Int
    external fun nativeContextSize(): Int
    external fun nativeCountTokens(text: String): Int
    external fun nativeChatTemplate(): String?
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean

    /**
     * The current conversation's KV state plus the tokens it describes, or null
     * when there is nothing cached (#52).
     *
     * Plaintext bytes: the caller encrypts them before they touch disk.
     */
    external fun nativeSaveState(): ByteArray?

    /**
     * Restores a blob from [nativeSaveState]. Returns the token count now
     * cached, or a negative value if nothing was restored, in which case the
     * context is left empty rather than half-filled.
     */
    external fun nativeRestoreState(blob: ByteArray): Int

    /** Frees the KV-cache context but keeps the model mmapped. Moderate pressure. */
    external fun nativeReleaseContext()
    /** Rebuilds the context if the model is loaded but the context was released. */
    external fun nativeEnsureContext(): String
    /** True when the model weights are resident, context or not. */
    external fun nativeIsModelLoaded(): Boolean
    /** True when the KV-cache context is currently allocated. */
    external fun nativeIsContextLoaded(): Boolean
}
