package com.kamsiob.kamai.integrations

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import com.kamsiob.kamai.MainActivity

/**
 * A quick-settings tile that jumps straight into a new chat. Zero permissions;
 * it only launches the app.
 */
class KamTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_NEW_CHAT)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pi)
    }
}
