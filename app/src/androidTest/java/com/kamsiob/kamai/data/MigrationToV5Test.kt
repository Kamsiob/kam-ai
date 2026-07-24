package com.kamsiob.kamai.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four-mode migration, version 4 to version 5, against a populated database.
 *
 * This is the one migration in the project that could cost somebody data they
 * hold nowhere else: it rewrites a stored mode name in two tables and adds two
 * columns. Issue #24 closed on the strength of the app launching with existing
 * conversations intact, which is evidence but not proof, so this drives the real
 * MIGRATION_4_5 object over a real version 4 database carrying every table and
 * asserts, row by row, that nothing was lost or rewritten wrongly.
 *
 * The tables below are the exported version 4 schema (app/schemas/.../4.json)
 * verbatim, so this is the shape a user's database actually had. Like
 * SchemaMigrationTest, it drives the migration directly rather than through
 * Room's MigrationTestHelper, whose schema-json validator pulls in a conflicting
 * kotlinx-serialization runtime on this toolchain.
 */
@RunWith(AndroidJUnit4::class)
class MigrationToV5Test {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-v4-to-v5-test.db"

    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() {
        if (::helper.isInitialized) helper.close()
        context.deleteDatabase(dbName)
    }

    /** Every version 4 table, created and populated the way a real install was. */
    private fun openPopulatedV4(): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                V4_TABLES.forEach(db::execSQL)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {}
        }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build(),
        )
        val db = helper.writableDatabase
        SEED.forEach(db::execSQL)
        return db
    }

    private fun SupportSQLiteDatabase.one(sql: String): String? =
        query(sql).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); c.getInt(0) }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info($table)").use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }

    @Test
    fun everyConversationSurvivesAndChatBecomesGeneral() {
        val db = openPopulatedV4()
        KamDatabase.MIGRATION_4_5.migrate(db)

        // Nothing is dropped. Five conversations in, five out.
        assertThat(db.count("conversations")).isEqualTo(5)

        // The two former Chat conversations are General now, and the modes that
        // were never renamed are untouched.
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c3'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c2'")).isEqualTo("LOGIC")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c4'")).isEqualTo("DISCOVER")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c5'")).isEqualTo("BENCH")

        // modesUsed is seeded from the post-rename mode, so a former Chat row
        // records GENERAL rather than a stale CHAT that the dots would not draw.
        assertThat(db.one("SELECT modesUsed FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT modesUsed FROM conversations WHERE id = 'c2'")).isEqualTo("LOGIC")
        assertThat(db.one("SELECT modesUsed FROM conversations WHERE id = 'c4'")).isEqualTo("DISCOVER")
        assertThat(db.one("SELECT modesUsed FROM conversations WHERE id = 'c5'")).isEqualTo("BENCH")

        // Nothing else on the row moved: title, flags, project, grounding, times.
        assertThat(db.one("SELECT title FROM conversations WHERE id = 'c1'"))
            .isEqualTo("Boat trailer bearings")
        assertThat(db.one("SELECT pinned FROM conversations WHERE id = 'c1'")).isEqualTo("1")
        assertThat(db.one("SELECT titleIsManual FROM conversations WHERE id = 'c1'")).isEqualTo("1")
        assertThat(db.one("SELECT projectId FROM conversations WHERE id = 'c3'")).isEqualTo("p1")
        assertThat(db.one("SELECT archived FROM conversations WHERE id = 'c5'")).isEqualTo("1")
        assertThat(db.one("SELECT groundingMomentId FROM conversations WHERE id = 'c4'"))
            .isEqualTo("m-42")
        assertThat(db.one("SELECT createdAt FROM conversations WHERE id = 'c1'")).isEqualTo("1000")
        assertThat(db.one("SELECT updatedAt FROM conversations WHERE id = 'c1'")).isEqualTo("2000")
    }

    @Test
    fun followUpsKeepTheirSourceAndGainTheCheckKind() {
        val db = openPopulatedV4()
        KamDatabase.MIGRATION_4_5.migrate(db)

        assertThat(db.count("follow_ups")).isEqualTo(3)

        // The same Chat to General rewrite, in the other place a mode is stored.
        assertThat(db.one("SELECT sourceMode FROM follow_ups WHERE id = 'f1'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT sourceMode FROM follow_ups WHERE id = 'f2'")).isEqualTo("DISCOVER")
        assertThat(db.one("SELECT sourceMode FROM follow_ups WHERE id = 'f3'")).isEqualTo("LOGIC")

        // Everything saved before kinds existed is a thing to check.
        assertThat(db.one("SELECT kind FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHECK")
        assertThat(db.one("SELECT kind FROM follow_ups WHERE id = 'f2'")).isEqualTo("CHECK")
        assertThat(db.one("SELECT kind FROM follow_ups WHERE id = 'f3'")).isEqualTo("CHECK")

        // A saved Discover moment still knows which moment it is, so it still
        // reopens as a grounded discussion.
        assertThat(db.one("SELECT packId FROM follow_ups WHERE id = 'f2'")).isEqualTo("history")
        assertThat(db.one("SELECT momentId FROM follow_ups WHERE id = 'f2'")).isEqualTo("m-42")
        assertThat(db.one("SELECT snippet FROM follow_ups WHERE id = 'f1'"))
            .isEqualTo("Repack the bearings every season.")
        assertThat(db.one("SELECT completed FROM follow_ups WHERE id = 'f3'")).isEqualTo("1")
        assertThat(db.one("SELECT note FROM follow_ups WHERE id = 'f1'")).isEqualTo("before the trip")
    }

    @Test
    fun messagesProjectsMemoryAndDiscoverStateAreUntouched() {
        val db = openPopulatedV4()
        KamDatabase.MIGRATION_4_5.migrate(db)

        assertThat(db.count("messages")).isEqualTo(5)
        assertThat(db.count("projects")).isEqualTo(1)
        assertThat(db.count("memory_entries")).isEqualTo(2)
        assertThat(db.count("discover_drawn")).isEqualTo(2)
        assertThat(db.count("discover_quiz_stats")).isEqualTo(1)
        assertThat(db.count("artifacts")).isEqualTo(1)
        assertThat(db.count("settings")).isEqualTo(2)

        assertThat(db.one("SELECT content FROM messages WHERE id = 'm2'"))
            .isEqualTo("Every season is about right.")
        // An answer that was cut off stays cut off, with its reason.
        assertThat(db.one("SELECT incomplete FROM messages WHERE id = 'm5'")).isEqualTo("1")
        assertThat(db.one("SELECT stoppedReason FROM messages WHERE id = 'm5'"))
            .isEqualTo("You stopped this one.")
        assertThat(db.one("SELECT instructions FROM projects WHERE id = 'p1'"))
            .isEqualTo("Keep answers short and practical.")
        assertThat(db.one("SELECT text FROM memory_entries WHERE id = 'mem2'"))
            .isEqualTo("Owns a boat trailer")
        assertThat(db.one("SELECT auto FROM memory_entries WHERE id = 'mem2'")).isEqualTo("1")
        assertThat(db.one("SELECT questionsRight FROM discover_quiz_stats WHERE packId = 'history'"))
            .isEqualTo("7")
        assertThat(db.one("SELECT value FROM settings WHERE key = 'chats.view'")).isEqualTo("compact")
    }

    /**
     * A migration that dies partway must leave the database exactly as it was, so
     * the next launch can run it again. Room runs each migration inside a
     * transaction; this proves the migration's own statements roll back cleanly
     * when that transaction is never committed, which is what a process kill in
     * the middle of the upgrade amounts to.
     */
    @Test
    fun anInterruptedMigrationRollsBackAndCanBeRunAgain() {
        val db = openPopulatedV4()

        db.beginTransaction()
        try {
            KamDatabase.MIGRATION_4_5.migrate(db)
            // No setTransactionSuccessful: this is the process dying mid-upgrade.
        } finally {
            db.endTransaction()
        }

        // Back to version 4 exactly: no new columns, no half-applied rename.
        assertThat(db.columns("conversations")).doesNotContain("modesUsed")
        assertThat(db.columns("follow_ups")).doesNotContain("kind")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("CHAT")
        assertThat(db.one("SELECT sourceMode FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHAT")
        assertThat(db.count("conversations")).isEqualTo(5)
        assertThat(db.count("messages")).isEqualTo(5)
        assertThat(db.count("follow_ups")).isEqualTo(3)

        // And the retry succeeds, which is what the user gets on the next launch.
        db.beginTransaction()
        try {
            KamDatabase.MIGRATION_4_5.migrate(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        assertThat(db.columns("conversations")).contains("modesUsed")
        assertThat(db.one("SELECT mode FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT modesUsed FROM conversations WHERE id = 'c1'")).isEqualTo("GENERAL")
        assertThat(db.one("SELECT kind FROM follow_ups WHERE id = 'f1'")).isEqualTo("CHECK")
        assertThat(db.count("conversations")).isEqualTo(5)
        assertThat(db.count("messages")).isEqualTo(5)
    }

    private companion object {
        /** The exported version 4 schema, verbatim. */
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

        /** What a used install held: chats in several modes, a project, memory,
         *  saved follow-ups including a Discover moment, and Discover state. */
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
