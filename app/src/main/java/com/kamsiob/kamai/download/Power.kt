package com.kamsiob.kamai.download

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Battery, for the one decision that needs it (#79).
 *
 * A five gigabyte download and install takes long enough that starting it at
 * nine percent with no charger is worth a sentence beforehand rather than a
 * failure halfway. Nothing else in the app cares, so this stays two functions
 * rather than a service.
 */
object Power {

    /** 0..100, or null when it cannot be read, which is never treated as flat. */
    fun batteryPercent(context: Context): Int? = runCatching {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    }.getOrNull()

    fun isCharging(context: Context): Boolean = runCatching {
        val bm = context.getSystemService(BatteryManager::class.java)
        if (bm != null) return bm.isCharging
        // Older path, kept because the property above is not guaranteed on every
        // device and reporting "not charging" while plugged in would produce a
        // warning nobody needs.
        val status = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)
}
