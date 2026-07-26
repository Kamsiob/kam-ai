package com.kamsiob.kamai.data

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The version 8 to version 9 migration, driven over a real SQLite database.
 *
 * Same approach as the four before it: the statements come from
 * `KamDatabase.MIGRATION_8_9_SQL`, so the migration that ships and the migration
 * that is tested cannot drift apart.
 *
 * This is the widest migration so far, touching seven tables, which is exactly
 * the kind that is worth driving over a real database rather than reasoning
 * about. The things it has to prove:
 *
 * - Every existing row survives, with every value it went in with.
 * - The new columns default to the "written before sync existed" values, and
 *   those values sort below any real write.
 * - The tombstones table exists, is keyed correctly, and is empty. A migration
 *   that invented a tombstone would delete somebody's data on the next sync.
 * - It can be run twice. Not because Room runs it twice, but because a migration
 *   interrupted partway is a real state, and the `IF NOT EXISTS` clauses are
 *   there to make the second attempt survivable.
 */
class MigrationV8ToV9SqlTest {

    private lateinit var dbFile: File
    private lateinit var db: Connection

    /** The version 8 tables this migration touches, as they were before it. */
    private val v8Tables = listOf(
        """
        CREATE TABLE projects (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            instructions TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            archived INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
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
            linkedConversationId TEXT,
            groundingMomentId TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE messages (
            id TEXT NOT NULL PRIMARY KEY,
            conversationId TEXT NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            incomplete INTEGER NOT NULL DEFAULT 0,
            stoppedReason TEXT,
            memoriesUsed INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE memory_entries (
            id TEXT NOT NULL PRIMARY KEY,
            text TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            sourceConversationId TEXT,
            auto INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE follow_ups (
            id TEXT NOT NULL PRIMARY KEY,
            snippet TEXT NOT NULL,
            sourceMode TEXT NOT NULL,
            conversationId TEXT,
            messageId TEXT,
            projectId TEXT,
            note TEXT,
            packId TEXT,
            momentId TEXT,
            kind TEXT NOT NULL DEFAULT 'CHECK',
            completed INTEGER NOT NULL DEFAULT 0,
            createdAt INTEGER NOT NULL,
            completedAt INTEGER
        )
        """.trimIndent(),
        """
        CREATE TABLE discover_drawn (
            packId TEXT NOT NULL,
            momentId TEXT NOT NULL,
            drawnAt INTEGER NOT NULL,
            readerOpened INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(packId, momentId)
        )
        """.trimIndent(),
        """
        CREATE TABLE settings (
            key TEXT NOT NULL PRIMARY KEY,
            value TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /**
     * Real content, including the awkward parts: an untitled conversation, a
     * message left incomplete by a process death, prose with quotes, newlines and
     * Markdown, a completed follow-up, and a project holding instructions and
     * notes separately.
     */
    private val seed = listOf(
        "INSERT INTO projects VALUES ('p1','Bakery rebrand','Be concise.'," +
            "'The client is a bakery in Leeds.',100,200,0)",
        "INSERT INTO conversations VALUES ('c1','What is a roux?','GENERAL'," +
            "'GENERAL,LOGIC','p1',100,300,1,0,0,NULL,NULL)",
        "INSERT INTO conversations VALUES ('c2',NULL,'BRAINSTORM','BRAINSTORM'," +
            "NULL,400,400,0,0,0,'c3',NULL)",
        "INSERT INTO messages VALUES ('m1','c1','USER','What is a roux?',100,0,NULL,0)",
        "INSERT INTO messages VALUES ('m2','c1','ASSISTANT','### Flour and fat" +
            "\nShe said \"no\".',110,0,NULL,3)",
        "INSERT INTO messages VALUES ('m3','c1','ASSISTANT','Half an ans',120,1,NULL,0)",
        "INSERT INTO memory_entries VALUES ('e1','Prefers short answers.',10,20,'c1',1)",
        "INSERT INTO follow_ups VALUES ('f1','Check the oven temperature','LOGIC'," +
            "'c1','m2',NULL,'might be 180C',NULL,NULL,'CHECK',0,500,NULL)",
        "INSERT INTO follow_ups VALUES ('f2','History of opera','DISCOVER'," +
            "NULL,NULL,NULL,NULL,'pack-history','moment-opera','PURSUE',1,600,700)",
        "INSERT INTO discover_drawn VALUES ('pack-history','moment-opera',800,1)",
        "INSERT INTO settings VALUES ('theme','dark')",
        "INSERT INTO settings VALUES ('onboarding.done','true')",
    )

    @Before
    fun setUp() {
        dbFile = File.createTempFile("kam-migration-89-", ".db").apply { delete() }
        db = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        db.createStatement().use { s -> v8Tables.forEach(s::executeUpdate) }
        db.createStatement().use { s -> seed.forEach(s::executeUpdate) }
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
    }

    private fun migrate() {
        db.createStatement().use { s ->
            KamDatabase.MIGRATION_8_9_SQL.forEach(s::executeUpdate)
        }
    }

    private fun <T> one(sql: String, read: (java.sql.ResultSet) -> T): T =
        db.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                rs.next()
                read(rs)
            }
        }

    private fun count(sql: String): Int = one(sql) { it.getInt(1) }

    @Test
    fun `every row survives`() {
        migrate()
        assertThat(count("SELECT COUNT(*) FROM projects")).isEqualTo(1)
        assertThat(count("SELECT COUNT(*) FROM conversations")).isEqualTo(2)
        assertThat(count("SELECT COUNT(*) FROM messages")).isEqualTo(3)
        assertThat(count("SELECT COUNT(*) FROM memory_entries")).isEqualTo(1)
        assertThat(count("SELECT COUNT(*) FROM follow_ups")).isEqualTo(2)
        assertThat(count("SELECT COUNT(*) FROM discover_drawn")).isEqualTo(1)
        assertThat(count("SELECT COUNT(*) FROM settings")).isEqualTo(2)
    }

    @Test
    fun `content comes out exactly as it went in`() {
        migrate()
        // The message with Markdown, a newline and an escaped quote in it, which
        // is what real answers look like and what a careless migration mangles.
        val content = one("SELECT content FROM messages WHERE id = 'm2'") { it.getString(1) }
        assertThat(content).isEqualTo("### Flour and fat\nShe said \"no\".")
        assertThat(one("SELECT memoriesUsed FROM messages WHERE id = 'm2'") { it.getInt(1) })
            .isEqualTo(3)
        assertThat(one("SELECT incomplete FROM messages WHERE id = 'm3'") { it.getInt(1) })
            .isEqualTo(1)

        // Instructions and notes stay separate, which is the whole reason there
        // are two columns.
        assertThat(one("SELECT instructions FROM projects WHERE id = 'p1'") { it.getString(1) })
            .isEqualTo("Be concise.")
        assertThat(one("SELECT notes FROM projects WHERE id = 'p1'") { it.getString(1) })
            .isEqualTo("The client is a bakery in Leeds.")

        // The untitled conversation stays untitled rather than becoming an empty
        // string, since the interface tells those apart.
        assertThat(one("SELECT title IS NULL FROM conversations WHERE id = 'c2'") { it.getInt(1) })
            .isEqualTo(1)
        assertThat(
            one("SELECT modesUsed FROM conversations WHERE id = 'c1'") { it.getString(1) },
        ).isEqualTo("GENERAL,LOGIC")
        assertThat(
            one("SELECT linkedConversationId FROM conversations WHERE id = 'c2'") { it.getString(1) },
        ).isEqualTo("c3")

        // A saved Discover moment keeps what lets it be reopened.
        assertThat(one("SELECT packId FROM follow_ups WHERE id = 'f2'") { it.getString(1) })
            .isEqualTo("pack-history")
        assertThat(one("SELECT completedAt FROM follow_ups WHERE id = 'f2'") { it.getLong(1) })
            .isEqualTo(700)
    }

    @Test
    fun `existing rows read as written before sync existed`() {
        migrate()
        val tables = listOf(
            "projects", "conversations", "messages", "memory_entries",
            "follow_ups", "discover_drawn", "settings",
        )
        tables.forEach { table ->
            assertThat(count("SELECT COUNT(*) FROM $table WHERE rev != 0")).isEqualTo(0)
            assertThat(count("SELECT COUNT(*) FROM $table WHERE lastWriterId != ''")).isEqualTo(0)
        }
        // And that state must lose to any real write, or the migration would
        // quietly prefer stale copies over edits made after it.
        assertThat(
            Reconcile.winner(local = Stamp(0, ""), remote = Stamp(1, "device-a")),
        ).isEqualTo(Reconcile.Winner.REMOTE)
    }

    @Test
    fun `the tombstones table exists and is empty`() {
        migrate()
        // Empty is the important half. A migration that invented tombstones would
        // delete real content the first time anything synced.
        assertThat(count("SELECT COUNT(*) FROM tombstones")).isEqualTo(0)
        db.createStatement().use { s ->
            s.executeUpdate(
                "INSERT INTO tombstones VALUES ('conversations','c9',5,'device-a',900)",
            )
        }
        assertThat(count("SELECT COUNT(*) FROM tombstones")).isEqualTo(1)
    }

    @Test
    fun `a tombstone cannot be recorded twice for the same row`() {
        migrate()
        db.createStatement().use { s ->
            s.executeUpdate("INSERT INTO tombstones VALUES ('conversations','c9',5,'a',900)")
        }
        val second = runCatching {
            db.createStatement().use { s ->
                s.executeUpdate("INSERT INTO tombstones VALUES ('conversations','c9',6,'b',950)")
            }
        }
        assertThat(second.isFailure).isTrue()
        // The composite key is what makes that fail, and it has to be composite:
        // the same id can legitimately exist in two tables.
        db.createStatement().use { s ->
            s.executeUpdate("INSERT INTO tombstones VALUES ('messages','c9',7,'a',960)")
        }
        assertThat(count("SELECT COUNT(*) FROM tombstones")).isEqualTo(2)
    }

    @Test
    fun `the index the only query needs is there`() {
        migrate()
        val hasIndex = one(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
                "AND name = 'index_tombstones_rev'",
        ) { it.getInt(1) }
        assertThat(hasIndex).isEqualTo(1)
    }

    @Test
    fun `an interrupted migration can be run again`() {
        // Not because Room would run it twice, but because a migration killed
        // partway through is a real state on a real phone, and the second attempt
        // has to be survivable. Only the CREATE statements are guarded, so this
        // proves the guards are on the right ones.
        migrate()
        val creates = KamDatabase.MIGRATION_8_9_SQL.filter { it.startsWith("CREATE") }
        assertThat(creates).hasSize(2)
        db.createStatement().use { s -> creates.forEach(s::executeUpdate) }
        assertThat(count("SELECT COUNT(*) FROM tombstones")).isEqualTo(0)
        assertThat(count("SELECT COUNT(*) FROM messages")).isEqualTo(3)
    }
}
