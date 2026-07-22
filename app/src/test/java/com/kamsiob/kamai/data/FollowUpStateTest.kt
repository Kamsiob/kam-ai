package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Follow-up state transitions. The feature is deliberately simple, which is
 * exactly why its few states have to be right: flag, complete, uncomplete,
 * remove, and the open count the nav badge reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowUpStateTest {

    private lateinit var db: KamDatabase
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KamDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun flag(id: String, snippet: String = "something to check") {
        db.followUps().upsert(
            FollowUpEntity(
                id = id, snippet = snippet, sourceMode = Mode.CHAT, createdAt = now,
            ),
        )
    }

    @Test
    fun `a flagged item starts open`() = runTest {
        flag("f1")

        val open = db.followUps().observeOpen().first()
        assertThat(open.map { it.id }).containsExactly("f1")
        assertThat(open.single().completed).isFalse()
        assertThat(open.single().completedAt).isNull()
        assertThat(db.followUps().observeCompleted().first()).isEmpty()
    }

    @Test
    fun `completing moves it into the completed group and stamps the time`() = runTest {
        flag("f1")

        db.followUps().setCompleted("f1", completed = true, at = now + 500)

        assertThat(db.followUps().observeOpen().first()).isEmpty()
        val done = db.followUps().observeCompleted().first().single()
        assertThat(done.completed).isTrue()
        assertThat(done.completedAt).isEqualTo(now + 500)
    }

    @Test
    fun `uncompleting sends it back to open and clears the timestamp`() = runTest {
        flag("f1")
        db.followUps().setCompleted("f1", completed = true, at = now + 500)

        db.followUps().setCompleted("f1", completed = false, at = null)

        assertThat(db.followUps().observeOpen().first().map { it.id }).containsExactly("f1")
        assertThat(db.followUps().byId("f1")!!.completedAt).isNull()
    }

    @Test
    fun `the open count only counts open items`() = runTest {
        flag("f1")
        flag("f2")
        flag("f3")
        db.followUps().setCompleted("f2", completed = true, at = now + 1)

        assertThat(db.followUps().observeOpenCount().first()).isEqualTo(2)
    }

    @Test
    fun `completed items sort by when they were completed`() = runTest {
        flag("first")
        flag("second")
        db.followUps().setCompleted("first", completed = true, at = now + 100)
        db.followUps().setCompleted("second", completed = true, at = now + 200)

        assertThat(db.followUps().observeCompleted().first().map { it.id })
            .containsExactly("second", "first").inOrder()
    }

    @Test
    fun `removing a completed item takes it out entirely`() = runTest {
        flag("f1")
        db.followUps().setCompleted("f1", completed = true, at = now + 1)

        db.followUps().delete("f1")

        assertThat(db.followUps().byId("f1")).isNull()
        assertThat(db.followUps().observeCompleted().first()).isEmpty()
    }

    @Test
    fun `a note and a project link can be added later`() = runTest {
        flag("f1")
        db.projects().upsert(
            ProjectEntity("p1", "Project", "instructions", now, now),
        )

        db.followUps().setNote("f1", "check the second claim")
        db.followUps().setProject("f1", "p1")

        val item = db.followUps().byId("f1")!!
        assertThat(item.note).isEqualTo("check the second claim")
        assertThat(item.projectId).isEqualTo("p1")
    }

    @Test
    fun `deleting the source conversation keeps the follow up but drops the link`() = runTest {
        db.conversations().upsert(
            ConversationEntity(
                id = "c1", title = "t", mode = Mode.CHAT,
                createdAt = now, updatedAt = now,
            ),
        )
        db.followUps().upsert(
            FollowUpEntity(
                id = "f1", snippet = "s", sourceMode = Mode.CHAT,
                conversationId = "c1", createdAt = now,
            ),
        )

        db.conversations().delete("c1")

        // The flag is the point, so it survives. The link back simply goes away.
        val item = db.followUps().byId("f1")
        assertThat(item).isNotNull()
        assertThat(item!!.conversationId).isNull()
        assertThat(item.snippet).isEqualTo("s")
    }

    @Test
    fun `the source mode is carried so the chip can say where it came from`() = runTest {
        db.followUps().upsert(
            FollowUpEntity(
                id = "f1", snippet = "s", sourceMode = Mode.DISCOVER, createdAt = now,
            ),
        )
        assertThat(db.followUps().byId("f1")!!.sourceMode).isEqualTo(Mode.DISCOVER)
    }
}
