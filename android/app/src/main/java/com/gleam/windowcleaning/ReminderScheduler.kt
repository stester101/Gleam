package com.gleam.windowcleaning

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val PREFS = "gleam_native"
    private const val KEY = "notificationsEnabled"
    private const val MORNING = "gleam_morning"
    private const val EVENING = "gleam_evening"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, enabled)
            .apply()

        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(MORNING)
        wm.cancelUniqueWork(EVENING)

        if (enabled) {
            schedule(context, MORNING, 7, "morning")
            schedule(context, EVENING, 18, "evening")
        }
    }

    private fun schedule(context: Context, name: String, hour: Int, kind: String) {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<BriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next))
            .setInputData(workDataOf("kind" to kind))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            name,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
