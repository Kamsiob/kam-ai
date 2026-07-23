package com.kamsiob.kamai

import android.app.Application
import android.content.ComponentCallbacks2
import com.kamsiob.kamai.llm.Models

/**
 * The application. Its one job beyond being the process root is to relay system
 * memory pressure to the model manager, so a resident model is released the
 * moment the system needs memory rather than waiting to be killed.
 */
class KamApp : Application() {

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val manager = Models.manager(this)
        when {
            // Severe: the app is backgrounded and the system wants memory, or the
            // device is critically low. Unload the model entirely.
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE ||
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ->
                manager.onSeverePressure()

            // Moderate: the app is in the foreground but memory is getting tight.
            // Release the KV cache, keep the model mapped, keep the conversation.
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                manager.onModeratePressure()
        }
    }

    @Deprecated("Deprecated in Android, still called on older levels")
    override fun onLowMemory() {
        super.onLowMemory()
        Models.manager(this).onSeverePressure()
    }
}
