package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The four-mode migration, version 4 to version 5, driven over a real SQLite
 * database on the JVM.
 *
 * This is the one migration that could cost somebody data they hold nowhere
 * else: it rewrites a stored mode name in two tables and adds two columns. It
 * had never been verified beyond "the app still launches with its
 * conversations", which is evidence, not proof.
 *
 * It is tested here, on plain JDBC SQLite, because this build machine can run
 * neither Android-backed alternative: its only JDK is 26, which Robolectric
 * 4.16.1 cannot instrument against, and the Android emulator's qemu process
 * segfaults on this kernel. The phone is not an option either, since it holds
 * the owner's real conversations and there is exactly one installation of the
 * app on it. See DECISIONS.md.
 *
 * What this proves and what it does not. It runs the exact statements the
 * shipped migration runs, in order, taken from KamDatabase.MIGRATION_4_5_SQL
 * rather than copied, so the two cannot drift. It proves the SQL preserves and
 * rewrites the data correctly, and that an interrupted run rolls back cleanly.
 * It does not exercise Room's version bookkeeping or SQLCipher, which need a
 * device; MigrationToV5Test in androidTest covers that path and should be run
 * on any machine that has a working emulator or a spare device.
 */
class MigrationSqlTest {

    private lateinit var dbFile: File
    private lateinit var db: Connection

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kam-migration-", ".db").apply { delete() }
        db = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        db.createStatement().use { s -> V4_TABLES.forEach(s::executeUpdate) }
        db.createStatement().use { s -> SEED.forEach(s::executeUpdate) }
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun migrate() {
        db.createStatement().use { s -> KamDatabase.MIGRATION_4_5_SQL.forEach(s::executeUpdate) }
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

    @Test
    fun everyConversationSurvivesAndChatBecomesGeneral() {
        migrate()

        // Nothing is dropped. Five conversations in, five out.
        assertThat(count("conversations")).isEqualTo(5)

        // The two former Chat conversations are General, and the modes that were
        // never renamed are untouched.
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c3'")).isEqualTo("GENERAL")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c2'")).isEqualTo("LOGIC")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c4'")).isEqualTo("DISCOVER")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c5'")).isEqualTo("BENCH")

        // modesUsed is seeded from the mode after the rename, so a former Chat
        // row records GENERAL rather than a stale CHAT the dots cannot draw.
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c2'")).isEqualTo("LOGIC")
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c4'")).isEqualTo("DISCOVER")
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c5'")).isEqualTo("BENCH")

        // Nothing else on the row moved.
        assertThat(one("SELECT title FROM conversations WHERE id = 'c1'"))
            .isEqualTo("Boat trailer bearings")
        assertThat(one("SELECT pinned FROM conversations WHERE id = 'c1'")).isEqualTo("1")
        assertThat(one("SELECT titleIsManual FROM conversations WHERE id = 'c1'")).isEqualTo("1")
        assertThat(one("SELECT projectId FROM conversations WHERE id = 'c3'")).isEqualTo("p1")
        assertThat(one("SELECT archived FROM conversations WHERE id = 'c5'")).isEqualTo("1")
        assertThat(one("SELECT groundingMomentId FROM conversations WHERE id = 'c4'"))
            .isEqualTo("m-42")
        assertThat(one("SELECT createdAt FROM conversations WHERE id = 'c1'")).isEqualTo("1000")
        assertThat(one("SELECT updatedAt FROM conversations WHERE id = 'c1'")).isEqualTo("2000")
    }

    @Test
    fun followUpsKeepTheirSourceAndGainTheCheckKind() {
        migrate()

        assertThat(count("follow_ups")).isEqualTo(3)

        assertThat(one("SELECT sourceMode FROM follow_ups WHERE id = 'f1'")).isEqualTo("GENERAL")
        assertThat(one("SELECT sourceMode FROM follow_ups WHERE id = 'f2'")).isEqualTo("DISCOVER")
        assertThat(one("SELECT sourceMode FROM follow_ups WHERE id = 'f3'")).isEqualTo("LOGIC")

        // Everything saved before kinds existed is a thing to check.
        assertThat(one("SELECT kind FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHECK")
        assertThat(one("SELECT kind FROM follow_ups WHERE id = 'f2'")).isEqualTo("CHECK")
        assertThat(one("SELECT kind FROM follow_ups WHERE id = 'f3'")).isEqualTo("CHECK")

        // A saved Discover moment still knows which moment it is, so it still
        // reopens as a grounded discussion.
        assertThat(one("SELECT packId FROM follow_ups WHERE id = 'f2'")).isEqualTo("history")
        assertThat(one("SELECT momentId FROM follow_ups WHERE id = 'f2'")).isEqualTo("m-42")
        assertThat(one("SELECT snippet FROM follow_ups WHERE id = 'f1'"))
            .isEqualTo("Repack the bearings every season.")
        assertThat(one("SELECT note FROM follow_ups WHERE id = 'f1'")).isEqualTo("before the trip")
        assertThat(one("SELECT completed FROM follow_ups WHERE id = 'f3'")).isEqualTo("1")
    }

    @Test
    fun messagesProjectsMemoryAndDiscoverStateAreUntouched() {
        migrate()

        assertThat(count("messages")).isEqualTo(5)
        assertThat(count("projects")).isEqualTo(1)
        assertThat(count("memory_entries")).isEqualTo(2)
        assertThat(count("discover_drawn")).isEqualTo(2)
        assertThat(count("discover_quiz_stats")).isEqualTo(1)
        assertThat(count("artifacts")).isEqualTo(1)
        assertThat(count("settings")).isEqualTo(2)

        assertThat(one("SELECT content FROM messages WHERE id = 'm2'"))
            .isEqualTo("Every season is about right.")
        // An answer that was cut off stays cut off, with its reason.
        assertThat(one("SELECT incomplete FROM messages WHERE id = 'm5'")).isEqualTo("1")
        assertThat(one("SELECT stoppedReason FROM messages WHERE id = 'm5'"))
            .isEqualTo("You stopped this one.")
        // A display-only mode marker is still in the transcript.
        assertThat(one("SELECT role FROM messages WHERE id = 'm3'")).isEqualTo("SYSTEM")
        assertThat(one("SELECT instructions FROM projects WHERE id = 'p1'"))
            .isEqualTo("Keep answers short and practical.")
        assertThat(one("SELECT text FROM memory_entries WHERE id = 'mem2'"))
            .isEqualTo("Owns a boat trailer")
        assertThat(one("SELECT auto FROM memory_entries WHERE id = 'mem2'")).isEqualTo("1")
        assertThat(one("SELECT questionsRight FROM discover_quiz_stats WHERE packId = 'history'"))
            .isEqualTo("7")
        assertThat(one("SELECT value FROM settings WHERE key = 'chats.view'")).isEqualTo("compact")
    }

    /**
     * A migration that dies partway must leave the database exactly as it was, so
     * the next launch runs it again from a clean version 4. Room runs each
     * migration inside a transaction; this is that transaction never being
     * committed, which is what a process kill in the middle of an upgrade is.
     */
    @Test
    fun anInterruptedMigrationRollsBackAndCanBeRunAgain() {
        db.autoCommit = false
        db.createStatement().use { s -> KamDatabase.MIGRATION_4_5_SQL.forEach(s::executeUpdate) }
        db.rollback()
        db.autoCommit = true

        // Back to version 4 exactly: no new columns, no half-applied rename.
        assertThat(columns("conversations")).doesNotContain("modesUsed")
        assertThat(columns("follow_ups")).doesNotContain("kind")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("CHAT")
        assertThat(one("SELECT sourceMode FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHAT")
        assertThat(count("conversations")).isEqualTo(5)
        assertThat(count("messages")).isEqualTo(5)
        assertThat(count("follow_ups")).isEqualTo(3)

        // The retry succeeds, which is what the user gets on the next launch.
        migrate()

        assertThat(columns("conversations")).contains("modesUsed")
        assertThat(one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(one("SELECT kind FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHECK")
        assertThat(count("conversations")).isEqualTo(5)
        assertThat(count("messages")).isEqualTo(5)
    }

    /**
     * The rename must not touch a mode that merely contains the old name, and
     * must not invent one. Cheap to check, and the kind of thing a hand-written
     * UPDATE gets wrong.
     */
    @Test
    fun onlyExactChatRowsAreRewritten() {
        db.createStatement().use { s ->
            s.executeUpdate(
                "INSERT INTO conversations VALUES " +
                    "('c6', 'Odd one', 'CHATTY', NULL, 1500, 2500, 0, 0, 0, NULL)",
            )
        }
        migrate()

        assertThat(one("SELECT mode FROM conversations WHERE id = 'c6'")).isEqualTo("CHATTY")
        assertThat(one("SELECT modesUsed FROM conversations WHERE id = 'c6'")).isEqualTo("CHATTY")
        assertThat(count("conversations")).isEqualTo(6)
    }

    private companion object {
        /**
         * The exported version 4 schema, verbatim from
         * app/schemas/com.kamsiob.kamai.data.KamDatabase/4.json, which is the
         * shape a user's database actually had before the four-mode update.
         * MigrationToV5Test in androidTest carries the same fixture for the Room
         * path; if one is corrected the other must be too.
         */
        val V4_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`instructions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT, " +
                "`mode` TEXT NOT NULL, `projectId` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
                "`archived` INTEGER NOT NULL, `titleIsManual` INTEGER NOT NULL, " +
                "`groundingMomentId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) " +
                "REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, " +
                "`conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `incomplete` INTEGER NOT NULL, " +
                "`stoppedReason` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) " +
                "REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `memory_entries` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`sourceConversationId` TEXT, `auto` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `follow_ups` (`id` TEXT NOT NULL, `snippet` TEXT NOT NULL, " +
                "`sourceMode` TEXT NOT NULL, `conversationId` TEXT, `messageId` TEXT, " +
                "`projectId` TEXT, `note` TEXT, `packId` TEXT, `momentId` TEXT, " +
                "`completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) " +
                "REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE TABLE IF NOT EXISTS `discover_drawn` (`packId` TEXT NOT NULL, " +
                "`momentId` TEXT NOT NULL, `drawnAt` INTEGER NOT NULL, " +
                "`readerOpened` INTEGER NOT NULL, PRIMARY KEY(`packId`, `momentId`))",
            "CREATE TABLE IF NOT EXISTS `discover_quiz_stats` (`packId` TEXT NOT NULL, " +
                "`momentsQuizzed` INTEGER NOT NULL, `questionsAsked` INTEGER NOT NULL, " +
                "`questionsRight` INTEGER NOT NULL, PRIMARY KEY(`packId`))",
            "CREATE TABLE IF NOT EXISTS `artifacts` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `fileName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                "`sha256` TEXT NOT NULL, `version` TEXT NOT NULL, `installedAt` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "PRIMARY KEY(`key`))",
        )

        /** What a used install held: chats in several modes, one inside a
         *  project, a pinned one and an archived one, memory, saved follow-ups
         *  including a Discover moment, and Discover state. */
        val SEED = listOf(
            "INSERT INTO projects VALUES ('p1', 'Boat', 'Keep answers short and practical.', 10, 20, 0)",
            "INSERT INTO conversations VALUES " +
                "('c1', 'Boat trailer bearings', 'CHAT', NULL, 1000, 2000, 1, 0, 1, NULL)",
            "INSERT INTO conversations VALUES " +
                "('c2', 'Quitting to day trade', 'LOGIC', NULL, 1100, 2100, 0, 0, 0, NULL)",
            "INSERT INTO conversations VALUES " +
                "('c3', 'Winter storage', 'CHAT', 'p1', 1200, 2200, 0, 0, 0, NULL)",
            "INSERT INTO conversations VALUES " +
                "('c4', 'The Bronze Age', 'DISCOVER', NULL, 1300, 2300, 0, 0, 0, 'm-42')",
            "INSERT INTO conversations VALUES " +
                "('c5', 'Tighten this note', 'BENCH', NULL, 1400, 2400, 0, 1, 0, NULL)",
            "INSERT INTO messages VALUES ('m1', 'c1', 'USER', 'How often do I repack?', 1001, 0, NULL)",
            "INSERT INTO messages VALUES " +
                "('m2', 'c1', 'ASSISTANT', 'Every season is about right.', 1002, 0, NULL)",
            "INSERT INTO messages VALUES " +
                "('m3', 'c2', 'SYSTEM', 'Logic Partner is on.', 1101, 0, NULL)",
            "INSERT INTO messages VALUES ('m4', 'c2', 'USER', 'It is guaranteed money.', 1102, 0, NULL)",
            "INSERT INTO messages VALUES " +
                "('m5', 'c2', 'ASSISTANT', 'That is an assumption', 1103, 1, 'You stopped this one.')",
            "INSERT INTO memory_entries VALUES ('mem1', 'Allergic to shellfish', 30, 30, 'c1', 0)",
            "INSERT INTO memory_entries VALUES ('mem2', 'Owns a boat trailer', 31, 31, 'c1', 1)",
            "INSERT INTO follow_ups VALUES ('f1', 'Repack the bearings every season.', 'CHAT', " +
                "'c1', 'm2', NULL, 'before the trip', NULL, NULL, 0, 40, NULL)",
            "INSERT INTO follow_ups VALUES ('f2', 'The Bronze Age', 'DISCOVER', " +
                "NULL, NULL, NULL, NULL, 'history', 'm-42', 0, 41, NULL)",
            "INSERT INTO follow_ups VALUES ('f3', 'Check the drawdown figure', 'LOGIC', " +
                "'c2', 'm5', NULL, NULL, NULL, NULL, 1, 42, 43)",
            "INSERT INTO discover_drawn VALUES ('history', 'm-42', 50, 1)",
            "INSERT INTO discover_drawn VALUES ('history', 'm-43', 51, 0)",
            "INSERT INTO discover_quiz_stats VALUES ('history', 2, 8, 7)",
            "INSERT INTO artifacts VALUES ('gemma-4-e2b', 'MODEL', 'Basic', 'gemma.gguf', " +
                "3106738272, 'abc', '1', 60, 1)",
            "INSERT INTO settings VALUES ('chats.view', 'compact')",
            "INSERT INTO settings VALUES ('memory.mode', 'MANUAL')",
        )
    }
}
