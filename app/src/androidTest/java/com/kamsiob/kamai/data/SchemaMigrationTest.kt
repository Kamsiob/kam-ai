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
 * The schema migration that adds the manual-title flag. PART 4.
 *
 * A schema bump must never lose a conversation, so this builds a version 1
 * conversations table with a real row, runs the actual MIGRATION_1_2 object
 * against it, and proves the row is still there with the new column defaulted.
 *
 * This drives the real migration directly rather than through Room's
 * MigrationTestHelper, whose schema-json validator pulls in a conflicting
 * kotlinx-serialization runtime on this toolchain. The migration SQL under test
 * is identical either way.
 */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration-schema-test.db"

    private lateinit var helper: SupportSQLiteOpenHelper

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(dbName)
    }

    private fun openV1Then(migrate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // The version 1 conversations table, without titleIsManual.
                db.execSQL(
                    "CREATE TABLE conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT, " +
                        "mode TEXT NOT NULL, projectId TEXT, createdAt INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, pinned INTEGER NOT NULL, " +
                        "archived INTEGER NOT NULL, groundingMomentId TEXT)",
                )
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
        migrate(db)
        return db
    }

    @Test
    fun version1DataSurvivesTheMoveToVersion2() {
        val db = openV1Then { database ->
            database.execSQL(
                "INSERT INTO conversations (id, title, mode, createdAt, updatedAt, pinned, archived) " +
                    "VALUES ('c1', 'An old chat', 'CHAT', 1, 1, 0, 0)",
            )
            // Run the real migration under test.
            KamDatabase.MIGRATION_1_2.migrate(database)
        }

        db.query("SELECT title, titleIsManual FROM conversations WHERE id = 'c1'").use { c ->
            assertThat(c.moveToFirst()).isTrue()
            assertThat(c.getString(0)).isEqualTo("An old chat")
            // The new column defaults to 0: an existing title is treated as an
            // auto title until the user renames it.
            assertThat(c.getInt(1)).isEqualTo(0)
        }
    }

    @Test
    fun memoryEntriesSurviveTheMoveToVersion3() {
        // A version 2 memory_entries table, without the auto flag.
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE memory_entries (id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, sourceConversationId TEXT)",
                )
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
        }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(callback).build(),
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO memory_entries (id, text, createdAt, updatedAt) VALUES ('m1', 'likes tea', 1, 1)")
        KamDatabase.MIGRATION_2_3.migrate(db)

        db.query("SELECT text, auto FROM memory_entries WHERE id = 'm1'").use { c ->
            c.moveToFirst()
            assertThat(c.getString(0)).isEqualTo("likes tea")
            // Existing memories default to manual (auto = 0).
            assertThat(c.getInt(1)).isEqualTo(0)
        }
    }

    @Test
    fun theNewColumnAcceptsAManualTitleFlag() {
        val db = openV1Then { KamDatabase.MIGRATION_1_2.migrate(it) }
        db.execSQL(
            "INSERT INTO conversations (id, title, mode, createdAt, updatedAt, pinned, archived, titleIsManual) " +
                "VALUES ('c2', 'My name', 'CHAT', 1, 1, 0, 0, 1)",
        )
        db.query("SELECT titleIsManual FROM conversations WHERE id = 'c2'").use { c ->
            c.moveToFirst()
            assertThat(c.getInt(0)).isEqualTo(1)
        }
    }
}
