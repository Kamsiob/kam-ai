package com.kamsiob.kamai.download

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The one place that runs downloads, so several can run at once, each can be
 * paused or cancelled, and they keep going while the app is in the background.
 *
 * Staying alive in the background is the job of [DownloadService]: while any
 * download is active this manager keeps that foreground service running, which
 * keeps the process alive so these coroutines are not killed the moment the user
 * leaves the app. When the last download finishes or is paused, the service
 * stops.
 *
 * A download that is paused keeps its partial file and resumes from it; a
 * cancelled one deletes the partial file. Registration of the finished artifact
 * is a per-download callback, so this manager stays unaware of models, voices,
 * and packs.
 */
object Downloads {

    enum class Status { RUNNING, VERIFYING, PAUSED, FAILED, DONE }

    data class Item(
        val id: String,
        val displayName: String,
        /** "model", "voice", or "pack", for grouping and the destination. */
        val kind: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val status: Status,
        val message: String? = null,
    ) {
        val fraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    /** Everything the manager needs to run and finish one download. */
    data class Spec(
        val id: String,
        val displayName: String,
        val kind: String,
        val url: String,
        val destination: File,
        val sizeBytes: Long,
        val sha256: String,
        /** Registers the finished file (as a model, voice, or pack). */
        val onInstalled: suspend (File) -> Unit,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val specs = mutableMapOf<String, Spec>()

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    val activeCount: Int get() = _items.value.count { it.status == Status.RUNNING || it.status == Status.VERIFYING }

    fun isActive(id: String): Boolean =
        _items.value.firstOrNull { it.id == id }?.status.let {
            it == Status.RUNNING || it == Status.VERIFYING || it == Status.PAUSED
        }

    /** Starts (or restarts) a download. Ignores a duplicate that is already running. */
    fun start(context: Context, downloader: Downloader, spec: Spec) {
        if (jobs[spec.id]?.isActive == true) return
        specs[spec.id] = spec
        val app = context.applicationContext
        put(Item(spec.id, spec.displayName, spec.kind, existingBytes(spec), spec.sizeBytes, Status.RUNNING))
        DownloadService.ensureRunning(app)

        jobs[spec.id] = scope.launch {
            downloader.download(spec.url, spec.destination, spec.sizeBytes, spec.sha256).collect { p ->
                when (p) {
                    is Downloader.Progress.Running ->
                        put(item(spec).copy(downloadedBytes = p.bytesDownloaded, totalBytes = p.totalBytes, status = Status.RUNNING))
                    Downloader.Progress.Verifying ->
                        put(item(spec).copy(status = Status.VERIFYING))
                    is Downloader.Progress.Done -> {
                        runCatching { spec.onInstalled(p.file) }
                        put(item(spec).copy(downloadedBytes = spec.sizeBytes, status = Status.DONE))
                        finish(app, spec.id, keep = true)
                    }
                    is Downloader.Progress.Failed -> {
                        put(item(spec).copy(status = Status.FAILED, message = p.message))
                        finish(app, spec.id, keep = true)
                    }
                }
            }
        }
    }

    /**
     * Re-surfaces a download that was interrupted by process death. If a partial
     * file is on disk and the finished file is not, this registers the spec and
     * shows the download as paused, so the person sees "Paused at X%" with a
     * Resume button after a restart instead of the progress silently vanishing.
     * It never auto-resumes: resuming spends data, so it stays the person's call.
     */
    fun restorePaused(spec: Spec) {
        if (jobs[spec.id]?.isActive == true) return
        if (_items.value.any { it.id == spec.id }) return
        if (spec.destination.exists()) return
        val part = File(spec.destination.parentFile, spec.destination.name + ".part")
        if (!part.exists() || part.length() <= 0L) return
        specs[spec.id] = spec
        put(Item(spec.id, spec.displayName, spec.kind, part.length(), spec.sizeBytes, Status.PAUSED))
    }

    /** Pauses a download, keeping its partial file so it resumes from where it was. */
    fun pause(context: Context, id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        item(id)?.let { put(it.copy(status = Status.PAUSED)) }
        maybeStopService(context)
    }

    /** Resumes a paused (or failed) download from its partial file. */
    fun resume(context: Context, downloader: Downloader, id: String) {
        specs[id]?.let { start(context, downloader, it) }
    }

    /** Cancels a download and deletes its partial file. */
    fun cancel(context: Context, id: String) {
        jobs[id]?.cancel()
        jobs.remove(id)
        specs[id]?.let { File(it.destination.parentFile, it.destination.name + ".part").delete() }
        specs.remove(id)
        _items.value = _items.value.filterNot { it.id == id }
        maybeStopService(context)
    }

    /** Clears a finished or failed row from the list. */
    fun dismiss(id: String) {
        _items.value = _items.value.filterNot { it.id == id && (it.status == Status.DONE || it.status == Status.FAILED) }
    }

    private fun existingBytes(spec: Spec): Long =
        File(spec.destination.parentFile, spec.destination.name + ".part").let { if (it.exists()) it.length() else 0L }

    private fun item(spec: Spec): Item = item(spec.id)
        ?: Item(spec.id, spec.displayName, spec.kind, 0, spec.sizeBytes, Status.RUNNING)

    private fun item(id: String): Item? = _items.value.firstOrNull { it.id == id }

    private fun put(item: Item) {
        // Update in place so a progress tick never reorders the list. With several
        // downloads running at once, appending would make the busiest one keep
        // jumping to the bottom on every tick.
        val current = _items.value
        _items.value = if (current.any { it.id == item.id }) {
            current.map { if (it.id == item.id) item else it }
        } else {
            current + item
        }
    }

    private fun finish(context: Context, id: String, keep: Boolean) {
        jobs.remove(id)
        if (!keep) _items.value = _items.value.filterNot { it.id == id }
        maybeStopService(context)
    }

    private fun maybeStopService(context: Context) {
        if (activeCount == 0) DownloadService.stop(context)
    }
}
