package com.gleam.windowcleaning

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class GleamWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GleamWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val snapshot = SnapshotStore.read(context)
            val views = RemoteViews(context.packageName, R.layout.widget_gleam)

            views.setTextViewText(
                R.id.widget_count,
                if (snapshot.todayCount == 1) "1 left" else "${snapshot.todayCount} left"
            )

            val a = snapshot.todayAddresses
            views.setTextViewText(
                R.id.widget_next1,
                a.getOrNull(0) ?: if (snapshot.leftBehind > 0) {
                    "${snapshot.leftBehind} left behind"
                } else {
                    "Nothing scheduled today"
                }
            )

            setOptionalLine(views, R.id.widget_next2, a.getOrNull(1))
            setOptionalLine(views, R.id.widget_next3, a.getOrNull(2))

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }

        private fun setOptionalLine(views: RemoteViews, id: Int, value: String?) {
            if (value.isNullOrBlank()) {
                views.setViewVisibility(id, View.GONE)
            } else {
                views.setViewVisibility(id, View.VISIBLE)
                views.setTextViewText(id, value)
            }
        }
    }
}
