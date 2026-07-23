package com.kamsiob.kamai.voice

import android.content.Context
import com.kamsiob.kamai.llm.Models

/**
 * Process-wide holder for the voice engines, mirroring [com.kamsiob.kamai.llm.Models].
 *
 * The speech-to-text engine is created with a hook that lets the model manager
 * make room before whisper loads. This is the voice half of the app's memory
 * discipline (PART B): a voice model shares the same budget as the language
 * model, so before whisper loads its weights the language model's KV cache is
 * released. Combined with SttEngine unloading whisper the instant it finishes,
 * the whisper and language-model peaks never overlap.
 */
object Voice {

    @Volatile
    private var sttRef: SttEngine? = null

    @Volatile
    private var ttsRef: TtsEngine? = null

    fun stt(context: Context): SttEngine {
        val app = context.applicationContext
        return sttRef ?: synchronized(this) {
            sttRef ?: SttEngine(
                onBeforeLoad = {
                    // Free the language model's KV cache so the two do not peak
                    // together. The model stays mmapped and rebuilds its context
                    // on the next reply.
                    Models.manager(app).onModeratePressure()
                },
            ).also { sttRef = it }
        }
    }

    fun tts(context: Context): TtsEngine {
        val app = context.applicationContext
        return ttsRef ?: synchronized(this) {
            ttsRef ?: TtsEngine(
                context = app,
                onBeforeLoad = { Models.manager(app).onModeratePressure() },
            ).also { ttsRef = it }
        }
    }

    /** Release voice models on memory pressure or backgrounding. */
    fun releaseAll() {
        ttsRef?.stop()
    }
}
