package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The version 5 to version 6 migration, driven over a real SQLite database.
 *
 * Same approach and the same reasoning as [MigrationSqlTest], which proved the 4
 * to 5 migration: the statements come from `KamDatabase.MIGRATION_5_6_SQL`, the
 * list the shipped `Migration` object executes, so the migration that ships and
 * the migration that is tested cannot drift apart.
 *
 * This one adds a single nullable column and touches nothing else, which is the
 * least destructive change available. The tests are therefore mostly about
 * proving that "touches nothing else" is true, because the failure that matters
 * here is not the column failing to appear, it is a user's conversations being
 * altered on the way past.
 */
class MigrationV5ToV6SqlTest {

    private lateinit var dbFile: File
    private lateinit var db: Connection

    /** The version 5 conversations table, the only one this migration touches. */
    private val v5Tables = listOf(
        """
        CREATE TABLE conversations (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT,
            mode TEXT NOT NULL,
            modesUsed TEXT NOT NULL DEFAULT 'GENERAL',
            projectId TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            pinned INTEGER NOT NULL DEFAULT 0,
            archived INTEGER NOT NULL DEFAULT 0,
            titleIsManual INTEGER NOT NULL DEFAULT 0,
            groundingMomentId TEXT
        )
        """.trimIndent(),
    )

    /** A used install: several modes, a pinned row, an archived one, a project,
     *  a manual title, and a grounded Discover discussion. */
    private val seed = listOf(
        "INSERT INTO conversations VALUES ('c1','Bread','GENERAL','GENERAL',NULL,100,200,0,0,0,NULL)",
        "INSERT INTO conversations VALUES ('c2','Argument','LOGIC','GENERAL,LOGIC',NULL,110,210,1,0,0,NULL)",
        "INSERT INTO conversations VALUES ('c3',NULL,'BRAINSTORM','BRAINSTORM','p1',120,220,0,1,0,NULL)",
        "INSERT INTO conversations VALUES ('c4','My own name','BENCH','BENCH',NULL,130,230,0,0,1,NULL)",
        "INSERT INTO conversations VALUES ('c5','Troy','DISCOVER','DISCOVER',NULL,140,240,0,0,0,'moment-9')",
    )

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kam-migration-56-", ".db").apply { delete() }
        db = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        db.createStatement().use { s -> v5Tables.forEach(s::executeUpdate) }
        db.createStatement().use { s -> seed.forEach(s::executeUpdate) }
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun migrate() {
        db.createStatement().use { s -> KamDatabase.MIGRATION_5_6_SQL.forEach(s::executeUpdate) }
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
            s.executeQuery("SELECT * FROM conversations WHERE id = '$id'").use { r ->
                r.next()
                val meta = r.metaData
                buildMap {
                    for (i in 1..meta.columnCount) put(meta.getColumnName(i), r.getString(i))
                }
            }
        }

    @Test
    fun theLinkColumnAppears() {
        assertThat(columns("conversations")).doesNotContain("linkedConversationId")
        migrate()
        assertThat(columns("conversations")).contains("linkedConversationId")
    }

    @Test
    fun everyConversationSurvives() {
        val before = count("conversations")
        migrate()
        assertThat(count("conversations")).isEqualTo(before)
        assertThat(count("conversations")).isEqualTo(5)
    }

    @Test
    fun everyExistingConversationIsSimplyUnlinked() {
        // Which is what it was. Null rather than an empty string, so "no link" has
        // one representation and the code never has to check for two.
        migrate()
        listOf("c1", "c2", "c3", "c4", "c5").forEach { id ->
            assertThat(row(id)["linkedConversationId"]).isNull()
        }
    }

    @Test
    fun nothingElseAboutAnyRowChanges() {
        // The failure that matters here is not the column failing to appear, it is
        // a user's conversations being altered on the way past.
        val before = listOf("c1", "c2", "c3", "c4", "c5").associateWith { row(it) }
        migrate()
        before.forEach { (id, old) ->
            val now = row(id)
            old.keys.forEach { column ->
                assertThat(now[column]).isEqualTo(old[column])
            }
        }
    }

    @Test
    fun theModesAndTheirUsedSetsAreUntouched() {
        migrate()
        assertThat(row("c2")["mode"]).isEqualTo("LOGIC")
        assertThat(row("c2")["modesUsed"]).isEqualTo("GENERAL,LOGIC")
        assertThat(row("c4")["mode"]).isEqualTo("BENCH")
    }

    @Test
    fun pinnedArchivedProjectManualTitleAndGroundingAllSurvive() {
        migrate()
        assertThat(row("c2")["pinned"]).isEqualTo("1")
        assertThat(row("c3")["archived"]).isEqualTo("1")
        assertThat(row("c3")["projectId"]).isEqualTo("p1")
        assertThat(row("c4")["titleIsManual"]).isEqualTo("1")
        assertThat(row("c4")["title"]).isEqualTo("My own name")
        assertThat(row("c5")["groundingMomentId"]).isEqualTo("moment-9")
    }

    @Test
    fun anInterruptedMigrationRollsBackAndCanBeRunAgain() {
        // A process killed mid upgrade amounts to a transaction that never
        // commits. The database has to come back as version 5 exactly, with no
        // half-added column, and the next launch has to be able to run it again.
        db.autoCommit = false
        db.createStatement().use { s -> KamDatabase.MIGRATION_5_6_SQL.forEach(s::executeUpdate) }
        db.rollback()
        db.autoCommit = true

        assertThat(columns("conversations")).doesNotContain("linkedConversationId")
        assertThat(count("conversations")).isEqualTo(5)

        migrate()
        assertThat(columns("conversations")).contains("linkedConversationId")
        assertThat(count("conversations")).isEqualTo(5)
    }

    @Test
    fun aLinkCanBeWrittenAndReadBackAfterTheMigration() {
        migrate()
        db.createStatement().use {
            it.executeUpdate("UPDATE conversations SET linkedConversationId = 'c1' WHERE id = 'c4'")
            it.executeUpdate("UPDATE conversations SET linkedConversationId = 'c4' WHERE id = 'c1'")
        }
        // Both directions, which is the point of storing it on both rows.
        assertThat(row("c4")["linkedConversationId"]).isEqualTo("c1")
        assertThat(row("c1")["linkedConversationId"]).isEqualTo("c4")
    }
}
