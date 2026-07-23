package com.kamsiob.kamai.voice

/**
 * The Kotlin side of the whisper.cpp bridge. Each method maps one to one onto a
 * function in `kamai_whisper.cpp`, which lives in its own shared library,
 * libkamwhisper.so, separate from the llama.cpp library.
 *
 * Not thread safe by itself: the native side holds a mutex, but callers should
 * still funnel work through a single dispatcher, which [SttEngine] does.
 */
internal object WhisperBridge {

    @Volatile
    private var libraryLoaded = false

    @Volatile
    private var loadFailure: String? = null

    /** Loads libkamwhisper.so. Returns null on success, or a plain reason. */
    fun ensureLibraryLoaded(): String? {
        if (libraryLoaded) return null
        loadFailure?.let { return it }
        synchronized(this) {
            if (libraryLoaded) return null
            loadFailure?.let { return it }
            return try {
                System.loadLibrary("kamwhisper")
                libraryLoaded = true
                null
            } catch (e: UnsatisfiedLinkError) {
                val reason =
                    "This build of Kam AI does not have the voice parts it needs for your " +
                        "phone's processor. Reinstalling from the official release should fix it."
                loadFailure = reason
                reason
            }
        }
    }

    /** Returns an empty string on success, or a plain reason on failure. */
    external fun nativeLoad(path: String): String

    external fun nativeIsLoaded(): Boolean

    /** Transcribes 16 kHz mono float PCM. Returns the text, possibly empty. */
    external fun nativeTranscribe(pcm: FloatArray, nThreads: Int, language: String): String

    external fun nativeFree()
}
