package com.kamsiob.kamai.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest

/**
 * A tiny foreground service whose only job is to keep the process alive while
 * downloads run, so a model finishes even when the user leaves the app, and to
 * show one honest progress notification. It owns no download logic itself; that
 * all lives in [Downloads]. It starts when the first download begins and stops
 * when the last one ends.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForegroundWith(buildNotification("Preparing download", 0, true))
        // Keep the notification in step with what is actually downloading.
        watcher = scope.launch {
            Downloads.items.collectLatest { items ->
                val active = items.filter {
                    it.status == Downloads.Status.RUNNING || it.status == Downloads.Status.VERIFYING
                }
                if (active.isEmpty()) return@collectLatest
                val overall = (active.map { it.fraction }.average() * 100).toInt()
                val title = if (active.size == 1) {
                    "Downloading ${active.first().displayName}"
                } else {
                    "Downloading ${active.size} items"
                }
                notify(buildNotification(title, overall, active.any { it.status == Downloads.Status.VERIFYING }))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watcher?.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notify(n: Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun buildNotification(title: String, percent: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Finishing up" else "$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, indeterminate)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()

    /** Tapping the notification returns to the app. */
    private fun openAppIntent(): android.app.PendingIntent? {
        val launch = Intent(this, com.kamsiob.kamai.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return android.app.PendingIntent.getActivity(
            this, 0, launch,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 42

        fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows model, voice, and pack downloads in progress."
                        setShowBadge(false)
                    },
                )
            }
        }

        fun ensureRunning(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
