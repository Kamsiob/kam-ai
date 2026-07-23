package com.kamsiob.kamai.llm

import android.app.ActivityManager
import android.content.Context

/** The real memory gauge, reading the system's current available memory. */
class AndroidMemoryGauge(context: Context) : MemoryGauge {
    private val manager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private fun info() = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }

    override fun availableBytes(): Long = info().availMem

    override fun totalBytes(): Long = info().totalMem

    override fun lowMemory(): Boolean = info().lowMemory
}
