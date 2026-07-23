package com.kamsiob.kamai.discover

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Deal-draw uniqueness over a small fixture pack: every moment is dealt exactly
 * once before the pack reports exhaustion, and a dealt id is never repeated. This
 * is the core of "only unseen cards are dealt" and the plain reshuffle at the end.
 */
@RunWith(RobolectricTestRunner::class)
class PackDealTest {

    private fun fixture(n: Int): File {
        val file = File.createTempFile("fixture", ".kampack").also { it.deleteOnExit() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE moments(id TEXT PRIMARY KEY, title TEXT, topic TEXT, preview TEXT, " +
                    "passage TEXT, source_title TEXT, source_url TEXT, license TEXT, pack_version INTEGER)",
            )
            for (i in 1..n) {
                db.execSQL(
                    "INSERT INTO moments VALUES(?,?,?,?,?,?,?,?,1)",
                    arrayOf("m$i", "Title $i", "Test", "preview $i", "passage $i", "Title $i", "url$i", "CC BY-SA 4.0"),
                )
            }
        }
        return file
    }

    @Test
    fun dealsEveryMomentOnceThenExhausts() {
        val file = fixture(12)
        val seen = mutableSetOf<String>()
        repeat(12) {
            val m = PackReader.dealUnseen("p", file, seen)
            assertTrue("should keep dealing while unseen remain", m != null)
            assertTrue("a dealt id must be new", seen.add(m!!.id))
        }
        // Every id dealt exactly once.
        assertEquals(PackReader.allIds(file).toSet(), seen)
        // Now exhausted.
        assertNull("no more unseen moments", PackReader.dealUnseen("p", file, seen))
    }

    @Test
    fun byIdRoundTrips() {
        val file = fixture(3)
        val m = PackReader.byId("p", file, "m2")
        assertEquals("Title 2", m?.title)
        assertEquals("passage 2", m?.passage)
        assertEquals("p", m?.packId)
    }
}
