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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KamDatabaseTest {

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

    private suspend fun seedConversation(
        id: String = "c1",
        title: String? = "A conversation",
        pinned: Boolean = false,
        archived: Boolean = false,
        updatedAt: Long = now,
        projectId: String? = null,
    ): ConversationEntity {
        val c = ConversationEntity(
            id = id, title = title, mode = Mode.GENERAL, projectId = projectId,
            createdAt = now, updatedAt = updatedAt, pinned = pinned, archived = archived,
        )
        db.conversations().upsert(c)
        return c
    }

    private suspend fun seedMessage(
        conversationId: String,
        id: String,
        role: Role,
        content: String,
        createdAt: Long,
    ) = db.messages().insert(
        MessageEntity(
            id = id, conversationId = conversationId, role = role,
            content = content, createdAt = createdAt,
        ),
    )

    @Test
    fun `a conversation round trips`() = runTest {
        val c = seedConversation()
        assertThat(db.conversations().byId("c1")).isEqualTo(c)
    }

    @Test
    fun `the list snippet is the most recent message not the first`() = runTest {
        seedConversation()
        seedMessage("c1", "m1", Role.USER, "first thing", now)
        seedMessage("c1", "m2", Role.ASSISTANT, "an answer", now + 1)
        seedMessage("c1", "m3", Role.USER, "most recent thing", now + 2)

        val row = db.conversations().observeActive().first().single()
        assertThat(row.snippet).isEqualTo("most recent thing")
        assertThat(row.messageCount).isEqualTo(3)
    }

    @Test
    fun `pinned conversations sort above recent ones`() = runTest {
        seedConversation(id = "old-pinned", pinned = true, updatedAt = now - 100_000)
        seedConversation(id = "new-unpinned", pinned = false, updatedAt = now)

        val ids = db.conversations().observeActive().first().map { it.id }
        assertThat(ids).containsExactly("old-pinned", "new-unpinned").inOrder()
    }

    @Test
    fun `archived conversations leave the active list and appear in archived`() = runTest {
        seedConversation()
        db.conversations().setArchived("c1", archived = true, now = now + 1)

        assertThat(db.conversations().observeActive().first()).isEmpty()
        assertThat(db.conversations().observeArchived().first().map { it.id })
            .containsExactly("c1")
    }

    @Test
    fun `search matches titles and message bodies`() = runTest {
        seedConversation(id = "c1", title = "Lighthouse plans")
        seedConversation(id = "c2", title = "Something else")
        seedMessage("c2", "m1", Role.USER, "what about a lighthouse", now)

        // SQLite's LIKE is case insensitive for ASCII, which is the behaviour a
        // person expects from a search box, so capitalisation must not matter.
        assertThat(db.conversations().search("Lighthouse").first().map { it.id })
            .containsExactly("c1", "c2")
        assertThat(db.conversations().search("lighthouse").first().map { it.id })
            .containsExactly("c1", "c2")

        // Title-only and body-only matches each work on their own.
        assertThat(db.conversations().search("plans").first().map { it.id })
            .containsExactly("c1")
        assertThat(db.conversations().search("what about").first().map { it.id })
            .containsExactly("c2")
        assertThat(db.conversations().search("nothing here").first()).isEmpty()
    }

    @Test
    fun `deleting a conversation takes its messages with it`() = runTest {
        seedConversation()
        seedMessage("c1", "m1", Role.USER, "hello", now)

        db.conversations().delete("c1")

        assertThat(db.messages().forConversation("c1")).isEmpty()
    }

    @Test
    fun `editing truncates every message after the edited one`() = runTest {
        seedConversation()
        seedMessage("c1", "m1", Role.USER, "first", now)
        seedMessage("c1", "m2", Role.ASSISTANT, "answer", now + 1)
        seedMessage("c1", "m3", Role.USER, "second", now + 2)
        seedMessage("c1", "m4", Role.ASSISTANT, "answer two", now + 3)

        // Editing m1 removes everything created after it. There is no branching.
        db.messages().deleteAfter("c1", after = now)

        assertThat(db.messages().forConversation("c1").map { it.id }).containsExactly("m1")
    }

    @Test
    fun `messages left mid stream are repaired rather than shown as finished`() = runTest {
        seedConversation()
        db.messages().insert(
            MessageEntity(
                id = "m1", conversationId = "c1", role = Role.ASSISTANT,
                content = "half an ans", createdAt = now, incomplete = true,
            ),
        )

        assertThat(db.messages().repairIncomplete()).isEqualTo(1)

        val repaired = db.messages().byId("m1")!!
        assertThat(repaired.incomplete).isFalse()
        assertThat(repaired.stoppedReason).isNotNull()
        assertThat(repaired.content).isEqualTo("half an ans")
    }

    @Test
    fun `deleting a project leaves its conversations alone but unlinked`() = runTest {
        db.projects().upsert(
            ProjectEntity(
                id = "p1", name = "A project", instructions = "Be brief",
                createdAt = now, updatedAt = now,
            ),
        )
        seedConversation(projectId = "p1")

        db.projects().delete("p1")

        val survivor = db.conversations().byId("c1")
        assertThat(survivor).isNotNull()
        assertThat(survivor!!.projectId).isNull()
    }

    @Test
    fun `an artifact of a kind can only be active one at a time`() = runTest {
        val base = ArtifactEntity(
            id = "", kind = ArtifactKind.LLM, displayName = "", fileName = "",
            sizeBytes = 1, sha256 = "x", version = "1", installedAt = now,
        )
        db.artifacts().upsert(base.copy(id = "a", displayName = "A", fileName = "a.gguf"))
        db.artifacts().upsert(base.copy(id = "b", displayName = "B", fileName = "b.gguf"))

        db.artifacts().setActive(ArtifactKind.LLM, "a")
        assertThat(db.artifacts().active(ArtifactKind.LLM)!!.id).isEqualTo("a")

        db.artifacts().setActive(ArtifactKind.LLM, "b")
        assertThat(db.artifacts().active(ArtifactKind.LLM)!!.id).isEqualTo("b")
        assertThat(db.artifacts().observeByKind(ArtifactKind.LLM).first().count { it.active })
            .isEqualTo(1)
    }

    @Test
    fun `settings round trip`() = runTest {
        db.settings().put(SettingEntity("chats.view", "compact"))
        assertThat(db.settings().get("chats.view")).isEqualTo("compact")

        db.settings().put(SettingEntity("chats.view", "grid"))
        assertThat(db.settings().get("chats.view")).isEqualTo("grid")
    }

    // Workbench pairing lifecycle (#32). These run at all only because the test
    // task moved to JDK 21; the whole class used to fail before any assertion.

    @Test
    fun aLinkIsWrittenOnBothSidesSoEitherCanFollowIt() = runTest {
        seedConversation(id = "session")
        seedConversation(id = "chat")
        db.conversations().setLink("session", "chat")
        db.conversations().setLink("chat", "session")

        assertThat(db.conversations().linkOf("session")).isEqualTo("chat")
        assertThat(db.conversations().linkOf("chat")).isEqualTo("session")
    }

    @Test
    fun anUnlinkedConversationHasNoLink() = runTest {
        seedConversation(id = "alone")
        assertThat(db.conversations().linkOf("alone")).isNull()
    }

    @Test
    fun breakingTheLinkClearsBothSides() = runTest {
        // Otherwise the other half is left pointing at a conversation that no
        // longer considers itself linked, and offers to open it.
        seedConversation(id = "session")
        seedConversation(id = "chat")
        db.conversations().setLink("session", "chat")
        db.conversations().setLink("chat", "session")

        db.conversations().setLink("session", null)
        db.conversations().setLink("chat", null)

        assertThat(db.conversations().linkOf("session")).isNull()
        assertThat(db.conversations().linkOf("chat")).isNull()
    }

    @Test
    fun linkingDoesNotDisturbAnythingElseAboutEitherRow() = runTest {
        seedConversation(id = "session", title = "Tightened", pinned = true)
        seedConversation(id = "chat", title = "About it")
        val before = db.conversations().byId("session")!!

        db.conversations().setLink("session", "chat")

        val after = db.conversations().byId("session")!!
        assertThat(after.title).isEqualTo(before.title)
        assertThat(after.pinned).isEqualTo(before.pinned)
        assertThat(after.updatedAt).isEqualTo(before.updatedAt)
        assertThat(after.mode).isEqualTo(before.mode)
        assertThat(after.linkedConversationId).isEqualTo("chat")
    }

    @Test
    fun aWorkbenchSessionIsRewrittenRatherThanAppendedTo() = runTest {
        // A session is the current state of one piece of text, not a transcript of
        // every attempt, so running again replaces its two messages.
        seedConversation(id = "session")
        db.messages().insert(MessageEntity("m1", "session", Role.USER, "first ask", now))
        db.messages().insert(MessageEntity("m2", "session", Role.ASSISTANT, "first result", now + 1))

        db.messages().deleteForConversation("session")
        db.messages().insert(MessageEntity("m3", "session", Role.USER, "second ask", now + 2))
        db.messages().insert(MessageEntity("m4", "session", Role.ASSISTANT, "second result", now + 3))

        val messages = db.messages().forConversation("session")
        assertThat(messages).hasSize(2)
        assertThat(messages.map { it.content })
            .containsExactly("second ask", "second result").inOrder()
    }
}
