package com.kamsiob.kamai.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.data.Role
import com.kamsiob.kamai.model.ModelCatalog
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves the real Gemma 4 model, as actually downloaded onto this phone, loads
 * through the bridge and generates coherent tokens in the Gemma prompt format.
 *
 * This runs against whatever tier model has been downloaded into the app's
 * files/models directory, so it is skipped on a machine where nothing has been
 * downloaded yet. When the E2B model is present, it is the definitive check that
 * the Gemma 4 swap works end to end, independent of any UI tapping.
 */
@RunWith(AndroidJUnit4::class)
class Gemma4LoadTest {

    private val modelsDir = File(
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
        "models",
    )

    @After
    fun tearDown() {
        if (LlamaBridge.nativeIsLoaded()) LlamaBridge.nativeUnload()
    }

    private fun anyDownloadedGemma4(): Pair<File, com.kamsiob.kamai.model.TierModel>? {
        ModelCatalog.all.forEach { model ->
            val f = File(modelsDir, "${model.id}.gguf")
            if (f.exists() && f.length() == model.downloadBytes) return f to model
        }
        return null
    }

    @Test
    fun theDownloadedGemma4LoadsAndGeneratesInGemmaFormat() {
        assertThat(LlamaBridge.ensureLibraryLoaded()).isNull()

        val found = anyDownloadedGemma4()
        assumeTrue(
            "No Gemma 4 model downloaded into ${modelsDir.absolutePath}. " +
                "Download one through the app first.",
            found != null,
        )
        val (file, model) = found!!

        val failure = LlamaBridge.nativeLoad(
            path = file.absolutePath,
            nCtx = 1024,
            nThreads = 4,
            nGpuLayers = 0,
        )
        assertThat(failure).isEmpty()
        assertThat(LlamaBridge.nativeIsLoaded()).isTrue()

        LlamaBridge.nativeConfigureSampler(0.3f, 0.7f, 0.05f, 20, 1.05f, 128, 7)

        // Build the prompt with the model's real format, exactly as the app does.
        val prompt = model.format.build(
            systemPrompt = "You answer in one short sentence.",
            history = listOf(PromptBuilder.Turn(Role.USER, "Name three primary colours.")),
        )
        val consumed = LlamaBridge.nativeIngest(prompt, addSpecial = false)
        assertThat(consumed).isGreaterThan(0)

        val out = StringBuilder()
        repeat(64) {
            val piece = LlamaBridge.nativeNextToken() ?: return@repeat
            if (PromptBuilder.isStopMarker(piece)) return@repeat
            out.append(piece)
        }

        val answer = PromptBuilder.cleanOutput(out.toString())
        assertThat(answer).isNotEmpty()
        // A real answer to this question mentions at least one primary colour.
        // This is the difference between "tokens came out" and "the model
        // actually understood the prompt in the Gemma 4 format".
        val lower = answer.lowercase()
        assertThat(
            lower.contains("red") || lower.contains("blue") || lower.contains("yellow"),
        ).isTrue()
    }
}
