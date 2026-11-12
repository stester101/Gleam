package com.gleam.windowcleaning

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.webkit.JavascriptInterface
import android.widget.Toast

class GleamNativeBridge(
    private val activity: MainActivity
) {
    @JavascriptInterface
    fun syncState(json: String) {
        val accepted = NativeStateStore.setFromWeb(activity, json)
        if (accepted) {
            SnapshotStore.updateFromState(activity, json)
            refreshWidgets()
            RecoveryStore.onSync(activity, json)
        }
    }

    @JavascriptInterface
    fun recoveryList(): String = RecoveryStore.list(activity)

    @JavascriptInterface
    fun recoveryRead(name: String): String = RecoveryStore.read(activity, name)

    @JavascriptInterface
    fun recoverySave(label: String, json: String) {
        RecoveryStore.saveBefore(activity, label, json)
    }

    @JavascriptInterface
    fun pendingNativeState(): String =
        NativeStateStore.pendingState(activity)

    @JavascriptInterface
    fun acknowledgeNativeState() {
        NativeStateStore.acknowledge(activity)
    }

    @JavascriptInterface
    fun acknowledgeNativeOps(idsJson: String) {
        NativeStateStore.acknowledgeOps(activity, idsJson)
    }

    @JavascriptInterface
    fun setBackActive(active: Boolean) {
        activity.setBackActive(active)
    }

    @JavascriptInterface
    fun notificationsAllowed(): Boolean = activity.notificationsAllowed()

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
    fun saveFile(name: String, mimeType: String, text: String): Boolean {
        val ok = Downloads.write(activity, name, mimeType, text)
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                if (ok) "Saved to Downloads/Gleam" else "Could not save the file",
                Toast.LENGTH_SHORT
            ).show()
        }
        return ok
    }

    @JavascriptInterface
    fun appVersion(): String = BuildConfig.VERSION_NAME
}
