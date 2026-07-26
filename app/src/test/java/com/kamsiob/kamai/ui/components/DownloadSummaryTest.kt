package com.kamsiob.kamai.ui.components

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.download.Downloads
import org.junit.Test

/**
 * What the app-wide download indicator says (#81).
 *
 * The wording and the arithmetic are separated from the composable because most
 * of the difficulty is here: deciding what counts as active, what to say when
 * several are queued, and what to show before the size is known. A bar that
 * follows the user across every screen for minutes at a time has to be right
 * about all three.
 */
class DownloadSummaryTest {

    private fun item(
        id: String = "m1",
        name: String = "Balanced",
        kind: String = "model",
        done: Long = 0,
        total: Long = 0,
        status: Downloads.Status = Downloads.Status.RUNNING,
    ) = Downloads.Item(id, name, kind, done, total, status)

    @Test
    fun `nothing downloading shows nothing`() {
        assertThat(DownloadSummary.active(emptyList())).isNull()
    }

    @Test
    fun `a finished download is not still announced`() {
        // Completion has its own quiet confirmation. A bar that lingered would
        // be saying something that has stopped being true.
        assertThat(
            DownloadSummary.active(listOf(item(status = Downloads.Status.DONE))),
        ).isNull()
    }

    @Test
    fun `a failure does not follow the user around`() {
        // It belongs where they can act on it, not in a bar on every screen.
        assertThat(
            DownloadSummary.active(listOf(item(status = Downloads.Status.FAILED))),
        ).isNull()
    }

    @Test
    fun `a running download shows its name, percentage and what is left`() {
        val s = DownloadSummary.active(
            listOf(item(done = 1_000_000_000, total = 4_000_000_000)),
        )!!
        assertThat(s.title).isEqualTo("Balanced")
        assertThat(s.detail).contains("25%")
        assertThat(s.detail).contains("3.0 GB left")
        assertThat(s.fraction).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `an unknown size shows no bar position rather than a made-up one`() {
        val s = DownloadSummary.active(listOf(item(done = 500, total = 0)))!!
        assertThat(s.fraction).isNull()
        assertThat(s.detail).isEqualTo("starting")
    }

    @Test
    fun `a paused download stays visible and says so`() {
        // Hiding it would make a pause look like a cancel.
        val s = DownloadSummary.active(
            listOf(item(done = 1, total = 10, status = Downloads.Status.PAUSED)),
        )!!
        assertThat(s.title).isEqualTo("Balanced, paused")
    }

    @Test
    fun `verifying is still work the user is waiting on`() {
        // Hashing a multi-gigabyte file takes long enough to look like a hang.
        val s = DownloadSummary.active(
            listOf(item(done = 10, total = 10, status = Downloads.Status.VERIFYING)),
        )!!
        assertThat(s.title).isEqualTo("Balanced, checking")
    }

    @Test
    fun `several queued show the current one and how many are behind`() {
        val s = DownloadSummary.active(
            listOf(
                item(id = "m1", name = "Balanced", done = 1, total = 4),
                item(id = "v1", name = "Voice", status = Downloads.Status.PAUSED),
                item(id = "p1", name = "History pack", status = Downloads.Status.PAUSED),
            ),
        )!!
        assertThat(s.title).isEqualTo("Balanced, and 2 more")
    }

    @Test
    fun `exactly one behind is singular`() {
        val s = DownloadSummary.active(
            listOf(
                item(id = "m1", done = 1, total = 4),
                item(id = "v1", name = "Voice", status = Downloads.Status.PAUSED),
            ),
        )!!
        assertThat(s.title).isEqualTo("Balanced, and 1 more")
    }

    @Test
    fun `the running one is shown even when a paused one comes first in the list`() {
        val s = DownloadSummary.active(
            listOf(
                item(id = "v1", name = "Voice", status = Downloads.Status.PAUSED),
                item(id = "m1", name = "Balanced", done = 1, total = 4),
            ),
        )!!
        assertThat(s.title).startsWith("Balanced")
    }

    @Test
    fun `a voice or a pack uses the same indicator as a model`() {
        // One treatment for all three. Three would be three things to learn.
        val voice = DownloadSummary.active(
            listOf(item(name = "Speech", kind = "voice", done = 1, total = 2)),
        )!!
        assertThat(voice.title).isEqualTo("Speech")
        val pack = DownloadSummary.active(
            listOf(item(name = "History", kind = "pack", done = 1, total = 2)),
        )!!
        assertThat(pack.title).isEqualTo("History")
    }

    @Test
    fun `a screen reader gets one sentence, not a row of fragments`() {
        val s = DownloadSummary.active(
            listOf(item(done = 1_000_000_000, total = 4_000_000_000)),
        )!!
        assertThat(s.spoken).isEqualTo("Downloading Balanced, 25 percent, 3.0 GB left")
    }

    @Test
    fun `sizes read as people write them`() {
        assertThat(DownloadSummary.formatBytes(3_100_000_000)).isEqualTo("3.1 GB")
        assertThat(DownloadSummary.formatBytes(48_000_000)).isEqualTo("48 MB")
        assertThat(DownloadSummary.formatBytes(0)).isEqualTo("0 MB")
    }

    @Test
    fun `a download past its stated size never reports negative remaining`() {
        // Servers lie about content length often enough that this matters.
        val s = DownloadSummary.active(listOf(item(done = 12, total = 10)))!!
        assertThat(s.detail).contains("0 MB left")
        assertThat(s.fraction).isWithin(0.001f).of(1f)
    }
}
