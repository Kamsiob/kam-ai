package com.kamsiob.kamai.llm

import android.content.Context
import android.os.PowerManager
import com.kamsiob.kamai.data.Mode
import com.kamsiob.kamai.model.TierModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Owns the loaded model and turns requests into streams of text.
 *
 * All native work runs on one dedicated thread. llama.cpp holds a single global
 * session, so serialising access is not an optimisation, it is a correctness
 * requirement.
 */
class InferenceEngine(
    private val context: Context,
    private val thermal: ThermalWatcher = ThermalWatcher(context),
) {

    /** One thread, one model, one generation at a time. */
    private val nativeDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kamai-inference").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val loadLock = Mutex()

    private val _state = MutableStateFlow<EngineState>(EngineState.NoModel)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    sealed interface EngineState {
        data object NoModel : EngineState
        data object Loading : EngineState
        data class Ready(val model: TierModel, val contextTokens: Int) : EngineState
        data class Failed(val reason: String) : EngineState
    }

    /** Why a response stopped, when it did not simply finish. */
    sealed interface StopReason {
        data object Finished : StopReason
        data object UserStopped : StopReason
        data class OutOfRoom(val message: String) : StopReason
        data class Overheating(val message: String) : StopReason
        data class Failed(val message: String) : StopReason
    }

    data class Chunk(val text: String)

    suspend fun load(model: TierModel, file: File): Result<Unit> = loadLock.withLock {
        withContext(nativeDispatcher) {
            LlamaBridge.ensureLibraryLoaded()?.let { reason ->
                _state.value = EngineState.Failed(reason)
                return@withContext Result.failure(IllegalStateException(reason))
            }

            if (!file.exists()) {
                val reason = "That model is not on this phone any more. Download it again in Settings."
                _state.value = EngineState.Failed(reason)
                return@withContext Result.failure(IllegalStateException(reason))
            }

            _state.value = EngineState.Loading

            if (LlamaBridge.nativeIsLoaded()) LlamaBridge.nativeUnload()

            // A context this size is a memory cost as well as a speed cost, so
            // it shrinks when the phone is already hot rather than being fixed.
            val contextTokens = thermal.contextFor(model.contextTokens)

            val failure = LlamaBridge.nativeLoad(
                path = file.absolutePath,
                nCtx = contextTokens,
                nThreads = threadCount(),
                nGpuLayers = 0,
            )

            if (failure.isNotEmpty()) {
                _state.value = EngineState.Failed(failure)
                Result.failure(IllegalStateException(failure))
            } else {
                _state.value = EngineState.Ready(model, contextTokens)
                Result.success(Unit)
            }
        }
    }

    suspend fun unload() = withContext(nativeDispatcher) {
        if (LlamaBridge.nativeIsLoaded()) LlamaBridge.nativeUnload()
        _state.value = EngineState.NoModel
    }

    /**
     * Streams a response to [prompt].
     *
     * The whole prompt is re-ingested each time rather than kept warm across
     * turns. It costs a prefill on every message, but it means the system
     * prompt is genuinely re-injected every request, which is what keeps a small
     * model from drifting out of its guardrails after a few turns.
     */
    fun generate(
        prompt: String,
        mode: Mode,
        maxTokens: Int = 1024,
        onStop: (StopReason) -> Unit = {},
    ): Flow<Chunk> = callbackFlow {
        val values = Sampling.forMode(mode)

        LlamaBridge.nativeResetContext()
        LlamaBridge.nativeConfigureSampler(
            temperature = values.temperature,
            topP = values.topP,
            minP = values.minP,
            topK = values.topK,
            repeatPenalty = values.repeatPenalty,
            repeatLastN = values.repeatLastN,
            seed = SEED_ANY,
        )

        val ingested = LlamaBridge.nativeIngest(prompt, addSpecial = false)
        if (ingested == OVER_LENGTH) {
            onStop(
                StopReason.OutOfRoom(
                    "That is longer than this model can hold at once. Try a shorter " +
                        "message, or start a new conversation.",
                ),
            )
            close()
            return@callbackFlow
        }
        if (ingested < 0) {
            onStop(StopReason.Failed("Something went wrong reading that. Try again."))
            close()
            return@callbackFlow
        }

        var produced = 0
        var reason: StopReason = StopReason.Finished

        while (isActive && produced < maxTokens) {
            // Checked every few tokens rather than every token, because reading
            // thermal status is not free.
            if (produced % THERMAL_CHECK_EVERY == 0) {
                thermal.criticalMessage()?.let { message ->
                    reason = StopReason.Overheating(message)
                    break
                }
            }

            val piece = LlamaBridge.nativeNextToken()
            if (piece == null) {
                reason = StopReason.Finished
                break
            }
            if (PromptBuilder.isStopMarker(piece)) {
                reason = StopReason.Finished
                break
            }

            produced++
            trySend(Chunk(piece))
        }

        if (produced >= maxTokens) reason = StopReason.Finished
        if (!isActive) reason = StopReason.UserStopped

        onStop(reason)
        close()

        awaitClose { LlamaBridge.nativeRequestStop() }
    }.flowOn(nativeDispatcher)

    /** Interrupts a decode that is already in flight. */
    fun requestStop() = LlamaBridge.nativeRequestStop()

    suspend fun countTokens(text: String): Int = withContext(nativeDispatcher) {
        if (LlamaBridge.nativeIsLoaded()) {
            LlamaBridge.nativeCountTokens(text).coerceAtLeast(0)
        } else {
            PromptBuilder.roughTokenCount(text)
        }
    }

    val contextSize: Int
        get() = (_state.value as? EngineState.Ready)?.contextTokens ?: 0

    /**
     * Leaves at least two cores for the rest of the phone. Using every core
     * makes the UI stutter while a response streams, which reads as the app
     * being broken rather than the model being busy.
     */
    private fun threadCount(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return (cores - 2).coerceIn(2, 6)
    }

    private companion object {
        const val OVER_LENGTH = -3
        const val THERMAL_CHECK_EVERY = 32
        const val SEED_ANY = -1
    }
}

/**
 * Watches thermal status so a long answer does not cook the phone.
 *
 * The app degrades rather than pushes on: a smaller context when it starts warm,
 * and a plain sentence and an early stop when it gets genuinely hot. It says
 * what is happening in ordinary words rather than showing a temperature.
 */
class ThermalWatcher(context: Context) {

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** A shorter context when the phone is already warm. */
    fun contextFor(requested: Int): Int = when (powerManager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE,
        PowerManager.THERMAL_STATUS_LIGHT,
        -> requested

        PowerManager.THERMAL_STATUS_MODERATE -> (requested * 3) / 4
        else -> requested / 2
    }

    /** Non-null when generation should stop, carrying the sentence to show. */
    fun criticalMessage(): String? = when (powerManager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> "Your phone is getting hot, so Kam AI stopped early. Give it a minute and ask again."

        else -> null
    }

    /** Shown before a long job when the phone is already warm. */
    fun warningMessage(): String? =
        if (powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            "Your phone is warm, so answers will be shorter for a bit."
        } else {
            null
        }
}
