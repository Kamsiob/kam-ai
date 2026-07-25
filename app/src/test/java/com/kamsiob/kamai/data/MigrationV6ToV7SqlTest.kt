package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The version 6 to version 7 migration, driven over a real SQLite database.
 *
 * Same approach and the same reasoning as [MigrationSqlTest] and
 * [MigrationV5ToV6SqlTest]: the statements come from
 * `KamDatabase.MIGRATION_6_7_SQL`, the list the shipped `Migration` object
 * executes, so the migration that ships and the migration that is tested cannot
 * drift apart.
 *
 * One column, project notes (#2). It is NOT NULL with an empty default, which is
 * the interesting part: a NOT NULL column added to a populated table is exactly
 * the shape that fails when the default is forgotten, and the rows that would
 * fail are somebody's projects and the instructions they wrote for them.
 */
class MigrationV6ToV7SqlTest {

    private lateinit var dbFile: File
    private lateinit var db: Connection

    /** The version 6 projects table, the only one this migration touches. */
    private val v6Tables = listOf(
        """
        CREATE TABLE projects (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            instructions TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            archived INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
    )

    /** A used install: a project with long instructions, one with none, an
     *  archived one, and one whose instructions contain the quotes and newlines
     *  that people actually type. */
    private val seed = listOf(
        "INSERT INTO projects VALUES ('p1','Novel','Set in 1920s Cairo.',100,200,0)",
        "INSERT INTO projects VALUES ('p2','Errands','',110,210,0)",
        "INSERT INTO projects VALUES ('p3','Old thing','Archived but kept.',120,220,1)",
        "INSERT INTO projects VALUES ('p4','Quotes','She said \"no\", then left." +
            "\nThat is the tone.',130,230,0)",
    )

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kam-migration-67-", ".db").apply { delete() }
        db = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        db.createStatement().use { s -> v6Tables.forEach(s::executeUpdate) }
        db.createStatement().use { s -> seed.forEach(s::executeUpdate) }
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun migrate() {
        db.createStatement().use { s -> KamDatabase.MIGRATION_6_7_SQL.forEach(s::executeUpdate) }
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
            s.executeQuery("SELECT * FROM projects WHERE id = '$id'").use { r ->
                r.next()
                val meta = r.metaData
                buildMap {
                    for (i in 1..meta.columnCount) put(meta.getColumnName(i), r.getString(i))
                }
            }
        }

    @Test
    fun theNotesColumnAppears() {
        assertThat(columns("projects")).doesNotContain("notes")
        migrate()
        assertThat(columns("projects")).contains("notes")
    }

    @Test
    fun everyProjectSurvives() {
        val before = count("projects")
        migrate()
        assertThat(count("projects")).isEqualTo(before)
        assertThat(count("projects")).isEqualTo(4)
    }

    @Test
    fun everyExistingProjectGetsEmptyNotesRatherThanNull() {
        // The column is NOT NULL, so the default has to do real work here. Adding
        // a NOT NULL column to a populated table without one fails outright, and
        // the rows it would fail on are somebody's projects.
        migrate()
        listOf("p1", "p2", "p3", "p4").forEach { id ->
            assertThat(row(id)["notes"]).isEqualTo("")
        }
    }

    @Test
    fun nothingElseAboutAnyRowChanges() {
        // The failure that matters is not the column failing to appear, it is
        // instructions somebody wrote being altered on the way past.
        val before = listOf("p1", "p2", "p3", "p4").associateWith { row(it) }
        migrate()
        before.forEach { (id, old) ->
            val now = row(id)
            old.keys.forEach { column ->
                assertThat(now[column]).isEqualTo(old[column])
            }
        }
    }

    @Test
    fun instructionsWithQuotesAndNewlinesSurviveIntact() {
        migrate()
        assertThat(row("p4")["instructions"])
            .isEqualTo("She said \"no\", then left.\nThat is the tone.")
    }

    @Test
    fun anEmptyInstructionsFieldIsStillEmptyAndNotNull() {
        // p2 distinguishes "no instructions" from "no notes": both are empty
        // strings after this, and neither should have become null.
        migrate()
        assertThat(row("p2")["instructions"]).isEqualTo("")
        assertThat(row("p2")["notes"]).isEqualTo("")
    }

    @Test
    fun theArchivedFlagAndTimestampsSurvive() {
        migrate()
        assertThat(row("p3")["archived"]).isEqualTo("1")
        assertThat(row("p3")["createdAt"]).isEqualTo("120")
        assertThat(row("p3")["updatedAt"]).isEqualTo("220")
    }

    @Test
    fun anInterruptedMigrationRollsBackAndCanBeRunAgain() {
        // A process killed mid upgrade amounts to a transaction that never
        // commits. The database has to come back as version 6 exactly, with no
        // half-added column, and the next launch has to be able to run it again.
        db.autoCommit = false
        db.createStatement().use { s -> KamDatabase.MIGRATION_6_7_SQL.forEach(s::executeUpdate) }
        db.rollback()
        db.autoCommit = true

        assertThat(columns("projects")).doesNotContain("notes")
        assertThat(count("projects")).isEqualTo(4)

        migrate()
        assertThat(columns("projects")).contains("notes")
        assertThat(count("projects")).isEqualTo(4)
    }

    @Test
    fun notesCanBeWrittenAndReadBackAfterTheMigration() {
        migrate()
        db.createStatement().use {
            it.executeUpdate(
                "UPDATE projects SET notes = 'The detective is Nadia Rashid.' WHERE id = 'p1'",
            )
        }
        assertThat(row("p1")["notes"]).isEqualTo("The detective is Nadia Rashid.")
        // And the field it sits beside is untouched by the write.
        assertThat(row("p1")["instructions"]).isEqualTo("Set in 1920s Cairo.")
    }
}
