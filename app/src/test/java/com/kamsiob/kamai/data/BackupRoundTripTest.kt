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
            ConversationEntity("c1", "A title", Mode.CHAT, null, 1, 2, pinned = true, groundingMomentId = "a passage"),
            ConversationEntity("c2", null, Mode.DISCOVER, "p1", 3, 4),
        ),
        messages = listOf(
            MessageEntity("m1", "c1", Role.USER, "hello", 1),
            MessageEntity("m2", "c1", Role.ASSISTANT, "hi there", 2, incomplete = false, stoppedReason = "You stopped this one."),
        ),
        projects = listOf(ProjectEntity("p1", "Proj", "instructions", 1, 2)),
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
}
