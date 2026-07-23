package com.kamsiob.kamai.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves sherpa-onnx text-to-speech actually synthesises audio on device. It runs
 * a Piper voice model through the real TtsEngine and checks that a plausible
 * amount of PCM comes back at a sane sample rate. It cannot check how the audio
 * sounds, only that synthesis works end to end, which is what the native
 * integration risk is about.
 *
 * Uses the app's own espeak-ng data and tokens (bundled assets) with a Piper
 * model provided as a test asset. Skips if the model asset is absent.
 */
@RunWith(AndroidJUnit4::class)
class TtsSynthesizeTest {

    @Test
    fun synthesisesSpeech() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        assumeTrue(
            "Piper model asset not present; skipping on-device TTS test.",
            runCatching { testAssets.open("piper-amy.onnx").use { true } }.getOrDefault(false),
        )

        val modelFile = File(context.cacheDir, "piper-amy.onnx").also { out ->
            if (!out.exists() || out.length() == 0L) {
                testAssets.open("piper-amy.onnx").use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
        }

        val engine = TtsEngine(context = context)
        val result = engine.synthesize(
            TtsCatalog.AMY, modelFile,
            "Hello. This is a short test of the reading voice.",
        )
        assertTrue("synthesis should produce audio", result != null)
        val audio = result!!
        assertTrue("sample rate should be sane, was ${audio.sampleRate}", audio.sampleRate in 8_000..48_000)
        // A one-sentence utterance is at least a few tenths of a second of audio.
        assertTrue(
            "expected a real amount of audio, got ${audio.samples.size} samples at ${audio.sampleRate} Hz",
            audio.samples.size > audio.sampleRate / 2,
        )
        // Audio should not be pure silence.
        assertTrue("audio should not be silent", audio.samples.any { kotlin.math.abs(it) > 0.01f })
    }
}
