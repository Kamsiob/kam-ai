package com.kamsiob.kamai.ui.components

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.download.Downloads
import org.junit.Test

/**
 * How long is left, and when not to say.
 *
 * The download the owner tried ran at about 1 MB/s on a 2.4 GHz link behind a
 * tunnel, while the same network gave a desktop 26 MB/s. The app cannot fix the
 * link. What it was doing wrong was saying "1.1 GB left", which is two minutes on
 * one connection and forty on another, so somebody watching it has no way to
 * decide whether to wait.
 *
 * The rule this encodes: quote a figure only once there is enough evidence for
 * one, keep it coarse, and measure the current attempt rather than a total that
 * might include an hour spent paused.
 */
class DownloadEtaTest {

    private val MB = 1024L * 1024

    private fun item(
        downloaded: Long,
        total: Long = 3_106_738_272L,
        startedAtMs: Long? = System.currentTimeMillis() - 60_000,
        resumedFrom: Long = 0,
    ) = Downloads.Item(
        id = "m", displayName = "Gemma 4 E2B", kind = "model",
        downloadedBytes = downloaded, totalBytes = total,
        status = Downloads.Status.RUNNING,
        startedAtMs = startedAtMs, resumedFromBytes = resumedFrom,
    )

    @Test
    fun `the rate is measured on this attempt, not on bytes already there`() {
        // A resumed download has bytes on disk it did not fetch this time. Counting
        // them would report a rate far higher than reality and an estimate far
        // shorter, which is the wrong direction to be wrong in.
        val resumed = item(downloaded = 900 * MB, resumedFrom = 800 * MB)
        assertThat(resumed.bytesThisAttempt).isEqualTo(100 * MB)
    }

    @Test
    fun `elapsed time is null before the attempt has started`() {
        assertThat(item(downloaded = 0, startedAtMs = null).elapsedMs).isNull()
    }

    @Test
    fun `a download that has just begun reports no estimate`() {
        // The first seconds are the least representative part of a transfer, and a
        // number that swings wildly reads as the app not knowing what it is doing.
        val fresh = item(downloaded = 1 * MB, startedAtMs = System.currentTimeMillis() - 1_000)
        val summary = DownloadSummary.active(listOf(fresh))!!
        // The size remaining is always shown; it is the time that is withheld.
        assertThat(summary.detail).contains("GB left")
        assertThat(summary.detail).doesNotContain("min left")
        assertThat(summary.detail).doesNotContain("under a minute")
        assertThat(summary.detail).doesNotContain("over an hour")
    }

    @Test
    fun `an established download says roughly how long`() {
        // 60 MB in 60 seconds against a 3.1 GB file is about 50 minutes remaining.
        val running = item(downloaded = 60 * MB, startedAtMs = System.currentTimeMillis() - 60_000)
        val summary = DownloadSummary.active(listOf(running))!!
        assertThat(summary.detail).contains("min left")
    }

    @Test
    fun `a very long wait is not quoted to the minute`() {
        // 8 MB in 60 seconds against 3.1 GB is over six hours. Printing "388 min
        // left" would be arithmetic rather than communication.
        //
        // Deliberately above the 4 MB floor, because below it the honest answer is
        // no estimate at all, which the previous test covers.
        val crawling = item(downloaded = 8 * MB, startedAtMs = System.currentTimeMillis() - 60_000)
        val summary = DownloadSummary.active(listOf(crawling))!!
        assertThat(summary.detail).contains("over an hour left")
    }

    @Test
    fun `an almost finished download says under a minute`() {
        val nearly = item(
            downloaded = 3_100_000_000L,
            startedAtMs = System.currentTimeMillis() - 60_000,
        )
        val summary = DownloadSummary.active(listOf(nearly))!!
        assertThat(summary.detail).contains("under a minute left")
    }
}
