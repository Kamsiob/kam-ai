package com.kamsiob.kamai.llm

import android.content.Context
import com.kamsiob.kamai.data.KamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide holder for the one inference engine and the one model manager.
 *
 * The manager is the single source of truth for what is resident, and it has to
 * outlive any single screen so the Application's memory-pressure and lifecycle
 * callbacks can reach it. Everything, view models and the Application alike, gets
 * the same instances from here.
 */
object Models {

    // An application-lifetime scope for background unloads and pressure handling,
    // separate from any view model scope so it survives screen changes.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var engineRef: InferenceEngine? = null

    @Volatile
    private var managerRef: ModelManager? = null

    fun engine(context: Context): InferenceEngine =
        engineRef ?: synchronized(this) {
            engineRef ?: InferenceEngine(context.applicationContext).also { engineRef = it }
        }

    fun manager(context: Context): ModelManager =
        managerRef ?: synchronized(this) {
            managerRef ?: build(context.applicationContext).also { managerRef = it }
        }

    private fun build(context: Context): ModelManager {
        val repository = KamRepository.get(context)
        return ModelManager(
            runtime = engine(context),
            gauge = AndroidMemoryGauge(context),
            scope = scope,
            fileFor = { repository.fileFor(it) },
            activeModel = { repository.activeModel() },
            setActive = { id ->
                if (id != null) repository.setActiveModel(id) else repository.clearActiveModel()
            },
            installedModels = { repository.installedModels() },
        )
    }
}
