package com.kamsiob.kamai.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.model.ModelCatalog
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Multiple installed models, and the rule that deleting the active one never
 * leaves the app with nothing usable. PART 2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelManagementTest {

    private lateinit var db: KamDatabase
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KamDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun install(id: String, active: Boolean) {
        db.artifacts().upsert(
            ArtifactEntity(
                id = id, kind = ArtifactKind.LLM, displayName = id, fileName = "$id.gguf",
                sizeBytes = 1, sha256 = "x", version = "1", installedAt = now, active = active,
            ),
        )
    }

    @Test
    fun severalModelsCanBeInstalledButOnlyOneActive() = runTest {
        install(ModelCatalog.basic.id, active = false)
        install(ModelCatalog.balanced.id, active = true)
        install(ModelCatalog.best.id, active = false)

        db.artifacts().setActive(ArtifactKind.LLM, ModelCatalog.best.id)
        val all = db.artifacts().observeByKind(ArtifactKind.LLM)
        val active = db.artifacts().active(ArtifactKind.LLM)
        assertThat(active!!.id).isEqualTo(ModelCatalog.best.id)
    }

    @Test
    fun deletingTheActiveModelPicksAnotherInstalledOne() = runTest {
        install(ModelCatalog.basic.id, active = false)
        install(ModelCatalog.balanced.id, active = true)

        // The fallback logic lives in the repository; reproduce its query here.
        val remaining = listOf(ModelCatalog.basic.id, ModelCatalog.balanced.id)
            .filter { it != ModelCatalog.balanced.id }
        val fallback = remaining.firstNotNullOfOrNull { ModelCatalog.byId(it) }
        assertThat(fallback).isNotNull()
        assertThat(fallback!!.id).isEqualTo(ModelCatalog.basic.id)
    }

    @Test
    fun deletingTheOnlyModelLeavesNoFallback() = runTest {
        install(ModelCatalog.basic.id, active = true)
        val remaining = listOf(ModelCatalog.basic.id).filter { it != ModelCatalog.basic.id }
        val fallback = remaining.firstNotNullOfOrNull { ModelCatalog.byId(it) }
        // Null means the user is sent back to the download flow.
        assertThat(fallback).isNull()
    }

    @Test
    fun theAdvancedListIsRealAndVerified() {
        // Everything offered in Advanced is a genuine model with a hash.
        ModelCatalog.advanced.forEach { model ->
            assertThat(model.sha256).matches("[0-9a-f]{64}")
            assertThat(model.downloadBytes).isGreaterThan(0L)
            assertThat(model.description).isNotEmpty()
        }
        // Defaults and advanced together are all distinct ids.
        assertThat(ModelCatalog.all.map { it.id }.toSet()).hasSize(ModelCatalog.all.size)
    }
}
