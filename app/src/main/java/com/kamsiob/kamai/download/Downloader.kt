package com.kamsiob.kamai.download

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads a large file with progress, resumes an interrupted attempt where it
 * left off, and refuses to hand back a file whose hash does not match.
 *
 * Everything lands in app-managed storage. Nothing is written anywhere the user
 * would have to grant a storage permission for.
 */
class Downloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // No read timeout: a slow phone on a slow connection pulling five
        // gigabytes is not an error, and killing it at an arbitrary minute
        // count is the most annoying possible failure.
        .readTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    sealed interface Progress {
        data class Running(
            val bytesDownloaded: Long,
            val totalBytes: Long,
        ) : Progress {
            val fraction: Float
                get() = if (totalBytes > 0) {
                    (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                } else {
                    0f
                }
        }

        data object Verifying : Progress
        data class Done(val file: File) : Progress

        /** [message] is written to be shown to the user as it is. */
        data class Failed(val message: String, val canRetry: Boolean = true) : Progress
    }

    fun directoryFor(kind: String): File =
        File(context.filesDir, kind).apply { mkdirs() }

    /**
     * Downloads [url] to [destination], resuming if a partial file is there.
     *
     * @param expectedSha256 verified before the file is moved into place. A
     *   download that fails verification is deleted rather than left to be
     *   picked up as a resumable partial, because a corrupt file that keeps
     *   resuming will never become correct.
     */
    fun download(
        url: String,
        destination: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
    ): Flow<Progress> = flow {
        val partial = File(destination.parentFile, destination.name + ".part")

        if (destination.exists() && destination.length() > 0) {
            emit(Progress.Verifying)
            if (sha256(destination).equals(expectedSha256, ignoreCase = true)) {
                emit(Progress.Done(destination))
                return@flow
            }
            destination.delete()
        }

        freeSpaceProblem(destination, expectedSizeBytes)?.let {
            emit(Progress.Failed(it, canRetry = false))
            return@flow
        }

        var existing = if (partial.exists()) partial.length() else 0L
        if (existing > expectedSizeBytes) {
            // The partial is longer than the finished file should be, so it is
            // not a prefix of anything useful. Start over.
            partial.delete()
            existing = 0L
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(Progress.Failed(httpMessage(response.code)))
                    return@flow
                }

                // A server that ignores the range header sends 200 and the whole
                // file, so appending would corrupt it.
                val appending = existing > 0 && response.code == HTTP_PARTIAL
                if (!appending) existing = 0L

                val body = response.body
                val total = if (appending) {
                    existing + body.contentLength()
                } else {
                    body.contentLength().takeIf { it > 0 } ?: expectedSizeBytes
                }

                var written = existing
                emit(Progress.Running(written, total))

                body.byteStream().use { input ->
                    java.io.FileOutputStream(partial, appending).use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var lastEmit = 0L

                        while (true) {
                            if (!currentCoroutineContext().isActive) return@flow
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read

                            // Emitting on every buffer would flood the UI.
                            if (written - lastEmit >= EMIT_EVERY_BYTES) {
                                lastEmit = written
                                emit(Progress.Running(written, total))
                            }
                        }
                        output.flush()
                    }
                }
                emit(Progress.Running(written, total))
            }
        } catch (e: IOException) {
            // The partial is kept so the next attempt resumes rather than
            // starting the whole download again.
            emit(
                Progress.Failed(
                    "The download stopped. Check your connection and try again, " +
                        "and it will pick up where it left off.",
                ),
            )
            return@flow
        }

        emit(Progress.Verifying)
        val actual = sha256(partial)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            partial.delete()
            emit(
                Progress.Failed(
                    "The downloaded file did not match what it should be, so it was " +
                        "deleted. Try again.",
                ),
            )
            return@flow
        }

        if (!partial.renameTo(destination)) {
            partial.delete()
            emit(Progress.Failed("The file could not be saved. Try again."))
            return@flow
        }

        emit(Progress.Done(destination))
    }.flowOn(Dispatchers.IO)

    /** Non-null when there is not enough room, carrying the sentence to show. */
    private fun freeSpaceProblem(destination: File, needed: Long): String? {
        val stat = StatFs(destination.parentFile?.absolutePath ?: context.filesDir.absolutePath)
        val free = stat.availableBytes
        // Ask for the file plus a margin, since a phone with exactly zero bytes
        // left afterwards is a broken phone.
        val required = needed + FREE_SPACE_MARGIN
        return if (free < required) {
            val shortBy = com.kamsiob.kamai.model.formatBytes(required - free)
            "There is not enough space on this phone. You need about $shortBy more."
        } else {
            null
        }
    }

    private fun httpMessage(code: Int): String = when (code) {
        404 -> "That file is not where it should be any more. This is a problem at our end."
        403, 401 -> "The download was refused. This is a problem at our end."
        in 500..599 -> "The server hosting the file is having trouble. Try again in a bit."
        else -> "The download failed. Check your connection and try again."
    }

    companion object {
        private const val BUFFER_BYTES = 1 shl 16
        private const val EMIT_EVERY_BYTES = 512L * 1024L
        private const val HTTP_PARTIAL = 206
        private const val FREE_SPACE_MARGIN = 300L * 1024L * 1024L

        /**
         * Identifies the project and gives a contact address, which is ordinary
         * etiquette and is required by Wikimedia for the pack pipeline.
         */
        const val USER_AGENT = "KamAI/1.0 (https://github.com/kamsiob/kam-ai; hello@kamsiob.com)"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
