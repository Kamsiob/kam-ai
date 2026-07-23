package com.kamsiob.kamai

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Crash visibility without any telemetry. The app never phones home, so a crash
 * that only shows the system's "app keeps stopping" dialog leaves nothing a user
 * could report and nothing a developer could act on. Instead the last uncaught
 * exception is written to a local file the user can view and share from Settings,
 * entirely on their terms.
 *
 * This deliberately does not swallow the crash. It records, then hands off to the
 * platform's own default handler, so the process still dies as it should and the
 * OS still shows its dialog. Catching a crash and carrying on would leave the app
 * in an unknown state, which is worse than an honest stop.
 */
object CrashLog {

    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            // Hand off so the process still dies and the OS dialog still shows.
            // Never swallow: a survived crash is a corrupted, lying app.
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }
        val text = buildString {
            append("Kam AI crash report\n")
            append("Build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Elapsed since boot (ms): ${android.os.SystemClock.elapsedRealtime()}\n")
            append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
            append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
            append("Thread: ${thread.name}\n\n")
            append(trace.toString())
        }
        dir(context).mkdirs()
        File(dir(context), FILE).writeText(text)
    }

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** The last recorded crash, or null if there is none. */
    fun lastCrash(context: Context): String? =
        File(dir(context), FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(dir(context), FILE).delete()
    }
}
