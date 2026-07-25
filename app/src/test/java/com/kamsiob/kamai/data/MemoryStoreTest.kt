package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * What actually happens in the database when a fact is remembered (#16).
 *
 * `MemorySupersessionTest` proves the decision over lists of strings.
 * This proves the part that touches the user's data: that the superseded row is
 * really gone, that the survivor is really there, and that a retraction leaves
 * nothing behind. Deleting the wrong row is the failure that matters, and it is
 * not visible from a pure function.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryStoreTest {

    private lateinit var db: KamDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KamDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun store(text: String, auto: Boolean = false) {
        val now = System.currentTimeMillis()
        db.memory().upsert(
            MemoryEntity(
                id = UUID.randomUUID().toString(), text = text,
                createdAt = now, updatedAt = now,
                sourceConversationId = null, auto = auto,
            ),
        )
    }

    private suspend fun texts(): List<String> = db.memory().mostRecent(500).map { it.text }

    /**
     * The repository's own logic, run against this database. Kept here rather
     * than constructing a `KamRepository`, which wants an encrypted database and
     * a context it cannot have in a unit test.
     */
    private suspend fun remember(text: String): KamRepository.Remembered {
        val target = com.kamsiob.kamai.llm.MemoryExtractor.normalise(text)
        if (target.isBlank()) return KamRepository.Remembered.NOTHING
        val existing = db.memory().mostRecent(500)
        if (existing.any { com.kamsiob.kamai.llm.MemoryExtractor.normalise(it.text) == target }) {
            return KamRepository.Remembered.NOTHING
        }
        val verdict = com.kamsiob.kamai.llm.MemorySupersession.verdict(text, existing.map { it.text })
        val superseded = when (verdict) {
            is com.kamsiob.kamai.llm.MemorySupersession.Verdict.Store -> verdict.replaces
            is com.kamsiob.kamai.llm.MemorySupersession.Verdict.RetractOnly -> verdict.removes
        }
        existing.filter { it.text in superseded }.forEach { db.memory().delete(it) }
        if (verdict is com.kamsiob.kamai.llm.MemorySupersession.Verdict.RetractOnly) {
            return KamRepository.Remembered(stored = false, removed = superseded)
        }
        store(text)
        return KamRepository.Remembered(stored = true, removed = superseded)
    }

    @Test
    fun `moving house leaves one address in the database, the new one`() = runTest {
        store("lives in Leeds")
        store("is learning Spanish")

        val result = remember("lives in Manchester")

        assertThat(result.stored).isTrue()
        assertThat(result.removed).containsExactly("lives in Leeds")
        assertThat(texts()).containsExactly("lives in Manchester", "is learning Spanish")
    }

    @Test
    fun `a retraction removes its fact and adds nothing`() = runTest {
        store("is learning Spanish")
        store("lives in Leeds")

        val result = remember("no longer learning Spanish")

        assertThat(result.stored).isFalse()
        assertThat(result.removed).containsExactly("is learning Spanish")
        // Not "is learning Spanish" struck through, and not a second row saying
        // it is over. Gone, and the unrelated fact untouched.
        assertThat(texts()).containsExactly("lives in Leeds")
    }

    @Test
    fun `an ordinary fact is added without disturbing anything`() = runTest {
        store("lives in Leeds")
        store("prefers plain language")

        val result = remember("is allergic to shellfish")

        assertThat(result.stored).isTrue()
        assertThat(result.removed).isEmpty()
        assertThat(texts()).containsExactly(
            "lives in Leeds", "prefers plain language", "is allergic to shellfish",
        )
    }

    @Test
    fun `remembering the same thing twice stores one row and says nothing changed`() = runTest {
        store("lives in Leeds")

        val result = remember("Lives in Leeds.")

        assertThat(result.stored).isFalse()
        assertThat(result.removed).isEmpty()
        assertThat(texts()).containsExactly("lives in Leeds")
    }

    @Test
    fun `a second preference joins the first rather than replacing it`() = runTest {
        store("prefers plain language")

        remember("prefers short answers")

        assertThat(texts()).containsExactly("prefers plain language", "prefers short answers")
    }

    @Test
    fun `a retraction that matches nothing is kept, so the information is not lost`() = runTest {
        store("lives in Leeds")

        val result = remember("no longer eats dairy")

        assertThat(result.stored).isTrue()
        assertThat(texts()).containsExactly("lives in Leeds", "no longer eats dairy")
    }
}
