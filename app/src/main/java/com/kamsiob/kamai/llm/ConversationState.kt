package com.kamsiob.kamai.llm

import android.content.Context
import com.kamsiob.kamai.data.DatabaseKey
import java.io.File

/**
 * A conversation's KV cache, kept across app sessions (#52).
 *
 * Prefix reuse (#38) means an ongoing conversation does not re-read its history
 * every turn, but it lives in the context and dies with the process. Reopening a
 * long chat re-prefilled every token before the first new one could appear,
 * which is the single largest cost left in time to first token.
 *
 * **Encrypted, always.** A serialised KV state is the conversation in
 * reconstructible form. The database is SQLCipher-encrypted precisely so a file
 * copied off the device is meaningless, and a plaintext state file sitting
 * beside it would quietly undo that. These files use the same Keystore-wrapped
 * key, streamed rather than held in memory twice, because the blobs are tens of
 * megabytes.
 *
 * **One at a time.** A state file is proportional to the context it describes,
 * so keeping a library of them would cost more disk than the models. Only the
 * most recent conversation keeps one; opening a different one replaces it. That
 * covers the case worth covering, which is coming back to what you were just
 * doing.
 *
 * Everything here fails quietly. A missing, unreadable or rejected file means a
 * slower first turn, which is exactly what happened before any of this existed.
 */
object ConversationState {

    private const val TAG = "KamState"

    private const val DIR = "kv"

    /**
     * Above this, saving costs more than the prefill it saves.
     *
     * The write is encrypted and synchronous, and it happens when the user is
     * leaving a screen. A quarter of a gigabyte of that is worse than the wait
     * it is trying to avoid.
     */
    private const val MAX_BYTES = 256L * 1024 * 1024

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    /**
     * One file, named for the conversation and the model.
     *
     * The model is in the name because a state blob is meaningless to a
     * different one, and llama.cpp cannot tell: it would either reject it, which
     * is the good case, or restore something that decodes into nonsense.
     */
    private fun file(context: Context, conversationId: String, modelId: String): File =
        File(dir(context), "${safe(conversationId)}--${safe(modelId)}.kv")

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /**
     * Writes the engine's current state for [conversationId], replacing whatever
     * was there.
     *
     * Written to a temporary file and renamed, so a process killed mid-write
     * leaves the previous state rather than a half-file that would be rejected
     * on the next open.
     */
    suspend fun save(
        context: Context,
        engine: InferenceEngine,
        conversationId: String,
        modelId: String,
    ): Boolean = runCatching {
        val blob = engine.saveState()
        if (blob == null) {
            android.util.Log.i(TAG, "save: nothing cached for $conversationId")
            return false
        }
        if (blob.size > MAX_BYTES) {
            clear(context)
            return false
        }
        val target = file(context, conversationId, modelId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { out ->
            DatabaseKey.encrypting(out).use { it.write(blob) }
        }
        // Only now is the old one gone. Everything except the file just written,
        // which is what "one at a time" means; clearing the whole directory here
        // deleted the temporary file before it could be renamed, and the feature
        // silently did nothing.
        dir(context).listFiles()?.forEach { if (it != temp) it.delete() }
        val ok = temp.renameTo(target)
        android.util.Log.i(TAG, "save: ${blob.size} bytes for $conversationId, renamed=$ok")
        ok
    }.getOrElse {
        // Logged rather than swallowed. A save that quietly does nothing is
        // indistinguishable from a feature that is not there, which is exactly
        // how the first version of this shipped nothing at all.
        android.util.Log.w(TAG, "save failed", it)
        false
    }

    /**
     * Restores [conversationId]'s state into the engine, if there is one for
     * this model. True when the context now holds it.
     */
    suspend fun restore(
        context: Context,
        engine: InferenceEngine,
        conversationId: String,
        modelId: String,
    ): Boolean = runCatching {
        val f = file(context, conversationId, modelId)
        if (!f.exists()) return false
        val blob = f.inputStream().use { input ->
            DatabaseKey.decrypting(input).use { it.readBytes() }
        }
        val ok = engine.restoreState(blob)
        android.util.Log.i(TAG, "restore: ${blob.size} bytes for $conversationId, ok=$ok")
        ok
    }.getOrElse {
        // An unreadable file is worse than none: it will keep failing. Drop it.
        android.util.Log.w(TAG, "restore failed", it)
        clear(context)
        false
    }

    /**
     * Throws the saved state away.
     *
     * Called whenever the history the cache describes stops being the history
     * that will be sent: an edited message, a mode switch, changed instructions,
     * changed memory, a trimmed conversation. Each of those shifts token
     * positions or content, and a cache restored against them would be decoding
     * against a prompt that no longer exists.
     */
    fun clear(context: Context) {
        runCatching { dir(context).listFiles()?.forEach { it.delete() } }
    }

    /** Bytes on disk, for the Storage screen. */
    fun bytes(context: Context): Long =
        runCatching { dir(context).listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)
}
