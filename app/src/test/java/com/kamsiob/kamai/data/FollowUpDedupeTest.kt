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
 * One follow-up per message (#128).
 *
 * The bookmark is the app's single save action and Follow-ups is meant to be the
 * one place saved things live, so the same reply saved twice is that list
 * disagreeing with itself.
 *
 * It happened because the bookmark icon forgot its state when a conversation was
 * reopened, so the honest response to a grey bookmark on an already-saved reply
 * was to tap it. The display is fixed separately; this makes the data right
 * whatever the display does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowUpDedupeTest {

    private lateinit var db: KamDatabase
    private lateinit var repository: KamRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KamRepository(context, db)
    }

    @After
    fun tearDown() = db.close()

    /** follow_ups references conversations, so the row has to exist first. */
    private suspend fun seedConversation(id: String) {
        db.conversations().upsert(
            ConversationEntity(
                id = id, title = id, mode = Mode.GENERAL,
                createdAt = 0L, updatedAt = 0L,
            ),
        )
    }

    @Test
    fun savingTheSameReplyTwiceKeepsOneEntry() = runTest {
        seedConversation("c1")
        val first = repository.flag("Rest a steak for 5 to 10 minutes.", Mode.GENERAL, "c1", "m1")
        val second = repository.flag("Rest a steak for 5 to 10 minutes.", Mode.GENERAL, "c1", "m1")

        assertThat(second).isEqualTo(first)
        assertThat(db.followUps().allForBackup().filter { it.messageId == "m1" }).hasSize(1)
    }

    @Test
    fun differentRepliesAreSeparateEntries() = runTest {
        seedConversation("c1")
        repository.flag("First reply.", Mode.GENERAL, "c1", "m1")
        repository.flag("Second reply.", Mode.GENERAL, "c1", "m2")
        assertThat(db.followUps().allForBackup()).hasSize(2)
    }

    @Test
    fun somethingSavedWithNoMessageStillSaves() = runTest {
        // A saved Discover moment has no message id, and two of those are two
        // different saves rather than a duplicate.
        repository.flag("A passage.", Mode.DISCOVER, null, null)
        repository.flag("Another passage.", Mode.DISCOVER, null, null)
        assertThat(db.followUps().allForBackup()).hasSize(2)
    }
}
