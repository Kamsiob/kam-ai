package com.kamsiob.kamai.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.kamsiob.kamai.data.DatabaseKey
import com.kamsiob.kamai.ui.KamAiApp
import com.kamsiob.kamai.ui.components.ConfirmDialog
import com.kamsiob.kamai.ui.components.ConfirmRequest
import com.kamsiob.kamai.ui.components.ConfirmTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The very top of the app. Everything below it, including the database, is gated
 * behind the app lock. When the lock is off, this is a straight passthrough to
 * the app. When it is on, nothing that can read or add data renders until it is
 * satisfied. PART 3.
 */
@Composable
fun KamRoot(activity: FragmentActivity) {
    if (!AppLock.locked) {
        KamAiApp()
        return
    }

    var error by remember { mutableStateOf<String?>(null) }
    var forgetRequest by remember { mutableStateOf<ConfirmRequest?>(null) }

    val doBiometric: () -> Unit = {
        error = null
        Biometric.prompt(
            activity = activity,
            title = "Unlock Kam AI",
            subtitle = "Use your fingerprint, face, or phone code.",
            onSuccess = { AppLock.markUnlocked(null) },
            onError = { message -> error = message },
        )
    }

    // Device mode goes straight to the system prompt when the screen appears.
    LaunchedEffect(Unit) {
        if (AppLock.mode == AppLock.Mode.DEVICE) doBiometric()
    }

    LockScreen(
        mode = AppLock.mode,
        error = error,
        onSubmitPassphrase = { entered ->
            error = null
            // The passphrase is proven right by whether it unwraps the database
            // key. No passphrase is ever stored to compare against.
            val ok = runCatching { DatabaseKey.getOrCreate(activity, entered) }.isSuccess
            if (ok) {
                AppLock.markUnlocked(entered)
            } else {
                error = "That passphrase did not work. Try again."
            }
        },
        onUseBiometric = doBiometric,
        // Biometric convenience in passphrase mode would mean storing the
        // passphrase under a biometric-bound key; for now the strong mode is
        // passphrase-only, which is the honest, simplest form of "unrecoverable".
        biometricAvailable = AppLock.mode == AppLock.Mode.DEVICE,
        onForgot = {
            forgetRequest = ConfirmRequest(
                tier = ConfirmTier.MAJOR,
                title = "Start fresh?",
                body = "There is no way to recover a forgotten passphrase. Your existing " +
                    "conversations stay encrypted and unreadable, which is what keeps them " +
                    "safe from anyone else. Starting fresh erases them so you can use the " +
                    "app again.",
                undoneNote = "This erases everything and cannot be undone. If you have a " +
                    "backup, that is the only way to bring old data back.",
                confirmWord = "erase",
                confirmLabel = "Erase and start fresh",
                onConfirm = {
                    wipeAndStartFresh(activity)
                },
            )
        },
    )

    ConfirmDialog(request = forgetRequest, onDismiss = { forgetRequest = null })
}

/**
 * The forgot-passphrase wipe. It does not recover or reveal the old data, which
 * would defeat the whole point; it makes the encrypted data permanently
 * unopenable by destroying the key, clears the database file, turns the lock
 * off, and leaves a working fresh app.
 */
private fun wipeAndStartFresh(activity: FragmentActivity) {
    val context = activity.applicationContext
    // Close any open database handle first.
    runCatching { com.kamsiob.kamai.data.KamDatabase.closeAndForget() }
    // Destroy the key: the encrypted database becomes permanently unreadable.
    DatabaseKey.destroy(context)
    // Remove the database files so a clean one is created next open.
    val db = context.getDatabasePath(com.kamsiob.kamai.data.KamDatabase.NAME)
    listOf(db, java.io.File(db.path + "-wal"), java.io.File(db.path + "-shm"),
        java.io.File(db.parentFile, db.name + ".migrating")).forEach { it.delete() }
    // Turn the lock off and proceed into a fresh app.
    AppLock.disable()
    AppLock.markUnlocked(null)
}
