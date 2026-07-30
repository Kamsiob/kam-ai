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
 * Deleting a chat has to delete the document attached to it.
 *
 * An attached document's full text is stored as a setting keyed by conversation
 * (`attach.text.<id>`) rather than on the conversation row, and every delete path
 * removed rows without touching settings. So a user could attach something
 * private, delete the chat, and the document stayed on the device in full and came
 * back in the next backup.
 *
 * The copy is what makes it a defect rather than an untidiness. Deleting one chat
 * says "This removes the conversation and everything in it" and then "It will be
 * gone for good". Delete everything says "Every conversation ... This erases all of
 * it and cannot be undone."
 *
 * Found on the device, not by reading: a chat with an attachment was deleted
 * through the interface, a backup was exported, and the file still carried
 * `attach.text.<id>` with the whole document in it.
 *
 * Three paths reach a deleted conversation and all three are covered here, because
 * two of them delete the row directly instead of calling the first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentDeletionTest {

    private lateinit var db: KamDatabase
    private lateinit var repository: KamRepository

    private val now = 1_700_000_000_000L
    private val document = "The loading bay at Thornwick Halloway is repainted every March."

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

    private suspend fun chatWithAttachment(id: String, projectId: String? = null): String {
        db.conversations().upsert(
            ConversationEntity(
                id = id, title = "A chat", mode = Mode.GENERAL, projectId = projectId,
                createdAt = now, updatedAt = now,
            ),
        )
        repository.setAttachment(id, "site-note.txt", document)
        assertThat(repository.attachmentText(id)).isEqualTo(document)
        return id
    }

    /** Nothing anywhere in settings still holds the document. */
    private suspend fun assertNoDocumentAnywhere() {
        val leftovers = db.settings().all().filter { it.value.contains("Thornwick Halloway") }
        assertThat(leftovers.map { it.key }).isEmpty()
    }

    @Test
    fun `deleting a chat deletes the document attached to it`() = runTest {
        val id = chatWithAttachment("c1")

        repository.deleteConversation(id)

        assertThat(repository.attachmentText(id)).isNull()
        assertThat(repository.attachmentName(id)).isNull()
        assertNoDocumentAnywhere()
    }

    @Test
    fun `deleting a chat leaves another chat's document alone`() = runTest {
        val gone = chatWithAttachment("c1")
        val kept = chatWithAttachment("c2")

        repository.deleteConversation(gone)

        assertThat(repository.attachmentText(gone)).isNull()
        assertThat(repository.attachmentText(kept)).isEqualTo(document)
    }

    @Test
    fun `deleting a project with its chats deletes their documents`() = runTest {
        db.projects().upsert(
            ProjectEntity(
                id = "p1", name = "A project", instructions = "", notes = "",
                createdAt = now, updatedAt = now,
            ),
        )
        val id = chatWithAttachment("c1", projectId = "p1")

        repository.deleteProject("p1", deleteConversations = true)

        assertThat(repository.attachmentText(id)).isNull()
        assertNoDocumentAnywhere()
    }

    @Test
    fun `keeping the chats when a project goes keeps their documents`() = runTest {
        db.projects().upsert(
            ProjectEntity(
                id = "p1", name = "A project", instructions = "", notes = "",
                createdAt = now, updatedAt = now,
            ),
        )
        val id = chatWithAttachment("c1", projectId = "p1")

        repository.deleteProject("p1", deleteConversations = false)

        // The chat survives, so its document has to survive with it.
        assertThat(repository.attachmentText(id)).isEqualTo(document)
    }

    @Test
    fun `delete everything deletes every document`() = runTest {
        chatWithAttachment("c1")
        chatWithAttachment("c2")

        repository.deleteEverything(includeDownloads = false)

        assertThat(repository.attachmentText("c1")).isNull()
        assertThat(repository.attachmentText("c2")).isNull()
        assertNoDocumentAnywhere()
    }

    @Test
    fun `delete everything keeps settings that are not user content`() = runTest {
        chatWithAttachment("c1")
        db.settings().put(SettingEntity(KamRepository.Keys.ONBOARDING_DONE, "true"))

        repository.deleteEverything(includeDownloads = false)

        // The prefix sweep has to be a scalpel. Wiping settings wholesale would
        // send somebody who cleared their data back through onboarding.
        assertThat(db.settings().get(KamRepository.Keys.ONBOARDING_DONE)).isEqualTo("true")
        assertNoDocumentAnywhere()
    }

    @Test
    fun `a deleted chat's document is not in the next backup`() = runTest {
        val id = chatWithAttachment("c1")

        repository.deleteConversation(id)

        val snapshot = repository.exportSnapshot()
        assertThat(snapshot.settings.map { it.key }.filter { it.startsWith("attach.") }).isEmpty()
        assertThat(snapshot.settings.none { it.value.contains("Thornwick Halloway") }).isTrue()
    }
}
