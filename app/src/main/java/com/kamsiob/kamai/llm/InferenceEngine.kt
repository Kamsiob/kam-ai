package com.kamsiob.kamai.llm

import android.content.Context
import android.os.PowerManager
import android.util.Log
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
 * session, so serialising access is not an optimization, it is a correctness
 * requirement.
 */
class InferenceEngine(
    private val context: Context,
    private val thermal: ThermalWatcher = ThermalWatcher(context),
) : ModelRuntime {

    /** One thread, one model, one generation at a time. */
    private val nativeDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kamai-inference").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val loadLock = Mutex()

    /**
     * Called after each generation with the model that produced it and the decode
     * rate in tokens per second.
     *
     * The app has always measured this and only ever written it to logcat. Item
     * 22 asks for a speed figure with real numbers behind it, and the honest
     * number is the one this phone just produced, not one measured on somebody
     * else's hardware and shipped in a table.
     */
    var onMeasured: ((modelId: String, decodeTokensPerSecond: Double) -> Unit)? = null

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

    override suspend fun load(model: TierModel, file: File): Result<Unit> = loadLock.withLock {
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

            val loadStart = System.nanoTime()
            val failure = LlamaBridge.nativeLoad(
                path = file.absolutePath,
                nCtx = contextTokens,
                nThreads = threadCount(),
                nThreadsBatch = batchThreadCount(),
                nGpuLayers = 0,
            )
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000.0
            Log.i("KamPerf", "load model=${model.id} ctx=$contextTokens threads=${threadCount()}/${batchThreadCount()} in ${"%.0f".format(loadMs)}ms")

            if (failure.isNotEmpty()) {
                _state.value = EngineState.Failed(failure)
                Result.failure(IllegalStateException(failure))
            } else {
                _state.value = EngineState.Ready(model, contextTokens)
                Result.success(Unit)
            }
        }
    }

    override suspend fun unload() = withContext(nativeDispatcher) {
        if (LlamaBridge.ensureLibraryLoaded() == null && LlamaBridge.nativeIsModelLoaded()) {
            LlamaBridge.nativeUnload()
        }
        _state.value = EngineState.NoModel
    }

    /** Moderate pressure: free the KV cache, keep the model mmapped. */
    override suspend fun releaseContext() = withContext(nativeDispatcher) {
        if (LlamaBridge.ensureLibraryLoaded() == null && LlamaBridge.nativeIsContextLoaded()) {
            LlamaBridge.nativeReleaseContext()
        }
    }

    /**
     * Streams a response to [prompt].
     *
     * The whole prompt is passed every time, so the system prompt is genuinely
     * re-injected on every request, which is what keeps a small model from
     * drifting out of its guardrails after a few turns. It is not re-computed
     * every time: nativeIngest keeps the longest prefix already in the KV cache
     * and decodes only what is new (issue #38), which is the difference between
     * an eleven second wait and under a second on an ongoing turn.
     */
    /**
     * What to say about the phone being warm, once per episode, or null.
     *
     * Exposed here because the watcher is internal to the engine and the sentence
     * belongs on screen. The engine deliberately does not show it itself: the
     * engine has no screen, and a notice raised from here would appear whether or
     * not anybody was looking.
     */
    fun thermalNotice(): String? = thermal.warningMessage()

    fun generate(
        prompt: String,
        mode: Mode,
        maxTokens: Int = 1024,
        onStop: (StopReason) -> Unit = {},
    ): Flow<Chunk> = callbackFlow {
        val values = Sampling.forMode(mode)

        // Cleared per generation, so a stop from the previous answer cannot mark
        // this one as stopped.
        userStopRequested = false

        // Rebuild the context if it was released under memory pressure. The
        // model weights are still mmapped, so this is quick, and it is why a
        // moderate-pressure release costs at most a slightly slower next reply.
        val ensured = LlamaBridge.nativeEnsureContext()
        if (ensured.isNotEmpty()) {
            onStop(StopReason.Failed(ensured))
            close()
            return@callbackFlow
        }

        // The context is deliberately not reset here: nativeIngest keeps the
        // longest common prefix already in the KV cache and decodes only the new
        // tokens, so a long conversation does not re-prefill its whole history
        // every turn (issue #38). A fresh or divergent conversation simply finds a
        // short common prefix and decodes the rest.
        LlamaBridge.nativeConfigureSampler(
            temperature = values.temperature,
            topP = values.topP,
            minP = values.minP,
            topK = values.topK,
            repeatPenalty = values.repeatPenalty,
            repeatLastN = values.repeatLastN,
            seed = SEED_ANY,
        )

        val prefillStart = System.nanoTime()
        val ingested = LlamaBridge.nativeIngest(prompt, addSpecial = false)
        val prefillMs = (System.nanoTime() - prefillStart) / 1_000_000.0
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
        // Stopping while the prompt is still being read in is a stop, not a
        // fault. It used to surface as "Something went wrong reading that", which
        // is both untrue and alarming: the user did it on purpose.
        if (ingested == ABORTED) {
            userStopRequested = true
            onStop(StopReason.UserStopped)
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
        val guard = StreamGuard()
        val decodeStart = System.nanoTime()
        // Time to first token: what a user actually waits through before anything
        // appears. The generateStart is captured at the top of the flow.
        var firstTokenMs = -1.0

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

            // The guard holds back any tail that could still become a control
            // marker, so a marker typed out across several tokens stops the
            // answer instead of appearing in it (issue #49).
            val step = guard.accept(piece)
            if (step.emit.isNotEmpty()) {
                if (firstTokenMs < 0) {
                    firstTokenMs = (System.nanoTime() - prefillStart) / 1_000_000.0
                }
                produced++
                trySend(Chunk(step.emit))
            }
            if (step.stop) {
                reason = StopReason.Finished
                break
            }
        }

        // Settle the reason before flushing, because whether the held tail is
        // worth showing depends on it.
        if (produced >= maxTokens) reason = StopReason.Finished
        // A user stop is now reported explicitly rather than inferred from the
        // coroutine being canceled. requestStop only raises the native abort
        // flag, which makes nativeNextToken return null and looks exactly like a
        // model that finished, so without this a stopped answer was recorded as
        // a complete one. Cancellation still counts, for the case where the whole
        // screen goes away mid-answer. See issue #40.
        if (userStopRequested || !isActive) reason = StopReason.UserStopped

        // Whatever is still held back was ordinary text after all.
        if (reason != StopReason.UserStopped) {
            guard.flush().takeIf { it.isNotEmpty() }?.let { trySend(Chunk(it)) }
        }

        // An answer with nothing in it is never shown as one.
        //
        // Seen on the device: Logic Partner was given a sound argument and the
        // model emitted end-of-generation immediately, decoding zero tokens. The
        // transcript rendered an empty bubble, which is the worst way to fail,
        // because it looks like the app broke rather than like the model having
        // nothing to say, and there is no way to tell those apart from the outside.
        //
        // The model doing this is a separate matter and is tracked as its own
        // issue. Whatever it does, an empty bubble is the app's fault, and saying
        // plainly that nothing came back is both true and something a person can
        // act on.
        if (produced == 0 && reason == StopReason.Finished) {
            trySend(Chunk("No answer came back. Try sending that again."))
        }

        // Measured performance for this generation. Read with:
        //   adb logcat -s KamPerf
        // Prefill is prompt ingestion (tokens/s over the whole prompt); decode is
        // generation (tokens/s), which is the number a user actually feels.
        val decodeMs = (System.nanoTime() - decodeStart) / 1_000_000.0
        val decodeTps = if (decodeMs > 0) produced * 1000.0 / decodeMs else 0.0
        val prefillTps = if (prefillMs > 0 && ingested > 0) ingested * 1000.0 / prefillMs else 0.0
        // The model id is on every line on purpose. A measurement that does not
        // say what it measured is worse than none: a whole run was read as one
        // model's figures when a finished download had quietly made another one
        // active, and it was caught only by the numbers matching the other
        // tier's recorded baseline.
        val runningModel = (_state.value as? EngineState.Ready)?.model?.id ?: "unknown"
        Log.i(
            "KamPerf",
            "model=$runningModel mode=$mode" +
                " TTFT=${"%.0f".format(firstTokenMs)}ms prefill=${ingested}tok/${"%.0f".format(prefillMs)}ms" +
                " (${"%.1f".format(prefillTps)} tok/s) decode=${produced}tok/${"%.0f".format(decodeMs)}ms" +
                " (${"%.1f".format(decodeTps)} tok/s) threads=${threadCount()} ctx=${contextSize}",
        )

        // Short generations say more about load and warm-up than about speed, so
        // they are not worth recording. Fifty tokens is a couple of sentences.
        val measuredModel = (_state.value as? EngineState.Ready)?.model
        if (measuredModel != null && produced >= 50 && decodeTps > 0) {
            onMeasured?.invoke(measuredModel.id, decodeTps)
        }

        onStop(reason)
        close()

        awaitClose { LlamaBridge.nativeRequestStop() }
    }.flowOn(nativeDispatcher)

    /**
     * True from the moment the user asks to stop until the next generation
     * begins. Volatile because it is written from whichever thread taps stop and
     * read on the native dispatcher.
     */
    @Volatile
    private var userStopRequested = false

    /**
     * Interrupts a decode that is already in flight, and records that it was the
     * user who did it.
     *
     * The flag matters as much as the abort. Raising the native flag alone ends
     * the loop in a way indistinguishable from the model reaching its own end,
     * so the answer would be filed as complete and the honest "You stopped this
     * one." line would never appear.
     */
    fun requestStop() {
        userStopRequested = true
        // Guarded, because this is a teardown path and teardown runs whether or
        // not anything ever started. The overlay calls it from `onPause` every
        // time it closes, including when the user opened the assistant, changed
        // their mind, and never asked anything. In a process where the native
        // library was never loaded, that threw UnsatisfiedLinkError straight out
        // of onPause and took the app down with "Kam AI keeps stopping".
        //
        // Long-standing rather than new, but it only became easy to reach once
        // the overlay gained a handle that opens the app (#47) and a voice-first
        // mode that opens without typing (#46). Nothing to stop is not an error.
        if (LlamaBridge.isLibraryLoaded) LlamaBridge.nativeRequestStop()
    }

    /**
     * The current conversation's cached state, or null when there is nothing
     * worth saving (#52).
     *
     * On the native dispatcher, like every other call into the library, because
     * a save that ran beside a decode would read a context that is being written
     * to. Plaintext: encrypting it is [ConversationState]'s job, and doing it
     * here would put a cipher in the middle of the inference engine.
     */
    /**
     * Prefills a mode's fixed instruction block into the KV cache, so the user
     * does not pay for it on their first message (#38).
     *
     * **Why this is the whole fix for cold start.** Time to first token was about
     * thirty seconds on a cold app, and measuring it showed prefill accounted for
     * 99.5 percent of that: 31438 ms of a 31588 ms wait, decoding roughly 1100
     * tokens at 37 tokens per second. Nothing was broken. Batching was working,
     * and 37 tokens per second of prefill against 3 of decode is the ordinary
     * ratio for this CPU. The prompt is simply large and the phone is simply slow,
     * so there was no bug to fix and no clever trick to find.
     *
     * What there was, was a wait happening at the worst possible moment. Almost
     * every one of those tokens is the mode's system prompt, which is identical on
     * every first message and known long before the user types. So it is decoded
     * while they are still reading the screen, and their first message then only
     * has to prefill itself.
     *
     * Deliberately fire and forget. It runs off the main path, ignores its own
     * failures, and holds no locks the send path needs: a warm-up that delayed a
     * real message would have traded the problem for a worse one. If it has not
     * finished when the user sends, the prefix diffing in nativeIngest reuses
     * whatever part of it landed, so a partial warm-up is still a partial win.
     */
    suspend fun warmUp(systemPrompt: String): Unit = withContext(nativeDispatcher) {
        runCatching {
            if (!LlamaBridge.nativeIsLoaded()) return@runCatching
            val ensured = LlamaBridge.nativeEnsureContext()
            if (ensured.isNotEmpty()) return@runCatching
            val started = System.nanoTime()
            val tokens = LlamaBridge.nativeIngest(systemPrompt, addSpecial = false)
            val ms = (System.nanoTime() - started) / 1_000_000.0
            android.util.Log.i(
                "KamPerf",
                "warmup=${tokens}tok/${"%.0f".format(ms)}ms",
            )
        }
        Unit
    }

    /**
     * Runs [block] on the engine and leaves the KV cache holding what it held
     * before, so a background pass does not cost the user a re-prefill (#71).
     *
     * Titling and memory extraction run their own prompts through the same single
     * llama.cpp sequence as the conversation. When one finishes, the cache holds
     * that prompt instead: measured at 268 tokens of titling prompt where the
     * conversation was 1700 tokens long, so the user's next message re-prefilled
     * everything.
     *
     * A snapshot is a few megabytes of memcpy, which is cheap next to a full
     * re-prefill of a long conversation. The blob carries the token list as well
     * as the cache, and `nativeRestoreState` reassigns the native side's
     * `cached_tokens` from it, so prefix diffing stays consistent afterwards
     * rather than believing it holds something it does not.
     *
     * The restore is NonCancellable on purpose. The path that most needs the
     * cache back is the one where the caller was canceled part way, and a restore
     * that is skipped because its scope died leaves the cache holding a titling
     * prompt, which is the exact defect this exists to prevent.
     */
    suspend fun <T> preservingCache(block: suspend () -> T): T {
        val saved = saveState()
        return try {
            block()
        } finally {
            if (saved != null) {
                withContext(kotlinx.coroutines.NonCancellable) { restoreState(saved) }
            }
        }
    }

    suspend fun saveState(): ByteArray? = withContext(nativeDispatcher) {
        if (LlamaBridge.isLibraryLoaded && LlamaBridge.nativeIsLoaded()) {
            LlamaBridge.nativeSaveState()
        } else {
            null
        }
    }

    /** Restores a blob from [saveState]. True when the context now holds it. */
    suspend fun restoreState(blob: ByteArray): Boolean = withContext(nativeDispatcher) {
        LlamaBridge.isLibraryLoaded &&
            LlamaBridge.nativeIsLoaded() &&
            LlamaBridge.nativeRestoreState(blob) > 0
    }

    suspend fun countTokens(text: String): Int = withContext(nativeDispatcher) {
        // The library check has to come first. This method already has a
        // deliberate fallback for "nothing is loaded", but the guard was itself a
        // native call, so in the one case the fallback exists for, a process with
        // no library at all, it threw instead of falling back.
        if (LlamaBridge.isLibraryLoaded && LlamaBridge.nativeIsLoaded()) {
            LlamaBridge.nativeCountTokens(text).coerceAtLeast(0)
        } else {
            PromptBuilder.roughTokenCount(text)
        }
    }

    val contextSize: Int
        get() = (_state.value as? EngineState.Ready)?.contextTokens ?: 0

    /** ModelRuntime: whether the model weights are resident right now. The KV
     *  context may have been released under pressure; the engine rebuilds it
     *  transparently on the next generation.
     *
     *  Ensures the native library is loaded before asking it anything. The
     *  manager checks this before any load, which can be the very first native
     *  call in the process, so it must not assume the library is already up. */
    override val isLoaded: Boolean
        get() = LlamaBridge.ensureLibraryLoaded() == null && LlamaBridge.nativeIsModelLoaded()

    /**
     * How many threads to decode with. On a big.LITTLE phone, spilling onto the
     * slow efficiency cores makes them stragglers at every layer barrier, so this
     * counts only the performance cores (the fastest frequency cluster) rather
     * than all cores. It still leaves headroom so the UI does not stutter.
     *
     * `debug.kamai.threads` overrides it for on-device measurement:
     *   adb shell setprop debug.kamai.threads 4
     */
    private fun threadCount(): Int {
        sysPropInt("debug.kamai.threads")?.let { return it.coerceIn(1, 8) }
        // Decode is memory-bandwidth bound, so past about four threads extra cores
        // do not read weights any faster; they just contend for bandwidth, stall
        // on the layer barrier, and heat the phone. Measured on a Tensor G5 (2 +
        // 5 + 1 clusters): four threads decoded ~30 percent faster than six and
        // far faster than eight. So use the performance cores but cap at four.
        // See DECISIONS.md for the figures.
        return performanceCoreCount().coerceIn(2, 4)
    }

    /**
     * Threads for prefill (prompt ingestion). Unlike decode, prefill is a
     * compute-bound matrix multiply that parallelises well, so it uses all the
     * performance cores rather than the decode cap of four. This directly cuts
     * time to first token, which the prefill dominates. See DECISIONS.md (#38).
     */
    private fun batchThreadCount(): Int {
        sysPropInt("debug.kamai.batchthreads")?.let { return it.coerceIn(1, 8) }
        return performanceCoreCount().coerceIn(4, 8)
    }

    /**
     * The number of performance cores: cores whose maximum frequency is in the
     * top cluster, i.e. everything faster than the slowest (efficiency) cluster.
     * Reads cpufreq; falls back to availableProcessors minus two little cores.
     */
    private fun performanceCoreCount(): Int {
        val freqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toLong()
            }.getOrNull()
        }
        if (freqs.isEmpty()) return (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(2)
        val slowest = freqs.min()
        // Cores above the slowest cluster are the performance cores. When every
        // core is the same speed (a uniform SoC) they all count.
        val perf = freqs.count { it > slowest }
        return if (perf > 0) perf else freqs.size
    }

    private fun sysPropInt(key: String): Int? = runCatching {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java)
        (m.invoke(null, key) as String).takeIf { it.isNotBlank() }?.trim()?.toInt()
    }.getOrNull()

    private companion object {
        const val OVER_LENGTH = -3

        /**
         * The prompt was still being read in when the user pressed stop.
         * llama.cpp reports an aborted decode, which is not a fault: the abort
         * flag is ours and the user raised it on purpose.
         */
        const val ABORTED = -5
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

    /**
     * What to tell the user, once, when the phone has become warm enough to
     * change how the application behaves. Null when there is nothing new to say.
     *
     * **This used to have no callers at all**, which meant the user was told
     * nothing at LIGHT and nothing at MODERATE either, while the context was
     * quietly shrinking at MODERATE. Answers got slower and shorter and the
     * application said nothing about why, which is the failure #134 describes,
     * and it was worse than the issue claimed.
     *
     * LIGHT now says something, because LIGHT is where the phone starts throttling
     * and where sustained use actually lands: measured over an evening of
     * continuous generation, the platform reported LIGHT and never rose further.
     *
     * **It says it once per episode, not once per turn.** Nagging is banned, and a
     * warm phone stays warm for many turns. The rule is to speak when the status
     * rises above the highest already announced, and to reset once the phone is
     * cool again, so a user who warms the phone twice in a session hears it twice
     * and a user who warms it once hears it once.
     */
    private var announced: Int = PowerManager.THERMAL_STATUS_NONE

    fun warningMessage(): String? {
        val now = powerManager.currentThermalStatus
        if (now <= PowerManager.THERMAL_STATUS_NONE) {
            announced = PowerManager.THERMAL_STATUS_NONE
            return null
        }
        if (now <= announced) return null
        announced = now
        return when (now) {
            PowerManager.THERMAL_STATUS_LIGHT ->
                "Your phone is warming up, so answers will come more slowly for a while."
            PowerManager.THERMAL_STATUS_MODERATE ->
                "Your phone is warm, so answers will be shorter and slower for a bit."
            else ->
                "Your phone is hot. Answers will be short until it cools down."
        }
    }
}
