package com.kamsiob.kamai.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole migration chain, version 1 through version 9, driven by Room itself.
 *
 * **Why this exists when every migration already has a test.** The existing ones
 * each prove their own SQL in isolation: `MigrationSqlTest` and its siblings run
 * the statements over a plain SQLite database on the JVM, and
 * `SchemaMigrationTest` and `MigrationToV5Test` drive two of the migration
 * objects directly on the device. None of them is the thing that actually
 * happens to a user.
 *
 * What happens to a user is that Room opens a database several versions old,
 * runs every migration in sequence, and then **validates the result against its
 * own expected schema**. That last step is the one nothing covered, and it is
 * not theoretical: the version 8 to 9 migration passed its own SQL test and then
 * crashed the application on the phone, because Room validates indices as well
 * as columns and the migration created one the entity had not declared. A test
 * of the SQL alone cannot find that. Only Room can, because Room is the thing
 * complaining.
 *
 * So this seeds a real version 1 database with real content, hands it to Room
 * configured exactly as the application configures it, and asks Room to open it
 * at the current version. If any migration in the chain leaves the schema in a
 * shape Room does not expect, this fails the way the phone failed.
 *
 * **Why the chain rather than each step.** Somebody who installed early and
 * updated once goes 1 to 9 in a single open. Nobody goes 1 to 2 and stops. The
 * intermediate versions are only ever waypoints, and a migration that is correct
 * alone can still be wrong after the one before it.
 *
 * This runs unencrypted on purpose, using the framework helper factory rather
 * than SQLCipher. The encryption layer sits underneath Room and is covered by
 * [EncryptionMigrationTest]; mixing the two here would mean a failure could be
 * either, and the point of this test is to say precisely which.
 */
@RunWith(AndroidJUnit4::class)
class FullMigrationChainTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "full-chain-migration-test.db"

    private var db: KamDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    /**
     * The version 1 schema, taken from `app/schemas/.../1.json`, which is the
     * shape the earliest installs actually carry on disk.
     */
    /**
     * The version 1 schema, generated verbatim from `app/schemas/.../1.json`,
     * indices and foreign keys included.
     *
     * Written out in full rather than approximated, because the first attempt at
     * this test hand-wrote the tables from memory, left out every index and
     * foreign key and one column, and then failed with "Migration didn't
     * properly handle: conversations". That failure was the test being wrong, not
     * the migration, and half an hour went into proving which. The exported
     * schema is the only honest source for what a version 1 database looked like.
     */
    private val v1Tables = listOf(
        "CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT, `mode` TEXT NOT NULL, `projectId` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `groundingMomentId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_conversations_projectId` ON `conversations` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_conversations_updatedAt` ON `conversations` (`updatedAt`)",
        "CREATE INDEX IF NOT EXISTS `index_conversations_pinned` ON `conversations` (`pinned`)",
        "CREATE INDEX IF NOT EXISTS `index_conversations_archived` ON `conversations` (`archived`)",
        "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `incomplete` INTEGER NOT NULL, `stoppedReason` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`)",
        "CREATE INDEX IF NOT EXISTS `index_messages_conversationId_createdAt` ON `messages` (`conversationId`, `createdAt`)",
        "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `instructions` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_projects_updatedAt` ON `projects` (`updatedAt`)",
        "CREATE TABLE IF NOT EXISTS `memory_entries` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sourceConversationId` TEXT, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_memory_entries_updatedAt` ON `memory_entries` (`updatedAt`)",
        "CREATE TABLE IF NOT EXISTS `follow_ups` (`id` TEXT NOT NULL, `snippet` TEXT NOT NULL, `sourceMode` TEXT NOT NULL, `conversationId` TEXT, `messageId` TEXT, `projectId` TEXT, `note` TEXT, `completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_follow_ups_conversationId` ON `follow_ups` (`conversationId`)",
        "CREATE INDEX IF NOT EXISTS `index_follow_ups_projectId` ON `follow_ups` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_follow_ups_completed` ON `follow_ups` (`completed`)",
        "CREATE INDEX IF NOT EXISTS `index_follow_ups_createdAt` ON `follow_ups` (`createdAt`)",
        "CREATE TABLE IF NOT EXISTS `discover_drawn` (`packId` TEXT NOT NULL, `momentId` TEXT NOT NULL, `drawnAt` INTEGER NOT NULL, `readerOpened` INTEGER NOT NULL, PRIMARY KEY(`packId`, `momentId`))",
        "CREATE TABLE IF NOT EXISTS `discover_saved` (`packId` TEXT NOT NULL, `momentId` TEXT NOT NULL, `title` TEXT NOT NULL, `topic` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`packId`, `momentId`))",
        "CREATE TABLE IF NOT EXISTS `discover_quiz_stats` (`packId` TEXT NOT NULL, `momentsQuizzed` INTEGER NOT NULL, `questionsAsked` INTEGER NOT NULL, `questionsRight` INTEGER NOT NULL, PRIMARY KEY(`packId`))",
        "CREATE TABLE IF NOT EXISTS `artifacts` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `displayName` TEXT NOT NULL, `fileName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, `version` TEXT NOT NULL, `installedAt` INTEGER NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_artifacts_kind` ON `artifacts` (`kind`)",
        "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
    )

    /**
     * Content a real early install would hold, including the awkward parts: a
     * conversation in the old CHAT mode that the four-mode migration has to
     * rename, a saved Discover moment that has to survive being folded into
     * follow-ups when that table disappears, an untitled conversation, and prose
     * with the quotes and newlines that answers are full of.
     */
    private val seed = listOf(
        "INSERT INTO projects VALUES ('p1','Bakery rebrand','Be concise.',100,200,0)",
        "INSERT INTO conversations VALUES ('c1','What is a roux?','CHAT','p1',100,300,1,0,NULL)",
        "INSERT INTO conversations VALUES ('c2',NULL,'LOGIC',NULL,400,400,0,0,NULL)",
        "INSERT INTO conversations VALUES ('c3','Ideas','BRAINSTORM',NULL,500,500,0,1,NULL)",
        "INSERT INTO messages VALUES ('m1','c1','USER','What is a roux?',100,0,NULL)",
        "INSERT INTO messages VALUES ('m2','c1','ASSISTANT','Flour and fat." +
            "\nShe said \"no\".',110,0,NULL)",
        "INSERT INTO messages VALUES ('m3','c1','ASSISTANT','Half an ans',120,1,NULL)",
        "INSERT INTO memory_entries VALUES ('e1','Prefers short answers.',10,20,'c1')",
        "INSERT INTO follow_ups VALUES ('f1','Check the oven temperature','CHAT'," +
            "'c1','m2',NULL,'might be 180C',0,500,NULL)",
        "INSERT INTO discover_drawn VALUES ('pack-history','moment-opera',800,1)",
        "INSERT INTO discover_saved (packId, momentId, title, topic, savedAt) " +
            "VALUES ('pack-history','moment-rome','Rome','History',850)",
        "INSERT INTO discover_quiz_stats VALUES ('pack-history',3,9,7)",
        "INSERT INTO artifacts VALUES ('gemma-4-e4b','LLM','Gemma 4 E4B'," +
            "'gemma.gguf',5000000000,'abc','1',900,1)",
        "INSERT INTO settings VALUES ('theme','dark')",
        "INSERT INTO settings VALUES ('onboarding.done','true')",
    )

    /** Writes a version 1 database to disk, then closes it. */
    private fun seedV1() {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                v1Tables.forEach(db::execSQL)
                seed.forEach(db::execSQL)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName).callback(callback).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    /**
     * Opens the seeded database through Room with every migration registered,
     * exactly as `KamDatabase.build` does. Room runs the chain and then validates
     * the result, which is the whole point.
     */
    private fun openThroughRoom(): KamDatabase =
        Room.databaseBuilder(context, KamDatabase::class.java, dbName)
            .addMigrations(
                KamDatabase.MIGRATION_1_2, KamDatabase.MIGRATION_2_3,
                KamDatabase.MIGRATION_3_4, KamDatabase.MIGRATION_4_5,
                KamDatabase.MIGRATION_5_6, KamDatabase.MIGRATION_6_7,
                KamDatabase.MIGRATION_7_8, KamDatabase.MIGRATION_8_9,
            )
            .build()
            .also { db = it }

    @Test
    fun roomMigratesVersionOneAllTheWayToCurrentAndAcceptsTheResult() {
        seedV1()
        val database = openThroughRoom()

        // Forcing a real query is what makes Room actually open, migrate and
        // validate. Building the instance alone does none of that.
        runBlocking {
            val conversations = database.conversations().allForBackup()
            assertThat(conversations).hasSize(3)
        }
    }

    @Test
    fun everyRowSurvivesTheWholeChain() {
        seedV1()
        val database = openThroughRoom()

        runBlocking {
            assertThat(database.conversations().allForBackup()).hasSize(3)
            assertThat(database.messages().allForBackup()).hasSize(3)
            assertThat(database.projects().allForBackup()).hasSize(1)
            assertThat(database.memory().allForBackup()).hasSize(1)

            // Two follow-ups now: the original, and the saved Discover moment
            // that the version 3 to 4 migration folds in when discover_saved is
            // dropped. Losing that one would be silent.
            assertThat(database.followUps().allForBackup()).hasSize(2)
        }
    }

    @Test
    fun contentIsNotRewrittenOnTheWayThrough() {
        seedV1()
        val database = openThroughRoom()

        runBlocking {
            val messages = database.messages().allForBackup().associateBy { it.id }
            assertThat(messages["m2"]!!.content).isEqualTo("Flour and fat.\nShe said \"no\".")
            assertThat(messages["m3"]!!.incomplete).isTrue()

            val conversations = database.conversations().allForBackup().associateBy { it.id }
            // CHAT became GENERAL, which is the rename the four-mode update made.
            assertThat(conversations["c1"]!!.mode).isEqualTo(Mode.GENERAL)
            assertThat(conversations["c1"]!!.title).isEqualTo("What is a roux?")
            assertThat(conversations["c1"]!!.pinned).isTrue()
            // An untitled conversation stays untitled rather than becoming "".
            assertThat(conversations["c2"]!!.title).isNull()
            assertThat(conversations["c3"]!!.archived).isTrue()

            // The mode a conversation was in seeds the set of modes it has used.
            assertThat(conversations["c1"]!!.modesUsed).isEqualTo("GENERAL")

            // The follow-up's source mode was renamed the same way.
            val followUps = database.followUps().allForBackup().associateBy { it.id }
            assertThat(followUps["f1"]!!.sourceMode).isEqualTo(Mode.GENERAL)
            assertThat(followUps["f1"]!!.note).isEqualTo("might be 180C")
        }
    }

    @Test
    fun theSyncColumnsArriveUnstampedRatherThanInvented() {
        seedV1()
        val database = openThroughRoom()

        runBlocking {
            // Everything written before sync existed must read as rev 0 with no
            // writer, so it loses to any real write rather than beating one.
            database.conversations().allForBackup().forEach {
                assertThat(it.rev).isEqualTo(0L)
                assertThat(it.lastWriterId).isEmpty()
            }
            // And no tombstone may be invented, or the first sync would delete
            // content that was never deleted.
            assertThat(database.tombstones().count()).isEqualTo(0)
        }
    }

    @Test
    fun openingAnAlreadyCurrentDatabaseChangesNothing() {
        seedV1()
        openThroughRoom().also { runBlocking { it.conversations().allForBackup() } }
        db?.close()
        db = null

        // Second open, already at the current version, so no migration runs. A
        // user opens the app far more often than they update it, and this is the
        // path that has to stay boring.
        val again = openThroughRoom()
        runBlocking {
            assertThat(again.conversations().allForBackup()).hasSize(3)
            assertThat(again.followUps().allForBackup()).hasSize(2)
        }
    }
}
