package com.kamsiob.kamai.llm

import com.google.common.truth.Truth.assertThat
import com.kamsiob.kamai.model.ModelCatalog
import com.kamsiob.kamai.model.TierModel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * The model manager's decision surface, tested entirely with fakes so every rule
 * is pinned without a device or a real model. These are the guarantees the rest
 * of the app relies on.
 */
class ModelManagerTest {

    /** Records the order of load and unload so "never two resident" is checkable. */
    private class FakeRuntime : ModelRuntime {
        val events = mutableListOf<String>()
        var loaded: String? = null
        var failNextLoad = false
        var contextReleased = false

        override val isLoaded: Boolean get() = loaded != null

        override suspend fun load(model: TierModel, file: File): Result<Unit> {
            // The invariant: nothing may be resident at the moment a load begins.
            check(loaded == null) { "load started while ${loaded} was still resident" }
            events.add("load ${model.id}")
            if (failNextLoad) {
                failNextLoad = false
                return Result.failure(IllegalStateException("boom"))
            }
            loaded = model.id
            return Result.success(Unit)
        }

        override suspend fun unload() {
            if (loaded != null) events.add("unload $loaded")
            loaded = null
            contextReleased = false
        }

        override suspend fun releaseContext() {
            if (loaded != null) { events.add("releaseContext $loaded"); contextReleased = true }
        }
    }

    private class FakeGauge(
        var available: Long,
        var low: Boolean = false,
        var total: Long = 16L * 1024 * 1024 * 1024,
    ) : MemoryGauge {
        override fun availableBytes(): Long = available
        override fun totalBytes(): Long = total
        override fun lowMemory(): Boolean = low
    }

    private val basic = ModelCatalog.basic       // ~3.1 GB
    private val best = ModelCatalog.best          // ~7.1 GB
    private val plenty = 32L * 1024 * 1024 * 1024 // 32 GB free: everything fits

    // Real temp files so fileFor(model).exists() is honest in tests.
    private inline fun withTempFiles(ids: List<String>, block: (Map<String, File>) -> Unit) {
        val files = ids.associateWith { File.createTempFile("kamai-$it", ".gguf").apply { writeText("x") } }
        try {
            block(files)
        } finally {
            files.values.forEach { it.delete() }
        }
    }

    private fun mgr(
        runtime: FakeRuntime,
        gauge: FakeGauge,
        scope: kotlinx.coroutines.CoroutineScope,
        installed: MutableList<TierModel>,
        active: Array<String?>,
        files: Map<String, File>,
    ) = ModelManager(
        runtime = runtime,
        gauge = gauge,
        scope = scope,
        fileFor = { files[it.id] ?: File("/absent/${it.id}") },
        activeModel = { active[0]?.let { id -> installed.firstOrNull { it.id == id } } },
        setActive = { active[0] = it },
        installedModels = { installed },
    )

    @Test
    fun switchingNeverLeavesTwoResident() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id, best.id)) { files ->
            val installed = mutableListOf(basic, best)
            val active = arrayOf<String?>(basic.id)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files)

            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(basic.id)

            m.switchTo(best)
            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(best.id)

            // The recorded order shows the old model was unloaded before the new
            // one loaded, and load's own check would have thrown otherwise.
            assertThat(rt.events).containsExactly(
                "load ${basic.id}", "unload ${basic.id}", "load ${best.id}",
            ).inOrder()
        }
    }

    @Test
    fun nothingLoadsAtStartup() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val m = mgr(rt, FakeGauge(plenty), this, mutableListOf(basic), arrayOf(basic.id), files)
            // refreshActive is what startup calls. It must never load.
            m.refreshActive()
            assertThat(rt.events).isEmpty()
            assertThat(rt.isLoaded).isFalse()
            assertThat(m.status.value).isInstanceOf(ModelManager.Status.Idle::class.java)
        }
    }

    @Test
    fun deletingTheLoadedModelUnloadsItFirstAndFallsBack() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id, best.id)) { files ->
            val installed = mutableListOf(basic, best)
            val active = arrayOf<String?>(best.id)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files)

            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(best.id)

            m.delete(best, midDownload = false, cancelDownload = {}, removeArtifact = {
                installed.remove(best)
            })

            // Unloaded before removal, and fell back to the other installed model.
            assertThat(rt.isLoaded).isFalse()
            assertThat(rt.events.last()).isEqualTo("unload ${best.id}")
            assertThat(active[0]).isEqualTo(basic.id)
            assertThat(m.status.value).isEqualTo(ModelManager.Status.Idle(basic))
        }
    }

    @Test
    fun deletingTheOnlyModelLeavesAUsableNoModelState() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val installed = mutableListOf(basic)
            val active = arrayOf<String?>(basic.id)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files)
            m.ensureLoaded()

            m.delete(basic, midDownload = false, cancelDownload = {}, removeArtifact = {
                installed.remove(basic)
            })

            assertThat(rt.isLoaded).isFalse()
            assertThat(active[0]).isNull()
            assertThat(m.status.value).isEqualTo(ModelManager.Status.NoModel)
        }
    }

    @Test
    fun deletingAMidDownloadModelCancelsAndCleansUp() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val installed = mutableListOf(basic)
            var cancelled = false
            var removed = false
            val m = mgr(rt, FakeGauge(plenty), this, installed, arrayOf<String?>(null), files)

            m.delete(best, midDownload = true, cancelDownload = { cancelled = true }, removeArtifact = { removed = true })

            assertThat(cancelled).isTrue()
            assertThat(removed).isTrue()
            assertThat(rt.isLoaded).isFalse()
        }
    }

    @Test
    fun aLoadThatWouldExceedMemoryIsRefusedWithASmallerOption() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id, best.id)) { files ->
            val installed = mutableListOf(basic, best)
            val active = arrayOf<String?>(best.id)
            // Free memory that covers Basic's anonymous buffers but not Best's:
            // enough for Basic's KV+compute (~1.6 GB with margin) and short of
            // Best's (~2.1 GB). The mmapped weights are not part of this figure.
            val gauge = FakeGauge(available = 1_900L * 1024 * 1024)
            val m = mgr(rt, gauge, this, installed, active, files)

            val status = m.ensureLoaded()

            assertThat(status).isInstanceOf(ModelManager.Status.Refused::class.java)
            val refused = status as ModelManager.Status.Refused
            assertThat(refused.model).isEqualTo(best)
            assertThat(refused.smaller).isEqualTo(basic)
            assertThat(refused.reason).contains("more free memory")
            // And crucially, nothing was loaded.
            assertThat(rt.isLoaded).isFalse()
        }
    }

    @Test
    fun aModelTooBigForTheDeviceIsRefusedAsTooLarge() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(best.id)) { files ->
            // Plenty free right now, but the phone's total RAM cannot hold the
            // whole working set (a small-RAM device with a large model).
            val gauge = FakeGauge(
                available = 6L * 1024 * 1024 * 1024,
                total = 6L * 1024 * 1024 * 1024,
            )
            val m = mgr(rt, gauge, this, mutableListOf(best), arrayOf<String?>(best.id), files)
            val status = m.ensureLoaded() as ModelManager.Status.Refused
            assertThat(status.reason).contains("too large to run on this phone")
            assertThat(rt.isLoaded).isFalse()
        }
    }

    @Test
    fun aRefusalWithNoSmallerModelStillExplainsPlainly() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(best.id)) { files ->
            val installed = mutableListOf(best)
            val gauge = FakeGauge(available = 1L * 1024 * 1024 * 1024)
            val m = mgr(rt, gauge, this, installed, arrayOf<String?>(best.id), files)

            val status = m.ensureLoaded() as ModelManager.Status.Refused
            assertThat(status.smaller).isNull()
            assertThat(status.reason).contains("download a smaller model")
        }
    }

    @Test
    fun memoryPressureUnloadsAndNextUseReloadsTransparently() = runTest {
        val rt = FakeRuntime()
        val dispatcher = StandardTestDispatcher(testScheduler)
        withTempFiles(listOf(basic.id)) { files ->
            val m = mgr(rt, FakeGauge(plenty), this, mutableListOf(basic), arrayOf<String?>(basic.id), files)
            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(basic.id)

            m.onSeverePressure()
            advanceUntilIdle()
            assertThat(rt.isLoaded).isFalse()
            assertThat(m.status.value).isEqualTo(ModelManager.Status.Idle(basic))

            // Next use reloads with no special handling by the caller.
            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(basic.id)
            assertThat(rt.events).containsExactly(
                "load ${basic.id}", "unload ${basic.id}", "load ${basic.id}",
            ).inOrder()
        }
    }

    @Test
    fun installingAModelDoesNotLoadItOrDisturbTheActiveOne() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id, best.id)) { files ->
            val installed = mutableListOf(basic, best)
            val active = arrayOf<String?>(basic.id)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files)
            m.ensureLoaded()

            // A second model finishes downloading; the running one is untouched.
            m.onModelInstalled(best)
            assertThat(active[0]).isEqualTo(basic.id)
            assertThat(rt.loaded).isEqualTo(basic.id)
        }
    }

    @Test
    fun theFirstInstalledModelBecomesActiveWithoutLoading() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val installed = mutableListOf(basic)
            val active = arrayOf<String?>(null)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files)

            m.onModelInstalled(basic)
            assertThat(active[0]).isEqualTo(basic.id)
            assertThat(rt.isLoaded).isFalse()
            assertThat(m.status.value).isEqualTo(ModelManager.Status.Idle(basic))
        }
    }

    @Test
    fun aDanglingActiveReferenceIsRepairedAtStartupNotLoaded() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            // Active points at Best, whose file does not exist; Basic is installed.
            val installed = mutableListOf(basic, best)
            val active = arrayOf<String?>(best.id)
            val m = mgr(rt, FakeGauge(plenty), this, installed, active, files) // no best file

            m.refreshActive()
            assertThat(rt.events).isEmpty()          // never loaded
            assertThat(active[0]).isEqualTo(basic.id) // repaired to an installed one
        }
    }

    @Test
    fun aFailedLoadReportsPlainlyAndLeavesNothingResident() = runTest {
        val rt = FakeRuntime().apply { failNextLoad = true }
        withTempFiles(listOf(basic.id)) { files ->
            val m = mgr(rt, FakeGauge(plenty), this, mutableListOf(basic), arrayOf<String?>(basic.id), files)
            val status = m.ensureLoaded()
            assertThat(status).isInstanceOf(ModelManager.Status.Failed::class.java)
            assertThat(rt.isLoaded).isFalse()
        }
    }

    @Test
    fun moderatePressureReleasesTheKvCacheButKeepsTheModelResident() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val m = mgr(rt, FakeGauge(plenty), this, mutableListOf(basic), arrayOf<String?>(basic.id), files)
            m.ensureLoaded()
            assertThat(rt.loaded).isEqualTo(basic.id)

            m.onModeratePressure()
            advanceUntilIdle()

            // The model is still resident; only the KV cache was released.
            assertThat(rt.isLoaded).isTrue()
            assertThat(rt.contextReleased).isTrue()
            assertThat(m.status.value).isEqualTo(ModelManager.Status.Loaded(basic))
            assertThat(rt.events.last()).isEqualTo("releaseContext ${basic.id}")
        }
    }

    @Test
    fun severePressureUnloadsTheWholeModel() = runTest {
        val rt = FakeRuntime()
        withTempFiles(listOf(basic.id)) { files ->
            val m = mgr(rt, FakeGauge(plenty), this, mutableListOf(basic), arrayOf<String?>(basic.id), files)
            m.ensureLoaded()

            m.onSeverePressure()
            advanceUntilIdle()

            assertThat(rt.isLoaded).isFalse()
            assertThat(m.status.value).isEqualTo(ModelManager.Status.Idle(basic))
        }
    }
}
