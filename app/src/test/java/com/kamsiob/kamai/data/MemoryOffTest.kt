package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.llm.MemoryMode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Off means not used, not merely not added to (#123).
 *
 * The mode gated storing a new fact and never gated reading the stored ones, so
 * turning memory off left everything already there being sent with every
 * message, while the Memory screen said "Nothing is remembered between
 * conversations."
 *
 * That is the kind of defect a unit test is for: it is invisible in the
 * interface, since the only symptom is a small line under a reply, and it is
 * about a promise made to somebody about their own data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoryOffTest {

    private lateinit var db: KamDatabase
    private lateinit var repository: KamRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, KamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KamRepository(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun store(text: String) {
        repository.remember(text, sourceConversationId = null, auto = false)
    }

    @Test
    fun memoriesAreUsedWhenTheModeAllowsIt() = runTest {
        repository.setMemoryMode(MemoryMode.MANUAL)
        store("I always work in metric units")
        assertThat(repository.relevantMemory("metric", 4000, 8)).isNotEmpty()
    }

    @Test
    fun nothingIsRetrievedWhenMemoryIsOff() = runTest {
        repository.setMemoryMode(MemoryMode.MANUAL)
        store("I always work in metric units")

        repository.setMemoryMode(MemoryMode.OFF)

        assertThat(repository.relevantMemory("metric", 4000, 8)).isEmpty()
    }

    @Test
    fun turningMemoryOffDoesNotDeleteAnything() = runTest {
        // Off is not a request to forget. The Memory screen still lists
        // everything with a Forget button beside each, which is where deleting
        // belongs, and somebody who turns this off and back on should find their
        // facts where they left them.
        repository.setMemoryMode(MemoryMode.MANUAL)
        store("I always work in metric units")

        repository.setMemoryMode(MemoryMode.OFF)
        assertThat(repository.allMemoryTexts()).hasSize(1)

        repository.setMemoryMode(MemoryMode.MANUAL)
        assertThat(repository.relevantMemory("metric", 4000, 8)).isNotEmpty()
    }
}
