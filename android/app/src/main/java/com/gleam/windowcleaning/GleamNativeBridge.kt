package com.gleam.windowcleaning

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File

class GleamNativeBridge(
    private val activity: MainActivity
) {
    @JavascriptInterface
    fun syncState(json: String) {
        val accepted = NativeStateStore.setFromWeb(activity, json)
        if (accepted) {
            SnapshotStore.updateFromState(activity, json)
            refreshWidgets()
        }
    }

    @JavascriptInterface
    fun pendingNativeState(): String =
        NativeStateStore.pendingState(activity)

    @JavascriptInterface
    fun acknowledgeNativeState() {
        NativeStateStore.acknowledge(activity)
    }

    @JavascriptInterface
    fun haptic() {
        activity.performGleamHaptic()
    }

    @JavascriptInterface
    fun notificationsEnabled(): Boolean =
        ReminderScheduler.enabled(activity)

    @JavascriptInterface
    fun notificationSettings(): String =
        ReminderScheduler.settingsJson(activity)

    @JavascriptInterface
    fun setNotificationSettings(json: String) {
        ReminderScheduler.setSettings(activity, json)
        if (ReminderScheduler.enabled(activity)) {
            activity.requestNotificationPermissionIfNeeded()
        }
    }

    @JavascriptInterface
    fun setNotifications(enabled: Boolean) {
        ReminderScheduler.setEnabled(activity, enabled)
        if (enabled) activity.requestNotificationPermissionIfNeeded()
    }

    @JavascriptInterface
    fun requestWidget(type: String) {
        activity.runOnUiThread {
            val clazz = when (type) {
                "next" -> NextJobWidgetProvider::class.java
                "quick" -> QuickCompleteWidgetProvider::class.java
                else -> GleamWidgetProvider::class.java
            }
            val manager = AppWidgetManager.getInstance(activity)
            val provider = ComponentName(activity, clazz)
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(provider, null, null)
            } else {
                Toast.makeText(
                    activity,
                    "Add the Gleam widget from your home-screen widget picker.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun refreshWidgets() {
        GleamWidgetProvider.updateAll(activity)
        NextJobWidgetProvider.updateAll(activity)
        QuickCompleteWidgetProvider.updateAll(activity)
    }

    @JavascriptInterface
    fun saveFile(name: String, mimeType: String, text: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Gleam"
                    )
                }
                val uri = activity.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: error("Could not create download")
                activity.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("Could not write download")
            } else {
                val dir = File(
                    activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "Gleam"
                )
                dir.mkdirs()
                File(dir, name).writeText(text)
            }

            activity.runOnUiThread {
                Toast.makeText(
                    activity,
                    "Saved to Downloads/Gleam",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.onFailure {
            activity.runOnUiThread {
                Toast.makeText(
                    activity,
                    "Could not save the file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @JavascriptInterface
    fun appVersion(): String = "2.0.0-alpha2"
}
