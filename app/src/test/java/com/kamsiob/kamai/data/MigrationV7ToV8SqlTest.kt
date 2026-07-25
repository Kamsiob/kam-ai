package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The version 7 to version 8 migration, driven over a real SQLite database.
 *
 * Same approach as the three before it: the statements come from
 * `KamDatabase.MIGRATION_7_8_SQL`, so the migration that ships and the migration
 * that is tested cannot drift apart.
 *
 * One column on `messages`, which is the largest table in the app and the one
 * whose rows are least replaceable. Everything else here is about proving that
 * every message comes out the other side exactly as it went in.
 */
class MigrationV7ToV8SqlTest {

    private lateinit var dbFile: File
    private lateinit var db: Connection

    /** The version 7 messages table, the only one this migration touches. */
    private val v7Tables = listOf(
        """
        CREATE TABLE messages (
            id TEXT NOT NULL PRIMARY KEY,
            conversationId TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            incomplete INTEGER NOT NULL DEFAULT 0,
            stoppedReason TEXT
        )
        """.trimIndent(),
    )

    /** A real transcript: both roles, a system note, one left incomplete by a
     *  process death, one stopped early, and content with the quotes, newlines
     *  and Markdown that answers are full of. */
    private val seed = listOf(
        "INSERT INTO messages VALUES ('m1','c1','USER','What is a roux?',100,0,NULL)",
        "INSERT INTO messages VALUES ('m2','c1','ASSISTANT','Flour and fat, cooked together.',110,0,NULL)",
        "INSERT INTO messages VALUES ('m3','c1','SYSTEM','Switched to Logic Partner.',120,0,NULL)",
        "INSERT INTO messages VALUES ('m4','c1','ASSISTANT','Half an ans',130,1,NULL)",
        "INSERT INTO messages VALUES ('m5','c1','ASSISTANT','Ran out of room.',140,0,'You stopped this one.')",
        "INSERT INTO messages VALUES ('m6','c2','ASSISTANT','### Heading" +
            "\nShe said \"no\".',150,0,NULL)",
    )

    private val allIds = listOf("m1", "m2", "m3", "m4", "m5", "m6")

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kam-migration-78-", ".db").apply { delete() }
        db = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        db.createStatement().use { s -> v7Tables.forEach(s::executeUpdate) }
        db.createStatement().use { s -> seed.forEach(s::executeUpdate) }
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun migrate() {
        db.createStatement().use { s -> KamDatabase.MIGRATION_7_8_SQL.forEach(s::executeUpdate) }
    }

    private fun one(sql: String): String? =
        db.createStatement().use { s ->
            s.executeQuery(sql).use { r -> if (r.next()) r.getString(1) else null }
        }

    private fun count(table: String): Int = one("SELECT COUNT(*) FROM $table")!!.toInt()

    private fun columns(table: String): Set<String> =
        db.createStatement().use { s ->
            s.executeQuery("PRAGMA table_info($table)").use { r ->
                buildSet { while (r.next()) add(r.getString("name")) }
            }
        }

    private fun row(id: String): Map<String, String?> =
        db.createStatement().use { s ->
            s.executeQuery("SELECT * FROM messages WHERE id = '$id'").use { r ->
                r.next()
                val meta = r.metaData
                buildMap {
                    for (i in 1..meta.columnCount) put(meta.getColumnName(i), r.getString(i))
                }
            }
        }

    @Test
    fun theMemoriesUsedColumnAppears() {
        assertThat(columns("messages")).doesNotContain("memoriesUsed")
        migrate()
        assertThat(columns("messages")).contains("memoriesUsed")
    }

    @Test
    fun everyMessageSurvives() {
        val before = count("messages")
        migrate()
        assertThat(count("messages")).isEqualTo(before)
        assertThat(count("messages")).isEqualTo(6)
    }

    @Test
    fun everyExistingAnswerRecordsZeroRatherThanAGuess() {
        // Some of these answers probably did use memory. Nothing recorded it at
        // the time, and inventing a number for them would put a claim in the
        // transcript that nobody can check.
        migrate()
        allIds.forEach { assertThat(row(it)["memoriesUsed"]).isEqualTo("0") }
    }

    @Test
    fun nothingElseAboutAnyMessageChanges() {
        val before = allIds.associateWith { row(it) }
        migrate()
        before.forEach { (id, old) ->
            val now = row(id)
            old.keys.forEach { column -> assertThat(now[column]).isEqualTo(old[column]) }
        }
    }

    @Test
    fun markdownQuotesAndNewlinesInAnAnswerSurviveIntact() {
        migrate()
        assertThat(row("m6")["content"]).isEqualTo("### Heading\nShe said \"no\".")
    }

    @Test
    fun theIncompleteFlagAndStopReasonSurvive() {
        migrate()
        assertThat(row("m4")["incomplete"]).isEqualTo("1")
        assertThat(row("m5")["stoppedReason"]).isEqualTo("You stopped this one.")
        assertThat(row("m2")["stoppedReason"]).isNull()
    }

    @Test
    fun anInterruptedMigrationRollsBackAndCanBeRunAgain() {
        db.autoCommit = false
        db.createStatement().use { s -> KamDatabase.MIGRATION_7_8_SQL.forEach(s::executeUpdate) }
        db.rollback()
        db.autoCommit = true

        assertThat(columns("messages")).doesNotContain("memoriesUsed")
        assertThat(count("messages")).isEqualTo(6)

        migrate()
        assertThat(columns("messages")).contains("memoriesUsed")
        assertThat(count("messages")).isEqualTo(6)
    }

    @Test
    fun aCountCanBeWrittenAndReadBackAfterTheMigration() {
        migrate()
        db.createStatement().use {
            it.executeUpdate("UPDATE messages SET memoriesUsed = 3 WHERE id = 'm2'")
        }
        assertThat(row("m2")["memoriesUsed"]).isEqualTo("3")
        assertThat(row("m2")["content"]).isEqualTo("Flour and fat, cooked together.")
        // And its neighbours are untouched by the write.
        assertThat(row("m1")["memoriesUsed"]).isEqualTo("0")
    }
}
