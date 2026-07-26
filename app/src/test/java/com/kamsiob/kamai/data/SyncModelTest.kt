package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rules that make the data model sync-ready, tested without any sync.
 *
 * Everything here is pure, which is the point of having put it in Sync.kt rather
 * than inside a sync engine that does not exist yet. These rules have to be
 * identical on every device or two phones reconcile the same pair of writes
 * differently and never agree, and a rule that can only be exercised by running
 * two phones against a server is a rule nobody checks.
 */
class SyncModelTest {

    // ---- The clock -------------------------------------------------------

    @Test
    fun `a local write always gets a higher stamp than the last`() {
        val clock = LamportClock()
        val first = clock.tick()
        val second = clock.tick()
        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `seeing a peer ahead of us moves us past it, not to it`() {
        val clock = LamportClock(initial = 5)
        clock.observe(100)
        // Moved up to what was seen...
        assertThat(clock.value).isEqualTo(100)
        // ...and the next local write is after it, so causality holds: anything
        // written after seeing their change is ordered after their change.
        assertThat(clock.tick()).isEqualTo(101)
    }

    @Test
    fun `seeing a peer behind us does not drag us back`() {
        val clock = LamportClock(initial = 500)
        clock.observe(3)
        assertThat(clock.value).isEqualTo(500)
    }

    @Test
    fun `the clock never goes backwards however it is driven`() {
        // The property that `updatedAt` cannot offer, stated as a property rather
        // than as an example: no sequence of local writes and observed peers can
        // ever produce a value lower than one already handed out.
        val clock = LamportClock()
        var high = 0L
        val seen = listOf(7L, 2L, 90L, 1L, 90L, 4L, 300L, 12L)
        seen.forEachIndexed { i, s ->
            clock.observe(s)
            val stamp = if (i % 2 == 0) clock.tick() else clock.value
            assertThat(stamp).isAtLeast(high)
            high = stamp
        }
    }

    // ---- Ordering --------------------------------------------------------

    @Test
    fun `a later stamp beats an earlier one regardless of device`() {
        assertThat(Stamp(2, "aaa")).isGreaterThan(Stamp(1, "zzz"))
        assertThat(Stamp(1, "zzz")).isLessThan(Stamp(2, "aaa"))
    }

    @Test
    fun `two devices at the same stamp are ordered the same way from both sides`() {
        // The case that actually happens: two phones edit the same project name
        // while both offline, neither has seen the other, so both are at the same
        // count. Without a tiebreak each keeps its own and they never converge.
        val a = Stamp(9, "device-a")
        val b = Stamp(9, "device-b")
        assertThat(a.compareTo(b)).isLessThan(0)
        assertThat(b.compareTo(a)).isGreaterThan(0)
        // Same answer computed on either phone, which is the only thing that
        // matters. Which one wins is arbitrary; agreeing is not.
        assertThat(Reconcile.winner(local = a, remote = b)).isEqualTo(Reconcile.Winner.REMOTE)
        assertThat(Reconcile.winner(local = b, remote = a)).isEqualTo(Reconcile.Winner.LOCAL)
    }

    @Test
    fun `a row untouched since before sync existed loses to one that has been written`() {
        // Every row already on every phone is rev 0 with no writer. Those must
        // sort below any real write, or the migration would silently prefer stale
        // copies over edits made afterwards.
        val legacy = Stamp(0, "")
        val written = Stamp(1, "device-a")
        assertThat(Reconcile.winner(local = legacy, remote = written))
            .isEqualTo(Reconcile.Winner.REMOTE)
    }

    // ---- Deletion --------------------------------------------------------

    @Test
    fun `a deletion beats an edit at the same stamp`() {
        val stamp = Stamp(4, "same-device")
        assertThat(
            Reconcile.winner(stamp, stamp, localDeleted = true, remoteDeleted = false),
        ).isEqualTo(Reconcile.Winner.LOCAL)
        assertThat(
            Reconcile.winner(stamp, stamp, localDeleted = false, remoteDeleted = true),
        ).isEqualTo(Reconcile.Winner.REMOTE)
    }

    @Test
    fun `a later edit still beats an earlier deletion`() {
        // Deletion wins ties, not everything. Someone who deletes a chat and then
        // writes in it again on the other phone has said, later, that they want
        // it. The stamp is what says which came second.
        val deleted = Stamp(3, "device-a")
        val edited = Stamp(8, "device-b")
        assertThat(
            Reconcile.winner(local = deleted, remote = edited, localDeleted = true),
        ).isEqualTo(Reconcile.Winner.REMOTE)
    }

    @Test
    fun `the same write arriving twice is reported as nothing to do`() {
        // Ordinary in anything that retries, and the answer is neither side: an
        // identical stamp means the identical write, so there is no choice to
        // make and no write worth performing.
        val stamp = Stamp(11, "device-a")
        assertThat(Reconcile.winner(stamp, stamp)).isEqualTo(Reconcile.Winner.SAME)
        assertThat(
            Reconcile.winner(stamp, stamp, localDeleted = true, remoteDeleted = true),
        ).isEqualTo(Reconcile.Winner.SAME)
    }

    @Test
    fun `reconciling is decided the same way whichever phone asks`() {
        // Convergence, stated directly. For every pair of stamps and delete
        // states, the two phones must pick the same surviving version. If this
        // fails, two devices disagree forever and no amount of syncing fixes it.
        // This is the test that found the asymmetry Winner.SAME now names. It is
        // worth keeping in this exhaustive form rather than as the one example,
        // because the failure was in a corner nobody would have chosen to write a
        // case for.
        val stamps = listOf(Stamp(1, "a"), Stamp(1, "b"), Stamp(2, "a"), Stamp(2, "b"))
        val flags = listOf(false, true)
        for (x in stamps) for (y in stamps) for (dx in flags) for (dy in flags) {
            val fromX = Reconcile.winner(x, y, dx, dy)
            val fromY = Reconcile.winner(y, x, dy, dx)
            // Either both phones see nothing to do, or they name the same
            // surviving version from their own point of view. Never one each.
            if (fromX == Reconcile.Winner.SAME || fromY == Reconcile.Winner.SAME) {
                assertThat(fromX).isEqualTo(Reconcile.Winner.SAME)
                assertThat(fromY).isEqualTo(Reconcile.Winner.SAME)
            } else {
                val xWinsFromX = fromX == Reconcile.Winner.LOCAL
                val xWinsFromY = fromY == Reconcile.Winner.REMOTE
                assertThat(xWinsFromX).isEqualTo(xWinsFromY)
            }
        }
    }

    // ---- Policy ----------------------------------------------------------

    @Test
    fun `the tables holding the users own content sync`() {
        listOf("projects", "conversations", "messages", "memory_entries", "follow_ups")
            .forEach { assertThat(SyncPolicy.syncs(it)).isTrue() }
    }

    @Test
    fun `nothing describing this phones disk syncs`() {
        // artifacts is file names, sizes, hashes and which model is active. Every
        // value in it is false on another device.
        assertThat(SyncPolicy.syncs("artifacts")).isFalse()
    }

    @Test
    fun `counters do not sync, because last writer wins would lose counts`() {
        assertThat(SyncPolicy.syncs("discover_quiz_stats")).isFalse()
    }

    @Test
    fun `which moments have been dealt does sync`() {
        // Otherwise a second phone deals moments the reader has already seen,
        // which reads as the app having forgotten.
        assertThat(SyncPolicy.syncs("discover_drawn")).isTrue()
    }

    @Test
    fun `settings are allowlisted, so an unconsidered key stays put`() {
        assertThat(SyncPolicy.syncsSetting("theme")).isTrue()
        assertThat(SyncPolicy.syncsSetting("accent")).isTrue()
        // Device facts, named explicitly so this fails if somebody adds them.
        assertThat(SyncPolicy.syncsSetting("perf.decode.gemma-4-e4b")).isFalse()
        assertThat(SyncPolicy.syncsSetting("voice.installed")).isFalse()
        assertThat(SyncPolicy.syncsSetting(SyncKeys.DEVICE_ID)).isFalse()
        assertThat(SyncPolicy.syncsSetting(SyncKeys.LAMPORT)).isFalse()
        // And the general rule: a key nobody has thought about does not sync.
        assertThat(SyncPolicy.syncsSetting("something.added.later")).isFalse()
    }

    @Test
    fun `every table in the schema has an answer`() {
        // The list exists so that adding a table forces a decision. A table Room
        // knows about and this map does not would sync or not sync by accident.
        val schemaTables = listOf(
            "projects", "conversations", "messages", "memory_entries", "follow_ups",
            "discover_drawn", "discover_quiz_stats", "artifacts", "settings",
        )
        assertThat(SyncPolicy.TABLES.keys).containsExactlyElementsIn(schemaTables)
    }

    // ---- Wire version ----------------------------------------------------

    @Test
    fun `the same version is compatible`() {
        assertThat(SyncWire.check(SyncWire.VERSION)).isEqualTo(SyncWire.Verdict.Compatible)
    }

    @Test
    fun `a newer peer is refused rather than partly applied`() {
        val verdict = SyncWire.check(SyncWire.VERSION + 1)
        assertThat(verdict).isInstanceOf(SyncWire.Verdict.NeedsUpdate::class.java)
        // The wording has to say which phone needs attention. "Sync failed" sends
        // somebody to look at the wrong one.
        val message = (verdict as SyncWire.Verdict.NeedsUpdate).message
        assertThat(message).contains("Update this one")
        assertThat(message).contains("Nothing has been changed")
    }

    @Test
    fun `an older peer is told it is the one that needs updating`() {
        val verdict = SyncWire.check(SyncWire.VERSION - 1)
        assertThat(verdict).isInstanceOf(SyncWire.Verdict.PeerNeedsUpdate::class.java)
        assertThat((verdict as SyncWire.Verdict.PeerNeedsUpdate).message)
            .contains("Update it there")
    }

    @Test
    fun `no user facing sync wording contains an em dash`() {
        // The project rule, applied where it belongs: text a person reads.
        listOf(
            SyncWire.check(SyncWire.VERSION + 1),
            SyncWire.check(SyncWire.VERSION - 1),
        ).forEach { verdict ->
            val text = when (verdict) {
                is SyncWire.Verdict.NeedsUpdate -> verdict.message
                is SyncWire.Verdict.PeerNeedsUpdate -> verdict.message
                SyncWire.Verdict.Compatible -> ""
            }
            assertThat(text).doesNotContain("—")
        }
    }
}
