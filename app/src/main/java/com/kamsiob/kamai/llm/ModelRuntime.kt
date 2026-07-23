package com.kamsiob.kamai.llm

import com.kamsiob.kamai.model.TierModel
import java.io.File

/**
 * The narrow contract the model manager needs from whatever actually holds a
 * model in memory. Kept small and separate from the full inference engine so the
 * manager's loading and unloading logic can be tested with a fake that records
 * the order of calls, without a device or a real model.
 */
interface ModelRuntime {
    /** True when a model is resident in memory right now. */
    val isLoaded: Boolean

    /** Loads [model] from [file]. Returns success, or a plain reason on failure. */
    suspend fun load(model: TierModel, file: File): Result<Unit>

    /** Unloads whatever is resident and releases its memory. Safe to call when nothing is loaded. */
    suspend fun unload()

    /**
     * Releases the KV cache but keeps the model resident. The moderate-pressure
     * step: the conversation continues, the next response rebuilds the context
     * and is a little slower, and no model reload is needed. Safe to call when
     * nothing is loaded.
     */
    suspend fun releaseContext()
}

/**
 * How much memory is free right now. Abstracted so the fit check can be tested
 * with known numbers rather than a real device's fluctuating memory.
 */
interface MemoryGauge {
    /** Bytes the system reports as available to the app without killing others. */
    fun availableBytes(): Long

    /** Total physical RAM. Used to check the whole working set fits the device. */
    fun totalBytes(): Long

    /** True when the system is already in a low-memory state. */
    fun lowMemory(): Boolean
}
