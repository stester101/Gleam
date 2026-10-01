package com.gleam.windowcleaning

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.NumberFormat
import java.util.Locale

class NextJobWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, NextJobWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val s = SnapshotStore.read(context)
            val job = s.todayJobs.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_next_job)
            views.setTextViewText(R.id.next_address, job?.address ?: "Nothing left today")
            if (job != null && job.price > 0) {
                views.setViewVisibility(R.id.next_price, View.VISIBLE)
                views.setTextViewText(R.id.next_price, NumberFormat.getCurrencyInstance(Locale.UK).format(job.price))
            } else {
                views.setViewVisibility(R.id.next_price, View.GONE)
            }
            val open = PendingIntent.getActivity(
                context, 201,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.next_widget_root, open)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
