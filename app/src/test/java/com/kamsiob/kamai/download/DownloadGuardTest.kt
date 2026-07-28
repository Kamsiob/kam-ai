package com.kamsiob.kamai.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * When a download may start, and when the user has to be asked first (#79).
 *
 * Every rule here is about spending something nobody agreed to spend: a data
 * allowance, the last of the disk, or the last of the battery. They are pure so
 * the reasoning can be read without a radio, a disk or a charger, and so the
 * ordering between them is visible, which is the part most easily got wrong.
 */
class DownloadGuardTest {

    private val wifi = Connectivity.State(online = true, metered = false)
    private val cellular = Connectivity.State(online = true, metered = true)
    private val offline = Connectivity.State(online = false, metered = false)

    private val fiveGb = 5L * 1_000_000_000
    private val plenty = 50L * 1_000_000_000

    private fun check(
        size: Long = fiveGb,
        network: Connectivity.State = wifi,
        free: Long = plenty,
        battery: Int? = 80,
        charging: Boolean = false,
    ) = DownloadGuard.check(size, network, free, battery, charging)

    @Test
    fun `a big download on wifi with room and charge just goes`() {
        assertThat(check()).isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `a big download on mobile data asks first, with the size in the question`() {
        val v = check(network = cellular)
        assertThat(v).isInstanceOf(DownloadGuard.Verdict.Warn::class.java)
        val warn = v as DownloadGuard.Verdict.Warn
        assertThat(warn.message).contains("5.0 GB")
        assertThat(warn.message).contains("mobile data")
        // Waiting is the default, so the proceed action has to name itself
        // plainly rather than being the obvious button.
        assertThat(warn.proceedLabel).contains("anyway")
    }

    @Test
    fun `a small pack on mobile data does not ask`() {
        assertThat(check(size = 8L * 1_000_000, network = cellular))
            .isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `no connection stops, and says the app still works`() {
        val v = check(network = offline) as DownloadGuard.Verdict.Stop
        assertThat(v.message).contains("needs a connection")
        assertThat(v.message).contains("still works")
    }

    @Test
    fun `not enough disk stops with both real numbers`() {
        val v = check(free = 2L * 1_000_000_000) as DownloadGuard.Verdict.Stop
        assertThat(v.message).contains("5.0 GB")
        assertThat(v.message).contains("2.0 GB")
        assertThat(v.message).contains("smaller model")
    }

    @Test
    fun `the offer and the download agree about disk`() {
        // #75 wants a model that cannot fit refused before it is offered rather
        // than after it is chosen. That only holds while both places ask the same
        // question, so the offer calls the same function the download does. Two
        // checks with two slightly different constants is one screen offering
        // what the next screen refuses.
        val tooSmall = fiveGb + 1
        assertThat(DownloadGuard.fitsOnDisk(fiveGb, tooSmall)).isFalse()
        assertThat(check(size = fiveGb, free = tooSmall))
            .isInstanceOf(DownloadGuard.Verdict.Stop::class.java)

        assertThat(DownloadGuard.fitsOnDisk(fiveGb, plenty)).isTrue()
        assertThat(check(size = fiveGb, free = plenty)).isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `headroom is kept, so a download that exactly fits is still refused`() {
        val exactly = fiveGb
        assertThat(check(free = exactly)).isInstanceOf(DownloadGuard.Verdict.Stop::class.java)
        assertThat(check(free = exactly + DownloadGuard.DISK_HEADROOM_BYTES))
            .isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `disk is checked before the network, because agreeing cannot make it fit`() {
        val v = check(network = offline, free = 1L) as DownloadGuard.Verdict.Stop
        assertThat(v.message).contains("free")
    }

    @Test
    fun `a low battery with no charger is mentioned, not refused`() {
        val v = check(battery = 9) as DownloadGuard.Verdict.Warn
        assertThat(v.message).contains("9 percent")
        assertThat(v.proceedLabel).isEqualTo("Start anyway")
    }

    @Test
    fun `a low battery while charging says nothing`() {
        assertThat(check(battery = 9, charging = true)).isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `an unreadable battery is not treated as a flat one`() {
        assertThat(check(battery = null)).isEqualTo(DownloadGuard.Verdict.Go)
    }

    @Test
    fun `mobile data is asked about before a low battery is mentioned`() {
        val v = check(network = cellular, battery = 5) as DownloadGuard.Verdict.Warn
        assertThat(v.message).contains("mobile data")
    }

    @Test
    fun `a download killed by process death picks itself back up on wifi`() {
        assertThat(DownloadGuard.shouldAutoResume(userPaused = false, network = wifi)).isTrue()
    }

    @Test
    fun `a download the user paused stays paused`() {
        assertThat(DownloadGuard.shouldAutoResume(userPaused = true, network = wifi)).isFalse()
    }

    @Test
    fun `nothing auto-resumes onto mobile data`() {
        assertThat(DownloadGuard.shouldAutoResume(userPaused = false, network = cellular))
            .isFalse()
    }

    @Test
    fun `nothing auto-resumes with no connection`() {
        assertThat(DownloadGuard.shouldAutoResume(userPaused = false, network = offline))
            .isFalse()
    }
}
