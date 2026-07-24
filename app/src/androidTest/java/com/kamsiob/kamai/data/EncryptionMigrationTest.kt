package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The at-rest encryption and, most importantly, the migration of an existing
 * plaintext database into it without losing a single row. PART 3.
 *
 * This is the test that has to be right: a person upgrading has real
 * conversations in a plaintext database, and the migration is the one moment
 * where they could be lost.
 */
@RunWith(AndroidJUnit4::class)
class EncryptionMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-test.db"
    private val dbFile: File get() = context.getDatabasePath(dbName)

    @Before
    fun clean() {
        System.loadLibrary("sqlcipher")
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"),
            File(dbFile.parentFile, "$dbName.migrating")).forEach { it.delete() }
    }

    @After
    fun tearDown() = clean()

    private val now = 1_700_000_000_000L

    /** Builds a plaintext Room database with some real data in it. */
    private fun seedPlaintext(): Int {
        val db = Room.databaseBuilder(context, KamDatabase::class.java, dbName).build()
        var count = 0
        runBlocking {
            repeat(7) { i ->
                db.conversations().upsert(
                    ConversationEntity(
                        id = "c$i", title = "Conversation $i", mode = Mode.GENERAL,
                        createdAt = now, updatedAt = now + i,
                    ),
                )
                db.messages().insert(
                    MessageEntity(
                        id = "m$i", conversationId = "c$i", role = Role.USER,
                        content = "a secret worth protecting number $i", createdAt = now,
                    ),
                )
                count++
            }
            db.memory().upsert(MemoryEntity("mem1", "the user prefers plain words", now, now))
        }
        db.close()
        return count
    }

    private fun openEncrypted(passphrase: ByteArray): KamDatabase {
        val factory = DatabaseEncryption.openHelperFactory(context, dbFile, passphrase)
        return Room.databaseBuilder(context, KamDatabase::class.java, dbName)
            .openHelperFactory(factory)
            .build()
    }

    @Test
    fun plaintextDataSurvivesMigrationIntact() = runBlocking {
        val seeded = seedPlaintext()
        assertThat(DatabaseEncryption.isPlaintext(dbFile)).isTrue()

        // Open through the encrypting factory, which migrates on the way.
        val passphrase = ByteArray(32) { (it + 1).toByte() }
        val db = openEncrypted(passphrase)

        val conversations = db.conversations().observeActive().first()
        assertThat(conversations).hasSize(seeded)
        assertThat(conversations.map { it.title })
            .containsExactlyElementsIn((0 until seeded).map { "Conversation $it" })
        assertThat(db.memory().observeAll().first().single().text)
            .isEqualTo("the user prefers plain words")
        db.close()

        // The file on disk is no longer plaintext.
        assertThat(DatabaseEncryption.isPlaintext(dbFile)).isFalse()
    }

    @Test
    fun theMigratedFileIsUnreadableAsPlaintext() = runBlocking {
        seedPlaintext()
        val passphrase = ByteArray(32) { (it + 9).toByte() }
        openEncrypted(passphrase).close()

        // The secret content must not appear anywhere in the raw bytes.
        val raw = dbFile.readBytes()
        val needle = "a secret worth protecting".toByteArray(Charsets.US_ASCII)
        assertThat(indexOf(raw, needle)).isEqualTo(-1)

        // And it does not start with the SQLite magic header.
        assertThat(DatabaseEncryption.isPlaintext(dbFile)).isFalse()
    }

    @Test
    fun theWrongPassphraseCannotOpenTheDatabase() = runBlocking {
        seedPlaintext()
        val right = ByteArray(32) { (it + 3).toByte() }
        openEncrypted(right).close()

        val wrong = ByteArray(32) { (it + 4).toByte() }
        val failed = runCatching {
            val factory = SupportOpenHelperFactory(wrong)
            val db = Room.databaseBuilder(context, KamDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .build()
            db.conversations().count() // forces the open
            db.close()
        }.isFailure
        assertThat(failed).isTrue()
    }

    @Test
    fun anInterruptedMigrationRestartsFromTheUntouchedPlaintext() = runBlocking {
        val seeded = seedPlaintext()
        // Simulate a crash mid-migration: a leftover staging file, plaintext
        // still in place.
        File(dbFile.parentFile, "$dbName.migrating").writeBytes(ByteArray(128))
        assertThat(DatabaseEncryption.isPlaintext(dbFile)).isTrue()

        val passphrase = ByteArray(32) { (it + 5).toByte() }
        val db = openEncrypted(passphrase)
        // The partial was discarded and the real plaintext migrated cleanly.
        assertThat(db.conversations().observeActive().first()).hasSize(seeded)
        db.close()
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
