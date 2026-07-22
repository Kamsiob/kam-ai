package com.kamsiob.kamai.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 0 native smoke test.
 *
 * Proves the whole native path end to end on a real device: libkamai.so loads,
 * llama.cpp initialises, a GGUF file opens, a prompt tokenizes and decodes, and
 * the model produces tokens that come back across JNI as text.
 *
 * The model is a 1.2 MB story model. It is far too small to say anything
 * sensible, which is the point: this test is about the plumbing, not the
 * output. It travels inside the test APK as an asset and is copied out to a
 * real file at the start of the run, because llama.cpp needs a path on disk.
 * `tools/fetch_smoke_model.sh` puts the asset in place before building.
 */
@RunWith(AndroidJUnit4::class)
class LlamaBridgeSmokeTest {

    private companion object {
        const val MODEL_ASSET = "stories260K.gguf"
    }

    private lateinit var modelPath: String

    @Before
    fun setUp() {
        // The library has to come up before anything else, including tearDown.
        assertThat(LlamaBridge.ensureLibraryLoaded()).isNull()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val outFile = File(instrumentation.targetContext.cacheDir, MODEL_ASSET)

        if (!outFile.exists() || outFile.length() == 0L) {
            val names = assets.list("")?.toList().orEmpty()
            assumeTrue(
                "Smoke model asset missing. Run tools/fetch_smoke_model.sh, then rebuild.",
                names.contains(MODEL_ASSET),
            )
            assets.open(MODEL_ASSET).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        modelPath = outFile.absolutePath
    }

    @After
    fun tearDown() {
        // Guarded, so a skipped or failed setUp does not turn into a confusing
        // UnsatisfiedLinkError here instead of the real reason.
        if (LlamaBridge.ensureLibraryLoaded() == null && LlamaBridge.nativeIsLoaded()) {
            LlamaBridge.nativeUnload()
        }
    }

    @Test
    fun nativeLibraryReportsItsCapabilities() {
        val info = LlamaBridge.nativeSystemInfo()
        assertThat(info).contains("llama.cpp bridge ready")
        assertThat(info).contains("mmap=")
    }

    @Test
    fun loadsAModelAndGeneratesTokens() {
        val failure = LlamaBridge.nativeLoad(
            path = modelPath,
            nCtx = 512,
            nThreads = 4,
            nGpuLayers = 0,
        )
        assertThat(failure).isEmpty()
        assertThat(LlamaBridge.nativeIsLoaded()).isTrue()
        assertThat(LlamaBridge.nativeContextSize()).isEqualTo(512)

        LlamaBridge.nativeConfigureSampler(
            temperature = 0.8f,
            topP = 0.95f,
            minP = 0.05f,
            topK = 40,
            repeatPenalty = 1.1f,
            repeatLastN = 64,
            seed = 1234,
        )

        val consumed = LlamaBridge.nativeIngest("Once upon a time", addSpecial = true)
        assertThat(consumed).isGreaterThan(0)
        assertThat(LlamaBridge.nativeContextUsed()).isEqualTo(consumed)

        // Generate a handful of tokens. The content is meaningless at this size,
        // so the assertion is that text actually came back across the bridge.
        val produced = StringBuilder()
        var tokens = 0
        repeat(16) {
            val piece = LlamaBridge.nativeNextToken() ?: return@repeat
            produced.append(piece)
            tokens++
        }

        assertThat(tokens).isGreaterThan(0)
        assertThat(produced.toString()).isNotEmpty()
        assertThat(LlamaBridge.nativeContextUsed()).isGreaterThan(consumed)
    }

    @Test
    fun countsTokensWithoutTouchingTheContext() {
        assertThat(LlamaBridge.nativeLoad(modelPath, 512, 4, 0)).isEmpty()

        val before = LlamaBridge.nativeContextUsed()
        val count = LlamaBridge.nativeCountTokens("Once upon a time there was a lighthouse")
        assertThat(count).isGreaterThan(0)
        assertThat(LlamaBridge.nativeContextUsed()).isEqualTo(before)
    }

    @Test
    fun resetClearsTheSequence() {
        assertThat(LlamaBridge.nativeLoad(modelPath, 512, 4, 0)).isEmpty()
        LlamaBridge.nativeIngest("Once upon a time", addSpecial = true)
        assertThat(LlamaBridge.nativeContextUsed()).isGreaterThan(0)

        LlamaBridge.nativeResetContext()
        assertThat(LlamaBridge.nativeContextUsed()).isEqualTo(0)
    }

    @Test
    fun refusesTextThatCannotFitTheContext() {
        assertThat(LlamaBridge.nativeLoad(modelPath, 512, 4, 0)).isEmpty()

        // Comfortably past a 512 token context.
        val tooLong = "lighthouse ".repeat(4000)
        assertThat(LlamaBridge.nativeIngest(tooLong, addSpecial = true)).isEqualTo(-3)
    }
}
