package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A whole auto-archive pass over a real database, and the undo that puts it back
 * (#31).
 *
 * `AutoArchivePolicyTest` proves the decision: the boundary, the exemptions, the
 * second pass finding nothing. It is a pure function over a list, which leaves
 * the parts either side of it untested — the query that decides what the policy
 * even sees, and the bulk update that carries the decision out. Both are places
 * this can go wrong in ways the policy test cannot notice: a query that quietly
 * omits pinned rows, or a bulk archive that also moves the timestamps and makes
 * undo restore conversations to the wrong place in the list.
 *
 * The clock is passed in rather than read, so "four days ago" is exact instead
 * of nearly right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArchiveRoundTripTest {

    private lateinit var db: KamDatabase

    private val now = 1_700_000_000_000L
    private fun daysAgo(n: Int) = now - n * AutoArchivePolicy.MILLIS_PER_DAY

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KamDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(
        id: String,
        updatedAt: Long,
        pinned: Boolean = false,
        archived: Boolean = false,
    ) {
        db.conversations().upsert(
            ConversationEntity(
                id = id,
                title = id,
                mode = Mode.GENERAL,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                pinned = pinned,
                archived = archived,
            ),
        )
    }

    /** The state a used install is in when somebody first turns this on. */
    private suspend fun seedUsedInstall() {
        seed("old", daysAgo(10))
        seed("older", daysAgo(40))
        seed("yesterday", daysAgo(1))
        seed("pinned-and-old", daysAgo(60), pinned = true)
        seed("already-archived", daysAgo(90), archived = true)
        seed("open-right-now", daysAgo(30))
    }

    private suspend fun due(
        policy: AutoArchive,
        openConversationId: String? = null,
    ): List<String> = AutoArchivePolicy.due(
        conversations = db.conversations().activeForAutoArchive(),
        policy = policy,
        now = now,
        openConversationId = openConversationId,
    )

    private suspend fun archivedIds(): Set<String> =
        db.conversations().allForBackup().filter { it.archived }.map { it.id }.toSet()

    private suspend fun updatedAtOf(id: String): Long =
        db.conversations().allForBackup().first { it.id == id }.updatedAt

    @Test
    fun `the query hands the policy everything unarchived, including pinned`() = runTest {
        seedUsedInstall()
        // The exemption is the policy's job. A query that filtered pinned rows out
        // itself would look identical from the outside and would silently make
        // the policy's pinned rule dead code.
        val seen = db.conversations().activeForAutoArchive().map { it.id }
        assertThat(seen).containsExactly(
            "old", "older", "yesterday", "pinned-and-old", "open-right-now",
        )
    }

    @Test
    fun `a pass takes the old ones and leaves everything it should`() = runTest {
        seedUsedInstall()
        val ids = due(AutoArchive.DAYS_7, openConversationId = "open-right-now")
        db.conversations().setArchivedBulk(ids, archived = true)

        assertThat(archivedIds()).containsExactly("old", "older", "already-archived")
    }

    @Test
    fun `undo puts back exactly what the pass took, and nothing else`() = runTest {
        seedUsedInstall()
        val ids = due(AutoArchive.DAYS_7)
        db.conversations().setArchivedBulk(ids, archived = true)
        db.conversations().setArchivedBulk(ids, archived = false)

        // "already-archived" was archived before the pass and must stay that way:
        // undo restores the pass, not the whole archive.
        assertThat(archivedIds()).containsExactly("already-archived")
    }

    @Test
    fun `archiving does not move the timestamps, which is what makes undo exact`() = runTest {
        seedUsedInstall()
        val before = listOf("old", "older").associateWith { updatedAtOf(it) }
        val ids = due(AutoArchive.DAYS_7)
        db.conversations().setArchivedBulk(ids, archived = true)
        db.conversations().setArchivedBulk(ids, archived = false)

        // Touching updatedAt here would put restored conversations at the top of
        // Chats instead of back where the user left them.
        before.forEach { (id, at) -> assertThat(updatedAtOf(id)).isEqualTo(at) }
    }

    @Test
    fun `a second pass straight after the first finds nothing to do`() = runTest {
        seedUsedInstall()
        val first = due(AutoArchive.DAYS_7)
        db.conversations().setArchivedBulk(first, archived = true)

        assertThat(due(AutoArchive.DAYS_7)).isEmpty()
    }

    @Test
    fun `the window length changes what a pass takes`() = runTest {
        seedUsedInstall()
        // Same data, same code, different setting. Three days reaches everything
        // but yesterday's; thirty reaches the forty-day-old one and the
        // thirty-day-old one, which sits exactly on the boundary and is taken —
        // the inclusive rule, arrived at from the other direction.
        assertThat(due(AutoArchive.DAYS_3)).containsExactly("old", "older", "open-right-now")
        assertThat(due(AutoArchive.DAYS_30)).containsExactly("older", "open-right-now")
    }

    @Test
    fun `off takes nothing from a database full of old conversations`() = runTest {
        seedUsedInstall()
        assertThat(due(AutoArchive.OFF)).isEmpty()
    }

    @Test
    fun `an empty database is a no-op rather than an error`() = runTest {
        assertThat(due(AutoArchive.DAYS_3)).isEmpty()
        db.conversations().setArchivedBulk(emptyList(), archived = true)
        assertThat(archivedIds()).isEmpty()
    }
}
