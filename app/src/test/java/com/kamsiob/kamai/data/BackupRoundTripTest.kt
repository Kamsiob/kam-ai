package com.kamsiob.kamai.data

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The backup format must round-trip exactly: what goes out comes back identical.
 * These cover the two halves that carry the risk, the JSON codec and the
 * passphrase encryption, without needing the encrypted device database.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private fun sampleSnapshot() = BackupCodec.Snapshot(
        conversations = listOf(
            ConversationEntity(
                id = "c1", title = "A title", mode = Mode.LOGIC, modesUsed = "GENERAL,LOGIC",
                projectId = null, createdAt = 1, updatedAt = 2, pinned = true,
                groundingMomentId = "a passage",
            ),
            ConversationEntity(
                id = "c2", title = null, mode = Mode.DISCOVER, modesUsed = "DISCOVER",
                projectId = "p1", createdAt = 3, updatedAt = 4,
            ),
        ),
        messages = listOf(
            MessageEntity("m1", "c1", Role.USER, "hello", 1),
            MessageEntity("m2", "c1", Role.ASSISTANT, "hi there", 2, incomplete = false, stoppedReason = "You stopped this one."),
        ),
        projects = listOf(ProjectEntity("p1", "Proj", "instructions", "notes", 1, 2)),
        memory = listOf(MemoryEntity("mem1", "remembers this", 1, 2, "c1", auto = true)),
        followUps = listOf(
            FollowUpEntity(
                id = "f1", snippet = "snippet", sourceMode = Mode.LOGIC,
                conversationId = "c1", messageId = "m2", note = "note", createdAt = 1,
            ),
            // A saved Discover moment is a follow-up too now: it carries the moment
            // so it can be reopened, and its source tells it apart in the one list.
            FollowUpEntity(
                id = "f2", snippet = "Title", sourceMode = Mode.DISCOVER,
                packId = "history", momentId = "moment-2", createdAt = 5,
            ),
            // A pursue-kind follow-up saved from Brainstorm.
            FollowUpEntity(
                id = "f3", snippet = "an idea worth chasing", sourceMode = Mode.BRAINSTORM,
                kind = FollowUpKind.PURSUE, createdAt = 6,
            ),
        ),
        drawn = listOf(DrawnMomentEntity("history", "moment-1", 1, readerOpened = true)),
        quizStats = listOf(QuizStatsEntity("history", 3, 12, 9)),
        artifacts = listOf(ArtifactEntity("basic", ArtifactKind.LLM, "Gemma", "basic.gguf", 100, "abc", "1", 1, active = true)),
        settings = listOf(SettingEntity("theme", "dark"), SettingEntity("chats.view", "COMPACT")),
    )

    @Test
    fun codecRoundTripsIdentically() {
        val original = sampleSnapshot()
        val json = BackupCodec.encode(original, "1.0", 3)
        val restored = BackupCodec.decode(JSONObject(json.toString()))

        assertEquals(original.conversations, restored.conversations)
        assertEquals(original.messages, restored.messages)
        assertEquals(original.projects, restored.projects)
        assertEquals(original.memory, restored.memory)
        assertEquals(original.followUps, restored.followUps)
        assertEquals(original.drawn, restored.drawn)
        assertEquals(original.quizStats, restored.quizStats)
        assertEquals(original.artifacts, restored.artifacts)
        assertEquals(original.settings, restored.settings)
    }

    @Test
    fun legacySavedMomentsBecomeFollowUps() {
        // A backup written before saving was unified keeps moments in a separate
        // "saved" array. Importing it must lose nothing: each becomes a DISCOVER
        // follow-up carrying its pack and moment so it can be reopened.
        val legacy = JSONObject(
            """
            {
              "formatVersion": 1,
              "saved": [
                {"packId": "history", "momentId": "m9", "title": "An old save", "topic": "History", "savedAt": 42}
              ]
            }
            """.trimIndent(),
        )
        val restored = BackupCodec.decode(legacy)
        assertEquals(1, restored.followUps.size)
        val fu = restored.followUps.first()
        assertEquals(Mode.DISCOVER, fu.sourceMode)
        assertEquals("An old save", fu.snippet)
        assertEquals("history", fu.packId)
        assertEquals("m9", fu.momentId)
        assertEquals(42L, fu.createdAt)
    }

    @Test
    fun legacyChatModeImportsAsGeneral() {
        // A backup written before the four-mode update stores mode "CHAT" and has
        // no modesUsed or follow-up kind. Importing must map CHAT to GENERAL and
        // default the missing fields rather than throwing on an unknown enum.
        val legacy = JSONObject(
            """
            {
              "formatVersion": 2,
              "conversations": [
                {"id": "c1", "title": "t", "mode": "CHAT", "createdAt": 1, "updatedAt": 2,
                 "pinned": false, "archived": false, "titleIsManual": false}
              ],
              "followUps": [
                {"id": "f1", "snippet": "s", "sourceMode": "CHAT", "completed": false, "createdAt": 1}
              ]
            }
            """.trimIndent(),
        )
        val restored = BackupCodec.decode(legacy)
        assertEquals(Mode.GENERAL, restored.conversations.first().mode)
        assertEquals("GENERAL", restored.conversations.first().modesUsed)
        assertEquals(Mode.GENERAL, restored.followUps.first().sourceMode)
        assertEquals(FollowUpKind.CHECK, restored.followUps.first().kind)
    }

    @Test
    fun cryptoRoundTrips() {
        val plaintext = "the quick brown fox jumps over the lazy dog".toByteArray()
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(plaintext, "correct horse battery", out)
        val decrypted = BackupCrypto.decrypt(ByteArrayInputStream(out.toByteArray()), "correct horse battery")
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt("secret".toByteArray(), "right", out)
        assertThrows(BackupCrypto.WrongPassphraseException::class.java) {
            BackupCrypto.decrypt(ByteArrayInputStream(out.toByteArray()), "wrong")
        }
    }

    @Test
    fun garbageIsNotABackup() {
        assertThrows(BackupCrypto.NotABackupException::class.java) {
            BackupCrypto.decrypt(ByteArrayInputStream("not a backup file".toByteArray()), "x")
        }
    }

    @Test
    fun `a backup carries the sync stamps`() {
        // A restore is how a second device gets its first copy of everything. If
        // the stamps are dropped, that device starts counting from one while the
        // original is in the thousands, and the original then wins every
        // disagreement between them regardless of which edit came later.
        val snapshot = BackupCodec.Snapshot(
            conversations = listOf(
                ConversationEntity(
                    id = "c1", title = "Stamped", mode = Mode.GENERAL, modesUsed = "GENERAL",
                    createdAt = 1, updatedAt = 2, rev = 41, lastWriterId = "device-a",
                ),
            ),
            messages = listOf(
                MessageEntity(
                    id = "m1", conversationId = "c1", role = Role.USER, content = "Hello",
                    createdAt = 3, memoriesUsed = 2, rev = 42, lastWriterId = "device-a",
                ),
            ),
            projects = emptyList(), memory = emptyList(), followUps = emptyList(),
            drawn = emptyList(), quizStats = emptyList(), artifacts = emptyList(),
            settings = listOf(SettingEntity("theme", "dark", 43, "device-b")),
        )

        val back = BackupCodec.decode(BackupCodec.encode(snapshot, "1.0", 9))

        assertEquals(41L, back.conversations.single().rev)
        assertEquals("device-a", back.conversations.single().lastWriterId)
        assertEquals(42L, back.messages.single().rev)
        assertEquals(43L, back.settings.single().rev)
        assertEquals("device-b", back.settings.single().lastWriterId)
        // memoriesUsed was never written to a backup before this change, so an
        // answer that used memory lost that fact on every restore. Fixed here
        // because it is the same list of fields.
        assertEquals(2, back.messages.single().memoriesUsed)
    }

    @Test
    fun `an older backup with no stamps imports as unstamped`() {
        // A version 2 file has no rev or lastWriterId keys at all. Those rows must
        // import as rev 0 with no writer, which loses to any real write rather
        // than overwriting one. Failing to import, or importing as rev 1, would
        // both be worse.
        val root = JSONObject(
            """
            {
              "formatVersion": 2,
              "conversations": [
                {"id":"c1","title":"Old","mode":"GENERAL","modesUsed":"GENERAL",
                 "createdAt":1,"updatedAt":2,"pinned":false,"archived":false,
                 "titleIsManual":false}
              ],
              "settings": [{"key":"theme","value":"dark"}]
            }
            """.trimIndent(),
        )

        val back = BackupCodec.decode(root)

        assertEquals(0L, back.conversations.single().rev)
        assertEquals("", back.conversations.single().lastWriterId)
        assertEquals(0L, back.settings.single().rev)
        // And unstamped has to lose, or restoring an old backup would silently
        // beat edits made since.
        assertEquals(
            Reconcile.Winner.REMOTE,
            Reconcile.winner(local = Stamp(0, ""), remote = Stamp(1, "device-a")),
        )
    }

}
