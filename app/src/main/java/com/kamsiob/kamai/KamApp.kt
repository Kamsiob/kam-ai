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
        // On any real pressure, or when the app is fully backgrounded and the
        // system wants memory, drop the resident model. It reloads lazily on the
        // next use. This is the counterpart to the lazy-load rule and is what
        // keeps a large model from getting the app killed.
        val pressure = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure) {
            Models.manager(this).onMemoryPressure()
        }
    }

    @Deprecated("Deprecated in Android, still called on older levels")
    override fun onLowMemory() {
        super.onLowMemory()
        Models.manager(this).onMemoryPressure()
    }
}
