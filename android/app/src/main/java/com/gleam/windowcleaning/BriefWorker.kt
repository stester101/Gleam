package com.gleam.windowcleaning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class BriefWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        if (!ReminderScheduler.enabled(applicationContext)) return Result.success()
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val snapshot = SnapshotStore.read(applicationContext)
        val settings = ReminderScheduler.settings(applicationContext)
        val kind = inputData.getString("kind") ?: "morning"

        val content = when (kind) {
            "evening" -> {
                if (!settings.tomorrowEnabled || snapshot.tomorrowCount <= 0) {
                    return Result.success()
                }
                val title = if (snapshot.tomorrowCount == 1) {
                    "1 job tomorrow"
                } else {
                    "${snapshot.tomorrowCount} jobs tomorrow"
                }
                val body = snapshot.tomorrowRounds.ifBlank { "Open Gleam to review tomorrow." }
                Triple(title, body, 2202)
            }
            else -> {
                val showToday = settings.todayEnabled && snapshot.todayCount > 0
                val showLate = settings.leftBehindEnabled && snapshot.leftBehind > 0
                if (!showToday && !showLate) return Result.success()

                val title = when {
                    showToday && snapshot.todayCount == 1 -> "1 job today"
                    showToday -> "${snapshot.todayCount} jobs today"
                    snapshot.leftBehind == 1 -> "1 job left behind"
                    else -> "${snapshot.leftBehind} jobs left behind"
                }

                val next = if (showToday) snapshot.todayJobs.firstOrNull()?.address else null
                val parts = mutableListOf<String>()
                if (!next.isNullOrBlank()) parts += "Next: $next"
                if (showLate) {
                    parts += if (snapshot.leftBehind == 1) {
                        "1 left behind"
                    } else {
                        "${snapshot.leftBehind} left behind"
                    }
                }
                Triple(title, parts.joinToString(" · ").ifBlank { "Open Gleam to review." }, 2201)
            }
        }

        ensureChannel(applicationContext)

        val openIntent = Intent(applicationContext, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            applicationContext,
            10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.first)
            .setContentText(content.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.second))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(content.third, notification)

        return Result.success()
    }

    companion object {
        private const val CHANNEL = "gleam_work"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Work reminders",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Today, tomorrow and left-behind Gleam work"
                    }
                )
            }
        }
    }
}
