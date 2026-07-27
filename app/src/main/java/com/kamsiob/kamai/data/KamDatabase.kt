package com.kamsiob.kamai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun modeToString(mode: Mode): String = mode.name
    @TypeConverter fun stringToMode(value: String): Mode =
        // "CHAT" is the pre-four-modes name for GENERAL. The migration rewrites
        // stored rows, but map it here too so any stray value stays safe.
        if (value == "CHAT") Mode.GENERAL else Mode.valueOf(value)

    @TypeConverter fun followUpKindToString(kind: FollowUpKind): String = kind.name
    @TypeConverter fun stringToFollowUpKind(value: String): FollowUpKind = FollowUpKind.valueOf(value)

    @TypeConverter fun roleToString(role: Role): String = role.name
    @TypeConverter fun stringToRole(value: String): Role = Role.valueOf(value)

    @TypeConverter fun kindToString(kind: ArtifactKind): String = kind.name
    @TypeConverter fun stringToKind(value: String): ArtifactKind = ArtifactKind.valueOf(value)
}

/**
 * The one database. Everything the app remembers lives here so that a backup is
 * a single file and a restore is a single import.
 *
 * There is no destructive migration fallback anywhere in this class. Losing a
 * person's conversations because a schema moved would be unforgivable in an app
 * whose whole promise is that their data stays with them, so every version bump
 * gets a real migration.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProjectEntity::class,
        MemoryEntity::class,
        FollowUpEntity::class,
        DrawnMomentEntity::class,
        QuizStatsEntity::class,
        ArtifactEntity::class,
        SettingEntity::class,
        TombstoneEntity::class,
    ],
    version = KamDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KamDatabase : RoomDatabase() {

    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun projects(): ProjectDao
    abstract fun memory(): MemoryDao
    abstract fun followUps(): FollowUpDao
    abstract fun discover(): DiscoverDao
    abstract fun artifacts(): ArtifactDao
    abstract fun settings(): SettingsDao
    abstract fun tombstones(): TombstoneDao

    companion object {
        const val NAME = "kam-ai.db"

        /**
         * The Room schema version, in one place.
         *
         * It is here rather than only in the @Database annotation because the
         * backup file records it, and it was recording the wrong thing: the
         * export passed BackupCodec.FORMAT_VERSION, so every backup claimed
         * schema 3 while holding schema 9 data. Nothing reads that field on
         * import today, which is the only reason it did no harm, and a future
         * importer keying on it would have made exactly the wrong decision.
         */
        const val VERSION = 9

        /** Adds the manual-title flag. A real migration, never a destructive
         *  fallback: losing conversations to a schema bump is unacceptable. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE conversations ADD COLUMN titleIsManual INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Adds the auto-saved flag to memory entries. PART 7. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE memory_entries ADD COLUMN auto INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Unifies saving. There is now one saving action and one destination: the
         * bookmark means the same thing everywhere and everything saved lands in
         * the single follow-ups list, told apart by its source. A saved Discover
         * moment carries packId and momentId so it can still be reopened. This
         * migration adds those columns, moves every existing saved moment into
         * follow_ups so nothing is lost, then removes the separate table.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN packId TEXT")
                db.execSQL("ALTER TABLE follow_ups ADD COLUMN momentId TEXT")
                // Move saved moments into follow-ups. The moment title becomes the
                // snippet, the source is DISCOVER, and savedAt becomes createdAt.
                // hex(randomblob(16)) gives each a stable id in the same shape the
                // app writes elsewhere; it does not need to be a UUID.
                db.execSQL(
                    """
                    INSERT INTO follow_ups
                        (id, snippet, sourceMode, conversationId, messageId, projectId,
                         note, packId, momentId, completed, createdAt, completedAt)
                    SELECT
                        lower(hex(randomblob(16))), title, 'DISCOVER', NULL, NULL, NULL,
                        NULL, packId, momentId, 0, savedAt, NULL
                    FROM discover_saved
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE discover_saved")
            }
        }

        /**
         * The four-mode update. Chat becomes General, Brainstorm is added, and a
         * conversation now records every mode it has used (modesUsed), which the
         * chat-row dots and the mode filter read. Follow-ups gain a kind (check or
         * pursue). A real migration: existing conversations keep their mode as
         * their only recorded mode, Chat rows become General everywhere, and
         * existing follow-ups default to check.
         */
        /**
         * The statements MIGRATION_4_5 runs, in order, exposed so they can be
         * driven over a plain SQLite database by a pure JVM test.
         *
         * This build machine cannot run either kind of Android-backed test: its
         * only JDK is 26, which Robolectric 4.16.1 cannot instrument against, and
         * the emulator's own qemu process segfaults on this kernel. Instrumented
         * tests need a device, and the phone holds the owner's real data. Keeping
         * the SQL in one list means the migration that ships and the migration
         * that is tested cannot drift apart. See MigrationSqlTest.
         */
        val MIGRATION_4_5_SQL = listOf(
            // Chat -> General, in both places a mode name is stored.
            "UPDATE conversations SET mode = 'GENERAL' WHERE mode = 'CHAT'",
            "UPDATE follow_ups SET sourceMode = 'GENERAL' WHERE sourceMode = 'CHAT'",
            // Record every mode used. Existing rows have used exactly their
            // current mode, so seed the set from it (already General for the
            // former Chat rows above).
            "ALTER TABLE conversations ADD COLUMN modesUsed TEXT NOT NULL DEFAULT 'GENERAL'",
            "UPDATE conversations SET modesUsed = mode",
            // Follow-up kind, defaulting existing items to check.
            "ALTER TABLE follow_ups ADD COLUMN kind TEXT NOT NULL DEFAULT 'CHECK'",
        )

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        /**
         * The statements MIGRATION_5_6 runs, exposed for the same reason as
         * MIGRATION_4_5_SQL: so a pure JVM test can drive the exact statements
         * that ship rather than a copy of them. See MigrationSqlTest.
         *
         * One column. A Workbench session and the conversation discussing it point
         * at each other, so the link can be followed from either side (issue #32).
         * Nullable, because most conversations have no link and a link is a fact
         * about a pair rather than a property every row must carry.
         *
         * Adding a nullable column is the least destructive change available: no
         * existing row is read, rewritten, or moved, and every existing
         * conversation is simply unlinked, which is what it was.
         */
        val MIGRATION_5_6_SQL = listOf(
            "ALTER TABLE conversations ADD COLUMN linkedConversationId TEXT DEFAULT NULL",
        )

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach(db::execSQL)
            }
        }

        /**
         * The statements MIGRATION_6_7 runs, exposed for the same reason as the
         * two above. See MigrationSqlTest and MigrationV6ToV7SqlTest.
         *
         * One column: project notes, the background a project carries as
         * distinct from the instructions it gives (#2).
         *
         * NOT NULL with an empty default rather than nullable, because "no
         * notes" and "empty notes" are the same thing here and a nullable column
         * would make every reader decide which it was. The default is what makes
         * that safe: every existing project gets an empty string without a row
         * being read or rewritten.
         */
        val MIGRATION_6_7_SQL = listOf(
            "ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''",
        )

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_SQL.forEach(db::execSQL)
            }
        }

        /**
         * The statements MIGRATION_7_8 runs. See MigrationV7ToV8SqlTest.
         *
         * One column: how many remembered facts were put in front of the model
         * for a given answer, so the answer can say that it used them (#16).
         *
         * Zero is the honest default for everything already written. Those
         * answers may well have used memory; nothing recorded it at the time,
         * and inventing a number for them would be worse than saying nothing.
         */
        val MIGRATION_7_8_SQL = listOf(
            "ALTER TABLE messages ADD COLUMN memoriesUsed INTEGER NOT NULL DEFAULT 0",
        )

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_SQL.forEach(db::execSQL)
            }
        }

        /**
         * The statements MIGRATION_8_9 runs. See MigrationV8ToV9SqlTest.
         *
         * Makes the schema sync-ready without building any sync. See Sync.kt for
         * the reasoning behind each piece; what matters here is the migration.
         *
         * Two things, and both are additive:
         *
         * - A `rev` and `lastWriterId` on every table whose contents belong to the
         *   user, which together order two versions of a row.
         * - A `tombstones` table, so a deletion leaves a mark. Without one, a row
         *   deleted here and a row this device has not yet heard of are the same
         *   state, and the deleted one comes back from the other phone.
         *
         * `rev = 0` and `lastWriterId = ''` for everything already written, which
         * reads as "before this device knew about sync" and sorts below any real
         * write. That is the correct outcome: a row nobody has touched since sync
         * existed should lose to one somebody has.
         *
         * Nothing is dropped, nothing is rewritten, and no table is recreated, so
         * there is no window in which somebody's conversations exist only in a
         * temporary table.
         */
        val MIGRATION_8_9_SQL = listOf(
            "ALTER TABLE projects ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE projects ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE conversations ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE conversations ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE messages ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE messages ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE memory_entries ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE memory_entries ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE follow_ups ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE follow_ups ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE discover_drawn ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE discover_drawn ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE settings ADD COLUMN rev INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE settings ADD COLUMN lastWriterId TEXT NOT NULL DEFAULT ''",
            """
            CREATE TABLE IF NOT EXISTS tombstones (
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                rev INTEGER NOT NULL,
                deviceId TEXT NOT NULL,
                deletedAt INTEGER NOT NULL,
                PRIMARY KEY(entityType, entityId)
            )
            """.trimIndent(),
            // Indexed on rev because the only question ever asked of this table is
            // "what has been deleted since I last spoke to you", and a table that
            // is only ever scanned by that needs to answer it without a full scan.
            "CREATE INDEX IF NOT EXISTS index_tombstones_rev ON tombstones (rev)",
        )

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_9_SQL.forEach(db::execSQL)
            }
        }

        @Volatile
        private var instance: KamDatabase? = null

        fun get(context: Context): KamDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /** Closes and forgets the open database, so the next get() reopens it.
         *  Used by the forgot-code wipe before the files are deleted, and by the
         *  repository when a lock change means the key must be re-derived. */
        @Synchronized
        fun closeAndForget() {
            runCatching { instance?.close() }
            instance = null
        }

        private fun build(context: Context): KamDatabase {
            // PART 3. The database is encrypted at rest with SQLCipher, keyed
            // from the Android Keystore. On the first launch after this shipped,
            // any existing plaintext database is migrated across first, safely
            // and restartably. See DatabaseEncryption and DatabaseKey.
            val dbFile = context.getDatabasePath(NAME)
            // In separate-passphrase lock mode the key file carries a passphrase
            // layer, so the passphrase the user just entered (held in memory for
            // this unlocked session) is needed to unwrap it. In every other mode
            // this is null and the Keystore layer is enough. The app is gated so
            // this is only ever reached once the lock, if any, is satisfied.
            val secret = com.kamsiob.kamai.lock.AppLock.sessionSecret
            val passphrase = DatabaseKey.getOrCreate(context, secret)
            val factory = DatabaseEncryption.openHelperFactory(context, dbFile, passphrase)
            return Room.databaseBuilder(context, KamDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9,
                )
                .build()
        }
    }
}
