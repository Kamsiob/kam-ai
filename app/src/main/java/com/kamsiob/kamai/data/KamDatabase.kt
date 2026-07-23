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
    @TypeConverter fun stringToMode(value: String): Mode = Mode.valueOf(value)

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
        SavedMomentEntity::class,
        QuizStatsEntity::class,
        ArtifactEntity::class,
        SettingEntity::class,
    ],
    version = 3,
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

    companion object {
        const val NAME = "kam-ai.db"

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
