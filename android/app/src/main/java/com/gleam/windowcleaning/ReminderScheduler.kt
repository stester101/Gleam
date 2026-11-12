package com.gleam.windowcleaning

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONObject
import java.time.Duration
import java.time.ZonedDateTime

data class NotificationSettings(
    val todayEnabled: Boolean = true,
    val todayHour: Int = 7,
    val todayMinute: Int = 0,
    val tomorrowEnabled: Boolean = true,
    val tomorrowHour: Int = 18,
    val tomorrowMinute: Int = 0,
    val leftBehindEnabled: Boolean = true
)

object ReminderScheduler {
    private const val PREFS = "gleam_native"
    private const val MASTER = "notificationsEnabled"
    private const val SETTINGS = "notificationSettings"
    private const val MORNING = "gleam_morning"
    private const val EVENING = "gleam_evening"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(MASTER, false)

    fun settings(context: Context): NotificationSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SETTINGS, "")
            .orEmpty()
        if (raw.isBlank()) return NotificationSettings()
        return runCatching {
            val j = JSONObject(raw)
            NotificationSettings(
                todayEnabled = j.optBoolean("todayEnabled", true),
                todayHour = j.optInt("todayHour", 7).coerceIn(0, 23),
                todayMinute = j.optInt("todayMinute", 0).coerceIn(0, 59),
                tomorrowEnabled = j.optBoolean("tomorrowEnabled", true),
                tomorrowHour = j.optInt("tomorrowHour", 18).coerceIn(0, 23),
                tomorrowMinute = j.optInt("tomorrowMinute", 0).coerceIn(0, 59),
                leftBehindEnabled = j.optBoolean("leftBehindEnabled", true)
            )
        }.getOrDefault(NotificationSettings())
    }

    fun settingsJson(context: Context): String {
        val s = settings(context)
        return JSONObject().apply {
            put("enabled", enabled(context))
            put("todayEnabled", s.todayEnabled)
            put("todayHour", s.todayHour)
            put("todayMinute", s.todayMinute)
            put("tomorrowEnabled", s.tomorrowEnabled)
            put("tomorrowHour", s.tomorrowHour)
            put("tomorrowMinute", s.tomorrowMinute)
            put("leftBehindEnabled", s.leftBehindEnabled)
        }.toString()
    }

    fun setSettings(context: Context, json: String) {
        val j = runCatching { JSONObject(json) }.getOrNull() ?: return
        val s = NotificationSettings(
            todayEnabled = j.optBoolean("todayEnabled", true),
            todayHour = j.optInt("todayHour", 7).coerceIn(0, 23),
            todayMinute = j.optInt("todayMinute", 0).coerceIn(0, 59),
            tomorrowEnabled = j.optBoolean("tomorrowEnabled", true),
            tomorrowHour = j.optInt("tomorrowHour", 18).coerceIn(0, 23),
            tomorrowMinute = j.optInt("tomorrowMinute", 0).coerceIn(0, 59),
            leftBehindEnabled = j.optBoolean("leftBehindEnabled", true)
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MASTER, j.optBoolean("enabled", enabled(context)))
            .putString(SETTINGS, JSONObject().apply {
                put("todayEnabled", s.todayEnabled)
                put("todayHour", s.todayHour)
                put("todayMinute", s.todayMinute)
                put("tomorrowEnabled", s.tomorrowEnabled)
                put("tomorrowHour", s.tomorrowHour)
                put("tomorrowMinute", s.tomorrowMinute)
                put("leftBehindEnabled", s.leftBehindEnabled)
            }.toString())
            .apply()
        reschedule(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MASTER, enabled)
            .apply()
        reschedule(context)
    }

    /** Re-anchors reminders to the wall clock. Called on settings change and on app start. */
    fun refresh(context: Context) = reschedule(context)

    private fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(MORNING)
        wm.cancelUniqueWork(EVENING)
        if (!enabled(context)) return

        val s = settings(context)
        if (s.todayEnabled || s.leftBehindEnabled) scheduleNext(context, "morning")
        if (s.tomorrowEnabled) scheduleNext(context, "evening")
    }

    /**
     * One-shot work for the next occurrence of the chosen local time. BriefWorker calls this
     * again after each run, so the time stays right across clock changes (a 24h periodic
     * request drifts by an hour at every BST change).
     */
    fun scheduleNext(context: Context, kind: String) {
        if (!enabled(context)) return
        val s = settings(context)
        val (name, hour, minute) = if (kind == "evening") {
            Triple(EVENING, s.tomorrowHour, s.tomorrowMinute)
        } else {
            Triple(MORNING, s.todayHour, s.todayMinute)
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = OneTimeWorkRequestBuilder<BriefWorker>()
            .setInitialDelay(Duration.between(now, next))
            .setInputData(workDataOf("kind" to kind))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}
