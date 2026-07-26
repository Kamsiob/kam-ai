package com.kamsiob.kamai.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Round-trip through the full BackupManager: populate a database, export
 * (encode + encrypt), wipe, then import (decrypt + decode + write) and confirm
 * the data came back.
 *
 * **Runs against an in-memory database, deliberately.** It used to call
 * `KamRepository.get(context)`, which in an instrumentation test is the real
 * one, and then `deleteEverything()` on it. Anybody running
 * `./gradlew connectedAndroidTest` on a phone with Kam AI on it destroyed every
 * conversation, memory, follow-up, project and Discover row they had, with no
 * warning and no undo. The test passed, so nothing looked wrong.
 *
 * A test that wipes the database has to own the database. This one builds its
 * own and throws it away at the end.
 */
@RunWith(AndroidJUnit4::class)
class BackupDbRoundTripTest {

    private lateinit var db: KamDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun populatedDatabaseSurvivesExportWipeImport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = androidx.room.Room
            .inMemoryDatabaseBuilder(context, KamDatabase::class.java)
            .build()
        val repo = KamRepository(context, db)

        val convId = repo.createConversation(Mode.GENERAL)
        repo.addMessage(convId, Role.USER, "a backup test question")
        repo.addMessage(convId, Role.ASSISTANT, "a backup test answer", incomplete = false)
        repo.remember("the user is testing backups", convId, auto = false)

        val manager = BackupManager(repo, "test", BackupCodec.FORMAT_VERSION)
        val out = ByteArrayOutputStream()
        manager.export(out, "a good passphrase")
        val backup = out.toByteArray()
        assertTrue("backup should be non-trivial", backup.size > 200)

        // Wipe, then restore.
        repo.deleteEverything(includeDownloads = false)
        val before = repo.exportSnapshot()
        assertEquals("wipe should clear conversations", 0, before.conversations.size)

        val result = manager.import(ByteArrayInputStream(backup), "a good passphrase", replace = true)
        assertTrue(result.message, result.ok)

        val after = repo.exportSnapshot()
        assertEquals(1, after.conversations.count { it.id == convId })
        assertEquals(2, after.messages.count { it.conversationId == convId })
        assertTrue(after.memory.any { it.text == "the user is testing backups" })

        val msgs = after.messages.filter { it.conversationId == convId }.sortedBy { it.createdAt }
        assertEquals("a backup test question", msgs[0].content)
        assertEquals("a backup test answer", msgs[1].content)

    }

    @Test
    fun aRestoreInterruptedPartWayThroughLosesNothing() = runBlocking {
        // The failure this guards: replace-mode restore deletes everything and
        // then re-inserts the backup row by row. Interrupt the second half and
        // the user is left with the first half done, their conversations gone
        // and the replacement only partly written. Backing out of the screen was
        // enough to do it, because the coroutine belonged to the composition.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = androidx.room.Room
            .inMemoryDatabaseBuilder(context, KamDatabase::class.java)
            .build()
        val repo = KamRepository(context, db)

        val convId = repo.createConversation(Mode.GENERAL)
        repo.addMessage(convId, Role.USER, "the message that must survive")

        // A snapshot big enough that the restore is still running when it is
        // canceled, and unrelated to what is already here, so a completed
        // restore would be obvious.
        val incoming = repo.exportSnapshot().let { snap ->
            snap.copy(
                conversations = List(400) { n ->
                    snap.conversations.first().copy(id = "incoming-$n", title = "incoming $n")
                },
                messages = List(4000) { n ->
                    snap.messages.first().copy(
                        id = "im-$n",
                        conversationId = "incoming-${n % 400}",
                        content = "incoming message $n",
                    )
                },
            )
        }

        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            repo.importSnapshot(incoming, replace = true)
        }
        kotlinx.coroutines.delay(15)
        job.cancelAndJoin()

        // Either the restore finished before the cancel landed, or it rolled
        // back completely. What must never happen is the wipe surviving without
        // the restore, which is the state that loses the user's conversations.
        val after = repo.exportSnapshot()
        val restored = after.conversations.any { it.id == "incoming-0" }
        if (!restored) {
            assertEquals("rolled back, so the original must be intact", 1, after.conversations.size)
            assertTrue(
                "the original message must still be there",
                after.messages.any { it.content == "the message that must survive" },
            )
        }
        assertTrue(
            "the database must never be left empty",
            after.conversations.isNotEmpty(),
        )
    }
}
