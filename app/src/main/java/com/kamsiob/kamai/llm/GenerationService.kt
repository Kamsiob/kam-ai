package com.kamsiob.kamai.llm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the process alive while a response is being written (#96).
 *
 * A user who asks something and switches to another app is not doing anything
 * unusual: a long answer takes real time. Android freezes cached processes, so
 * leaving the app stalled the decode loop, and the user came back to a
 * half-written answer that had stopped rather than finished.
 *
 * Deliberately the same shape as [com.kamsiob.kamai.download.DownloadService]
 * rather than a second mechanism: same lifecycle, same notification pattern, so
 * the two behave consistently and there is one thing to understand rather than
 * two. It owns no generation logic. It starts when a response starts and stops
 * when it ends.
 *
 * `dataSync` is the foreground service type, which is already declared for
 * downloads, so this adds no permission. Verified against the merged manifest
 * rather than assumed.
 */
class GenerationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        startForegroundWith(buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Cancellable from outside the app, per the rule that nothing slow is
            // allowed to be uninterruptible. The engine stops inside its own
            // decode loop, so the partial answer is kept and marked honestly by
            // the same path a stop button uses.
            Models.engine(applicationContext).requestStop()
            stopSelf()
            return START_NOT_STICKY
        }
        // Not sticky. A process killed mid-answer should not have the system
        // restart this service into a state where nothing is generating; the
        // half-written message is repaired on next load instead.
        return START_NOT_STICKY
    }

    private fun startForegroundWith(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, GenerationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Writing a response")
            .setContentText("Kam AI is working on your phone.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            // Indeterminate on purpose. Token counts are not a percentage of
            // anything knowable, and a bar that guesses is worse than one that
            // says only that work is happening.
            .setProgress(0, 0, true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = Intent(this, com.kamsiob.kamai.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val CHANNEL = "generation"
        private const val NOTIF_ID = 43
        private const val ACTION_STOP = "com.kamsiob.kamai.STOP_GENERATION"

        fun ensureChannel(context: Context) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Responses",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description =
                            "Shows when Kam AI is writing a response, so it keeps " +
                                "going while you are in another app."
                        setShowBadge(false)
                    },
                )
            }
        }

        /**
         * Starts the service, or does nothing if it cannot.
         *
         * Wrapped because starting a foreground service can throw when the app is
         * in a state Android does not allow it from, and a failure here must never
         * take down the answer it was meant to protect. Without the notification
         * permission the service still runs and the notification simply is not
         * shown, so generation continues either way.
         */
        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, GenerationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GenerationService::class.java)) }
        }
    }
}
