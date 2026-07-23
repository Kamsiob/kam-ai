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
        followUps = listOf(FollowUpEntity("f1", "snippet", Mode.LOGIC, "c1", "m2", null, "note", false, 1, null)),
        drawn = listOf(DrawnMomentEntity("history", "moment-1", 1, readerOpened = true)),
        saved = listOf(SavedMomentEntity("history", "moment-2", "Title", "History", 5)),
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
        assertEquals(original.saved, restored.saved)
        assertEquals(original.quizStats, restored.quizStats)
        assertEquals(original.artifacts, restored.artifacts)
        assertEquals(original.settings, restored.settings)
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
