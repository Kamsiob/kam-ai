package com.kamsiob.kamai.integrations

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.kamsiob.kamai.MainActivity

/**
 * A quick-settings tile that jumps straight into a new chat. Zero permissions;
 * it only launches the app.
 */
class KamTileService : TileService() {

    /**
     * Suppressed deliberately, and only here.
     *
     * Lint treats the Intent overload as an error because it is deprecated on API
     * 34 and up. It is also the only one that exists below 34, and minSdk is 31,
     * so the branch below is required rather than sloppy. Following the lint
     * advice unconditionally is what caused the crash this fix is for.
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_NEW_CHAT)
        // Two overloads, and the version split is real rather than pedantic.
        // The PendingIntent overload arrived in API 34; minSdk here is 31, so on
        // Android 12 and 13 this called a method that does not exist and the tile
        // crashed the moment it was tapped. Found by lint, not by use, because the
        // tile is easy to add and forget.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pi)
        } else {
            // Deprecated on 34 and up, and the only thing that works below it.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
