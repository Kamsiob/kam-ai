package com.kamsiob.kamai.llm

import com.kamsiob.kamai.model.TierModel
import com.kamsiob.kamai.model.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * The single source of truth for which model is resident in memory, and the only
 * place that decides to load or unload one. Every other part of the app asks the
 * manager rather than calling load or unload itself, so the invariants below hold
 * everywhere by construction.
 *
 * The invariants, all enforced here:
 *
 * - **At most one model is ever resident.** Switching fully unloads the current
 *   model, and confirms the memory is released, before the next one is ever
 *   loaded. The two are never resident at once, not even briefly, because that
 *   doubles peak memory and is exactly what triggers the low memory killer.
 * - **Loading is lazy.** Nothing is loaded at startup. A model loads on first
 *   actual use, unloads when the app has been backgrounded a while, and unloads
 *   at once on system memory pressure, reloading transparently on the next use.
 * - **Loading is pressure-aware.** Before any load, the model's estimated
 *   requirement is checked against available memory plus a margin. If it will not
 *   fit, the load is refused with a plain explanation and, where possible, a
 *   smaller installed model to fall back to, rather than destabilising the phone.
 * - **Deleting is safe in every state.** A resident model is unloaded and its
 *   handles released before its file is removed; the active reference is cleared
 *   and pointed at another installed model or a usable no-model state; and a
 *   mid-download deletion cancels the download and cleans up the partial file.
 *   A deletion never leaves a dangling reference for a later launch to trip over.
 *
 * The manager is deliberately free of Android types so its whole decision surface
 * is testable with fakes. The Android wiring (memory gauge, file paths, the
 * repository) is injected.
 */
class ModelManager(
    private val runtime: ModelRuntime,
    private val gauge: MemoryGauge,
    private val scope: CoroutineScope,
    private val fileFor: (TierModel) -> File,
    private val activeModel: suspend () -> TierModel?,
    private val setActive: suspend (String?) -> Unit,
    private val installedModels: suspend () -> List<TierModel>,
) {

    sealed interface Status {
        /** Nothing installed or active. The app is usable, just cannot answer yet. */
        data object NoModel : Status

        /** A model is active but not resident. It will load on next use. */
        data class Idle(val model: TierModel) : Status

        /** A model is loading right now. The UI shows this so a slow first
         *  response is never a mystery. */
        data class Loading(val model: TierModel) : Status

        /** A model is resident and ready. */
        data class Loaded(val model: TierModel) : Status

        /** A load was refused because it would not fit. [smaller] is a smaller
         *  installed model to offer, when there is one. */
        data class Refused(
            val model: TierModel,
            val reason: String,
            val smaller: TierModel?,
        ) : Status

        /** A load was attempted and failed, with a plain reason. */
        data class Failed(val model: TierModel, val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.NoModel)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Serialises every load, unload, switch and delete so two can never race. */
    private val lock = Mutex()

    /** The id of the model actually resident, or null. The manager's own record,
     *  independent of the runtime, so it always knows what to release. */
    private var residentId: String? = null

    private var backgroundUnloadJob: Job? = null

    // Tunable memory model. See requiredBytes for the reasoning.
    private companion object {
        /** Fixed compute-buffer floor, on top of the KV cache. */
        const val COMPUTE_BASE_BYTES = 640L * 1024 * 1024
        /** The KV cache scales roughly with model size; this fraction of the
         *  weights, plus the compute base, matched the measured committed cost of
         *  E2B on device (about 1.1 GB for a 3.1 GB model). */
        const val KV_FRACTION = 0.20
        /** Headroom kept free after the anonymous buffers are allocated. */
        const val SAFETY_MARGIN_BYTES = 384L * 1024 * 1024
        /** Room the OS and foreground apps need beyond the model's whole working
         *  set, for the total-RAM sanity check. */
        const val OS_RESERVE_BYTES = 2L * 1024 * 1024 * 1024
        const val BACKGROUND_UNLOAD_MS = 90_000L
    }

    /**
     * Reads the active model without loading anything, and repairs a dangling
     * reference. Call at startup instead of loading: if the active model's file
     * is gone, the reference is cleared so a later use does not try to load a
     * file that is not there. Never loads.
     */
    suspend fun refreshActive() = lock.withLock {
        val model = activeModel()
        when {
            model == null -> {
                _status.value = Status.NoModel
            }
            !fileFor(model).exists() -> {
                // Dangling active reference, for example a model deleted out from
                // under a stale pointer. Clear it and fall back.
                setActive(null)
                val fallback = firstInstalledOrNull(exclude = model.id)
                setActive(fallback?.id)
                _status.value = fallback?.let { Status.Idle(it) } ?: Status.NoModel
            }
            else -> {
                _status.value = Status.Idle(model)
            }
        }
    }

    /**
     * Ensures the active model is resident, loading it if needed. This is the one
     * call sites use before generating. Lazy, memory-checked, and serialised.
     */
    suspend fun ensureLoaded(): Status = lock.withLock {
        val model = activeModel() ?: run {
            _status.value = Status.NoModel
            return Status.NoModel
        }

        if (runtime.isLoaded && residentId == model.id) {
            _status.value = Status.Loaded(model)
            return _status.value
        }

        loadLocked(model)
    }

    /** Loads [model], assuming the lock is held. Enforces the memory check and
     *  the one-resident rule. */
    private suspend fun loadLocked(model: TierModel): Status {
        val file = fileFor(model)
        if (!file.exists()) {
            setActive(null)
            _status.value = Status.NoModel
            return Status.NoModel
        }

        if (!fits(model)) {
            val smaller = smallerInstalledThatFits(model)
            _status.value = Status.Refused(model, refusalMessage(model, smaller), smaller)
            return _status.value
        }

        // The one-resident rule: whatever is loaded is released first, and we
        // confirm it, before the new model is loaded. Never both at once.
        if (runtime.isLoaded) {
            runtime.unload()
            residentId = null
        }
        check(!runtime.isLoaded) { "a model was still resident after unload" }

        _status.value = Status.Loading(model)
        val result = runtime.load(model, file)
        return if (result.isSuccess) {
            residentId = model.id
            _status.value = Status.Loaded(model)
            _status.value
        } else {
            residentId = null
            _status.value = Status.Failed(
                model,
                result.exceptionOrNull()?.message
                    ?: "That model could not be loaded. Try a smaller one.",
            )
            _status.value
        }
    }

    /**
     * Switches the active model to [model]. Fully unloads the current one and
     * confirms the memory is released; the new model is left idle and loads on
     * next use, so the two are never resident together.
     */
    suspend fun switchTo(model: TierModel) = lock.withLock {
        if (runtime.isLoaded) {
            runtime.unload()
            residentId = null
        }
        check(!runtime.isLoaded) { "a model was still resident after unload during switch" }
        setActive(model.id)
        _status.value = Status.Idle(model)
    }

    /**
     * Called when a download finishes. Never loads and never changes the active
     * model out from under the user: it only adopts the model as active when
     * there was no active model at all, which is the first-model case.
     */
    suspend fun onModelInstalled(model: TierModel) = lock.withLock {
        if (activeModel() == null) {
            setActive(model.id)
            _status.value = Status.Idle(model)
        }
        // Otherwise leave the running model exactly as it is.
    }

    /**
     * Called when a download is about to start. If a model is resident but idle,
     * it is unloaded to leave room for the download's memory and disk pressure,
     * and it reloads lazily on next use.
     */
    suspend fun onDownloadStarting() = lock.withLock {
        if (runtime.isLoaded) {
            runtime.unload()
            residentId = null
            activeModel()?.let { _status.value = Status.Idle(it) }
        }
    }

    /**
     * Deletes [model] safely from any state. [removeArtifact] removes the file
     * and the record; [cancelDownload] cancels and cleans up a partial download.
     * The manager handles unloading and the active reference so a deletion never
     * strands the app or leaves a dangling pointer.
     */
    suspend fun delete(
        model: TierModel,
        midDownload: Boolean,
        cancelDownload: suspend () -> Unit,
        removeArtifact: suspend () -> Unit,
    ) = lock.withLock {
        if (midDownload) cancelDownload()

        if (residentId == model.id || (runtime.isLoaded && activeModel()?.id == model.id)) {
            runtime.unload()
            residentId = null
        }

        removeArtifact()

        if (activeModel()?.id == model.id || activeModel() == null) {
            val fallback = firstInstalledOrNull(exclude = model.id)
            setActive(fallback?.id)
            _status.value = fallback?.let { Status.Idle(it) } ?: Status.NoModel
        }
    }

    /**
     * Moderate memory pressure: release the KV cache but keep the model mmapped,
     * so the conversation continues and only the next reply is slightly slower
     * while the context is rebuilt. The model stays resident and active.
     */
    fun onModeratePressure() {
        scope.launch {
            lock.withLock {
                if (runtime.isLoaded) runtime.releaseContext()
            }
        }
    }

    /** Severe memory pressure: unload the model entirely, keeping it active so
     *  the next use reloads it transparently. */
    fun onSeverePressure() {
        scope.launch {
            lock.withLock {
                if (runtime.isLoaded) {
                    runtime.unload()
                    residentId = null
                    activeModel()?.let { _status.value = Status.Idle(it) }
                }
            }
        }
    }

    /** The app went to the background. Schedule an unload after a quiet period so
     *  a brief trip away does not pay a reload, but a long one frees memory. */
    fun onBackgrounded() {
        backgroundUnloadJob?.cancel()
        backgroundUnloadJob = scope.launch {
            delay(BACKGROUND_UNLOAD_MS)
            lock.withLock {
                if (runtime.isLoaded) {
                    runtime.unload()
                    residentId = null
                    activeModel()?.let { _status.value = Status.Idle(it) }
                }
            }
        }
    }

    /** The app returned to the foreground. Cancel any pending background unload. */
    fun onForegrounded() {
        backgroundUnloadJob?.cancel()
        backgroundUnloadJob = null
    }

    val isResident: Boolean get() = runtime.isLoaded && residentId != null

    /**
     * Whether [model] is expected to fit in memory right now.
     *
     * Two checks, matching how the memory actually behaves:
     *
     * 1. The anonymous buffers (KV cache and compute) must fit in what is free
     *    right now. These are the allocations that cannot be reclaimed, so they
     *    are what a load can genuinely fail on.
     * 2. The whole working set (the mmapped weights plus those buffers) must fit
     *    the device's total RAM with room for the OS and foreground apps.
     *
     * The weights themselves are memory-mapped, so they do not need to sit in the
     * currently-free figure: the kernel pages them from disk and reclaims cached
     * apps as needed. An earlier version required the full weights to be free,
     * which on a normally-loaded 16 GB phone (often only 2 to 3 GB reported free)
     * refused every model, including the recommended one. That was the bug this
     * replaces. The total-RAM check still stops a model that is genuinely too big
     * for the phone from being loaded into an out-of-memory kill.
     */
    fun fits(model: TierModel): Boolean {
        if (gauge.lowMemory()) return false
        val anon = requiredBytes(model)
        val availableOk = gauge.availableBytes() >= anon + SAFETY_MARGIN_BYTES
        val workingSet = model.downloadBytes + anon + OS_RESERVE_BYTES
        val totalOk = gauge.totalBytes() >= workingSet
        return availableOk && totalOk
    }

    /**
     * The anonymous memory a load needs free right now: the KV cache and compute
     * buffers, not the weights. Grounded in measurement rather than theory: with
     * the weights memory-mapped, loading and running E2B (a 3.1 GB model) cost
     * about 1.1 GB of committed memory on device, which this estimate reproduces
     * (a compute-buffer floor plus a fraction of the weights for the KV cache).
     * The weights are reclaimable page cache and are covered by the total-RAM
     * check in [fits], not by this figure.
     */
    fun requiredBytes(model: TierModel): Long =
        COMPUTE_BASE_BYTES + (model.downloadBytes * KV_FRACTION).toLong()

    private fun refusalMessage(model: TierModel, smaller: TierModel?): String {
        // Distinguish "no room right now" from "too big for this phone at all".
        val tooBigForPhone = gauge.totalBytes() < model.downloadBytes + requiredBytes(model) + OS_RESERVE_BYTES
        val base = if (tooBigForPhone) {
            "${model.displayName} is too large to run on this phone."
        } else {
            "${model.displayName} needs a little more free memory than there is right now."
        }
        return if (smaller != null) {
            "$base You can switch to ${smaller.displayName}, which is smaller, or free up " +
                "memory by closing some apps and try again."
        } else if (tooBigForPhone) {
            "$base Try a smaller model."
        } else {
            "$base Close some apps to free memory and try again, or download a smaller model."
        }
    }

    private suspend fun firstInstalledOrNull(exclude: String?): TierModel? =
        installedModels().firstOrNull { it.id != exclude && fileFor(it).exists() }

    /** The largest installed model, other than [model], that would fit now. */
    private suspend fun smallerInstalledThatFits(model: TierModel): TierModel? =
        installedModels()
            .filter { it.id != model.id && it.downloadBytes < model.downloadBytes && fileFor(it).exists() }
            .filter { fits(it) }
            .maxByOrNull { it.downloadBytes }
}
