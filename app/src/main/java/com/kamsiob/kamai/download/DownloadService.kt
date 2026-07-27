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
import androidx.annotation.RequiresApi
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

    /**
     * Android gives a `dataSync` foreground service a limited amount of running
     * time per day. When it runs out the system calls this, and a service that
     * does not stop promptly is not warned again: the process is killed with
     * ForegroundServiceDidNotStopInTimeException. That crash is what led here,
     * and it lands on exactly the wrong people, the ones downloading five
     * gigabytes over a slow connection who need the background download most.
     *
     * So stop cleanly, keep the partial files, and say so plainly. The pause is
     * recorded as the system's rather than the user's, so it resumes by itself.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Downloads.pauseAllForSystemLimit(applicationContext)
        notifyWith(
            PAUSED_NOTIF_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("Download paused")
                .setContentText("Android limits how long apps can download in the background. Open Kam AI to pick it up from where it stopped.")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(false)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .build(),
        )
        stopSelf()
    }

    override fun onDestroy() {
        watcher?.cancel()
        super.onDestroy()
    }

    /**
     * The other half of the timeout problem. Once the daily `dataSync` budget is
     * spent, the system refuses to let the service go foreground at all and
     * throws instead, so resuming a download after a timeout would trade one
     * crash for another. Refusing is a legitimate answer here, not an error:
     * the download itself lives in [Downloads] and keeps running while the app
     * is open. All that is lost is the promise to continue in the background,
     * which the system has already withdrawn.
     */
    private fun startForegroundWith(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            android.util.Log.w("DownloadService", "Cannot go foreground, downloading without it", e)
            stopSelf()
        }
    }

    private fun notify(n: Notification) = notifyWith(NOTIF_ID, n)

    private fun notifyWith(id: Int, n: Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
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

        // A separate id on purpose. Stopping the service tears down the ongoing
        // notification NOTIF_ID owns, which would take the explanation with it.
        private const val PAUSED_NOTIF_ID = 43

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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Starting is refused outright in some states, such as after the
                // daily foreground budget is gone. A download that cannot be
                // backgrounded is still a download worth running.
                android.util.Log.w("DownloadService", "Could not start the download service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DownloadService::class.java))
        }
    }
}
