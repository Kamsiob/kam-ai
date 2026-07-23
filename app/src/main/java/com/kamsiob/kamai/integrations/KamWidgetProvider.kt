package com.kamsiob.kamai.integrations

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kamsiob.kamai.MainActivity
import com.kamsiob.kamai.R

/**
 * A home-screen widget: one tap into a new chat, one into voice capture. Both just
 * launch the app with an action extra, so the widget needs no permissions.
 */
class KamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_kam)
            views.setOnClickPendingIntent(R.id.widget_new_chat, launch(context, MainActivity.ACTION_NEW_CHAT, 1))
            views.setOnClickPendingIntent(R.id.widget_voice, launch(context, MainActivity.ACTION_VOICE, 2))
            manager.updateAppWidget(id, views)
        }
    }

    private fun launch(context: Context, action: String, req: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ACTION, action)
        return PendingIntent.getActivity(
            context, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
