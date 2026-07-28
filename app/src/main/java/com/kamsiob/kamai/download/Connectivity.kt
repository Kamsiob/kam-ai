package com.kamsiob.kamai.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What the network is doing, for decisions that cost the user money or leave them
 * stuck (#79).
 *
 * Nothing in the app looked at this before. A five gigabyte model would start on
 * cellular without a word, and a download interrupted by process death sat paused
 * until somebody found it and pressed Resume.
 */
object Connectivity {

    /** What a download decision needs to know, in one value. */
    data class State(
        val online: Boolean,
        /** True on cellular, or on a wifi network the user has marked as metered. */
        val metered: Boolean,
    ) {
        /** Safe for something measured in gigabytes without asking first. */
        val unmeteredAndOnline: Boolean get() = online && !metered
    }

    fun state(context: Context): State {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return State(online = false, metered = false)
        val network = cm.activeNetwork ?: return State(online = false, metered = false)
        val caps = cm.getNetworkCapabilities(network)
            ?: return State(online = false, metered = false)

        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        // NOT_METERED is the honest signal: it covers a wifi network the user has
        // marked as metered, which a transport check would call free.
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return State(online = online, metered = metered)
    }

    /**
     * The state, and every change to it.
     *
     * Used to pick a waiting download back up when wifi returns, so somebody who
     * chose to wait does not have to watch for it themselves.
     */
    fun observe(context: Context): Flow<State> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            trySend(State(online = false, metered = false))
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(state(context)) }
            override fun onLost(network: Network) { trySend(state(context)) }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                // Fires when a network becomes metered or stops being metered
                // without going away, which is exactly the case a pure
                // available/lost callback would miss.
                trySend(state(context))
            }
        }

        trySend(state(context))
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}

/**
 * Whether a download should start, wait, or ask first (#79).
 *
 * Pure, so the rules can be read and tested without a device or a radio. Every
 * one of them is about spending something the user did not agree to spend: their
 * data allowance, their remaining disk, or their last few percent of battery.
 */
object DownloadGuard {

    /**
     * Anything at or above this is worth a question on a metered connection.
     *
     * Content packs run to about twenty megabytes since they began carrying
     * whole articles rather than introductions (#13), and nobody wants a dialog
     * for those. Models and voices are hundreds of megabytes upward, which is
     * real money on a capped plan.
     *
     * Twenty is comfortably under this line and deliberately so: the largest pack
     * is a fortieth of the smallest model, and a question asked too often is a
     * question people learn to dismiss without reading.
     */
    const val METERED_ASK_BYTES: Long = 50L * 1024 * 1024

    /** Free space the app insists on leaving behind after a download. */
    const val DISK_HEADROOM_BYTES: Long = 500L * 1024 * 1024

    /** Below this, with no charger, a long download is worth mentioning. */
    const val LOW_BATTERY_PERCENT: Int = 20

    sealed interface Verdict {
        /** Nothing in the way. */
        data object Go : Verdict

        /** Possible, but the user should know something first. */
        data class Warn(val message: String, val proceedLabel: String) : Verdict

        /** Cannot proceed, with the reason and the numbers. */
        data class Stop(val message: String) : Verdict
    }

    /**
     * @param sizeBytes what the download will take.
     * @param freeBytes what is free on the volume it lands on.
     * @param batteryPercent 0..100, or null when it cannot be read.
     * @param charging whether the device is plugged in.
     */
    /**
     * Whether a download of this size can land at all, headroom included.
     *
     * Public and separate so the offer and the download agree. #75 asks for the
     * storage check to happen *before* something is offered rather than at
     * download time, and the way that goes wrong is two checks with two slightly
     * different constants: one screen offers a model the next screen refuses.
     */
    fun fitsOnDisk(sizeBytes: Long, freeBytes: Long): Boolean =
        freeBytes >= sizeBytes + DISK_HEADROOM_BYTES

    fun check(
        sizeBytes: Long,
        network: Connectivity.State,
        freeBytes: Long,
        batteryPercent: Int?,
        charging: Boolean,
    ): Verdict {
        // Order matters. Disk first, because no amount of agreeing makes a
        // download fit; then network, because it costs money; then battery, which
        // is only ever advice.
        if (!fitsOnDisk(sizeBytes, freeBytes)) {
            return Verdict.Stop(
                "This needs ${bytes(sizeBytes)} and there is ${bytes(freeBytes)} free. " +
                    "Free up some space, or pick a smaller model.",
            )
        }

        if (!network.online) {
            return Verdict.Stop(
                "This needs a connection to download. Everything already on the phone " +
                    "still works, and this will be here when you are back online.",
            )
        }

        if (network.metered && sizeBytes >= METERED_ASK_BYTES) {
            return Verdict.Warn(
                message = "You are on mobile data and this is ${bytes(sizeBytes)}. " +
                    "Waiting for wifi costs nothing, and it will start on its own when " +
                    "you are back on it.",
                proceedLabel = "Use mobile data anyway",
            )
        }

        if (batteryPercent != null && batteryPercent < LOW_BATTERY_PERCENT && !charging) {
            return Verdict.Warn(
                message = "Battery is at $batteryPercent percent and this is " +
                    "${bytes(sizeBytes)}, which takes a while. Plugging in first is " +
                    "safer than finding out halfway.",
                proceedLabel = "Start anyway",
            )
        }

        return Verdict.Go
    }

    /**
     * Whether a download paused by something other than the user should pick
     * itself back up.
     *
     * The original rule was never to auto-resume, on the grounds that resuming
     * spends data and should stay the user's call. That is right about cellular
     * and wrong about everything else: a download killed by process death on
     * home wifi sat paused until somebody noticed, which is not a decision
     * anybody made.
     *
     * So it resumes only when the user did not pause it themselves and the
     * connection is one that costs nothing.
     */
    fun shouldAutoResume(userPaused: Boolean, network: Connectivity.State): Boolean =
        !userPaused && network.unmeteredAndOnline

    private fun bytes(b: Long): String {
        val gb = b / 1_000_000_000.0
        if (gb >= 1.0) return "${"%.1f".format(gb)} GB"
        return "${(b / 1_000_000.0).toInt()} MB"
    }
}
