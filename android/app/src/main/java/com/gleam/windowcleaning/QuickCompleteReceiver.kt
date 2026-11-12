package com.gleam.windowcleaning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class QuickCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                handle(appContext, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun toast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID).orEmpty()
        val paid = intent.getBooleanExtra(EXTRA_PAID, false)
        if (jobId.isBlank()) return

        // two-step: the first tap arms the button, a second tap within a few seconds confirms
        if (!QuickArm.confirms(context, jobId, paid)) {
            QuickArm.arm(context, jobId, paid)
            QuickCompleteWidgetProvider.updateAll(context)
            val appContext = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                QuickArm.clearIfExpired(appContext)
                QuickCompleteWidgetProvider.updateAll(appContext)
            }, QuickArm.WINDOW_MS + 200)
            return
        }
        QuickArm.clear(context)

        val result = NativeStateStore.quickComplete(context, jobId, paid)
        if (result != NativeStateStore.QuickResult.DONE) SnapshotStore.refresh(context)
        GleamWidgetProvider.updateAll(context)
        NextJobWidgetProvider.updateAll(context)
        QuickCompleteWidgetProvider.updateAll(context)
        val message = when (result) {
            NativeStateStore.QuickResult.DONE -> if (paid) "Cleaned · paid" else "Cleaned · not paid"
            NativeStateStore.QuickResult.NOT_DUE -> "Already done"
            NativeStateStore.QuickResult.FAILED -> "Open Gleam and try again"
        }
        toast(context, message)
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_PAID = "paid"
    }
}

/** Armed state for Quick Complete's tap-again-to-confirm. */
object QuickArm {
    const val WINDOW_MS = 5000L
    private const val PREFS = "gleam_quick_arm"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun arm(c: Context, jobId: String, paid: Boolean) {
        prefs(c).edit().putString("job", jobId).putBoolean("paid", paid)
            .putLong("at", System.currentTimeMillis()).commit()
    }

    /** Which button is armed for this job right now: true = paid, false = not paid, null = none. */
    fun armedFor(c: Context, jobId: String): Boolean? {
        val p = prefs(c)
        if (p.getString("job", "") != jobId) return null
        if (System.currentTimeMillis() - p.getLong("at", 0L) > WINDOW_MS) return null
        return p.getBoolean("paid", false)
    }

    fun confirms(c: Context, jobId: String, paid: Boolean): Boolean = armedFor(c, jobId) == paid

    fun clear(c: Context) { prefs(c).edit().clear().commit() }

    fun clearIfExpired(c: Context) {
        if (System.currentTimeMillis() - prefs(c).getLong("at", 0L) > WINDOW_MS) clear(c)
    }
}
