package com.kamsiob.kamai.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Round-trip on the real, encrypted device database: populate it, export through
 * the full BackupManager (encode + encrypt), wipe, then import (decrypt + decode +
 * write) and confirm the data came back. This is the spec's populated-database
 * gate, exercised against SQLCipher rather than a fake.
 */
@RunWith(AndroidJUnit4::class)
class BackupDbRoundTripTest {

    @Test
    fun populatedDatabaseSurvivesExportWipeImport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = KamRepository.get(context)

        // Start clean so counts are deterministic.
        repo.deleteEverything(includeDownloads = false)

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

        // Clean up so the device is not left with test data.
        repo.deleteEverything(includeDownloads = false)
    }
}
