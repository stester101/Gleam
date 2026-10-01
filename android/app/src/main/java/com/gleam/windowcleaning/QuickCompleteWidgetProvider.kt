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

class QuickCompleteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, QuickCompleteWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val s = SnapshotStore.read(context)
            val job = s.todayJobs.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_quick_complete)

            views.setTextViewText(R.id.quick_address, job?.address ?: "Nothing left today")
            if (job != null && job.price > 0) {
                views.setViewVisibility(R.id.quick_price, View.VISIBLE)
                views.setTextViewText(R.id.quick_price, NumberFormat.getCurrencyInstance(Locale.UK).format(job.price))
            } else {
                views.setViewVisibility(R.id.quick_price, View.GONE)
            }

            if (job == null) {
                views.setViewVisibility(R.id.quick_actions, View.GONE)
            } else {
                views.setViewVisibility(R.id.quick_actions, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.quick_paid,
                    actionIntent(context, widgetId, job.id, true)
                )
                views.setOnClickPendingIntent(
                    R.id.quick_unpaid,
                    actionIntent(context, widgetId, job.id, false)
                )
            }

            val open = PendingIntent.getActivity(
                context, 300 + widgetId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.quick_address, open)
            manager.updateAppWidget(widgetId, views)
        }

        private fun actionIntent(
            context: Context,
            widgetId: Int,
            jobId: String,
            paid: Boolean
        ): PendingIntent {
            val intent = Intent(context, QuickCompleteReceiver::class.java).apply {
                putExtra(QuickCompleteReceiver.EXTRA_JOB_ID, jobId)
                putExtra(QuickCompleteReceiver.EXTRA_PAID, paid)
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 10 + if (paid) 1 else 2,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
