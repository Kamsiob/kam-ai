package com.kamsiob.kamai.discover

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Reads moments out of a downloaded pack file. Pack files are plain SQLite
 * databases of public Wikipedia content, so they are opened read-only and are not
 * encrypted (there is nothing private in them; the user's own state lives in the
 * encrypted app database).
 *
 * Opened per query and closed again rather than held: a pack read is infrequent
 * (dealing a card, opening the reader) and keeping handles open across a pack
 * delete would strand file locks.
 */
object PackReader {

    private fun open(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun momentCount(file: File): Int {
        if (!file.exists()) return 0
        return open(file).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM moments", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        }
    }

    /** All moment ids in a pack, for computing what is still unseen. */
    fun allIds(file: File): List<String> {
        if (!file.exists()) return emptyList()
        return open(file).use { db ->
            db.rawQuery("SELECT id FROM moments", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }
    }

    fun byId(packId: String, file: File, id: String): Moment? {
        if (!file.exists()) return null
        return open(file).use { db ->
            db.rawQuery(
                "SELECT id,title,topic,preview,passage,source_title,source_url,license FROM moments WHERE id = ?",
                arrayOf(id),
            ).use { c -> if (c.moveToFirst()) row(packId, c) else null }
        }
    }

    /**
     * Deals a moment whose id is not in [excludeIds]. Returns null when every
     * moment has been seen, which the caller turns into the plain reshuffle offer.
     * Uses SQL random ordering so the draw is uniform without loading the pack.
     */
    fun dealUnseen(packId: String, file: File, excludeIds: Set<String>): Moment? {
        if (!file.exists()) return null
        return open(file).use { db ->
            // Pull a small random sample and pick the first unseen, so a large
            // exclude set does not need a giant NOT IN clause.
            db.rawQuery(
                "SELECT id,title,topic,preview,passage,source_title,source_url,license " +
                    "FROM moments ORDER BY RANDOM() LIMIT 200",
                null,
            ).use { c ->
                while (c.moveToNext()) {
                    if (c.getString(0) !in excludeIds) return@use row(packId, c)
                }
                null
            }
        }
    }

    private fun row(packId: String, c: android.database.Cursor) = Moment(
        packId = packId,
        id = c.getString(0),
        title = c.getString(1),
        topic = c.getString(2),
        preview = c.getString(3),
        passage = c.getString(4),
        sourceTitle = c.getString(5),
        sourceUrl = c.getString(6),
        license = c.getString(7),
    )
}
