package com.gleam.windowcleaning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class QuickCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID).orEmpty()
        val paid = intent.getBooleanExtra(EXTRA_PAID, false)
        if (jobId.isBlank()) return

        val ok = NativeStateStore.quickComplete(context, jobId, paid)
        if (ok) {
            GleamWidgetProvider.updateAll(context)
            NextJobWidgetProvider.updateAll(context)
            QuickCompleteWidgetProvider.updateAll(context)
            Toast.makeText(
                context,
                if (paid) "Cleaned · paid" else "Cleaned · not paid",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(context, "Open Gleam and try again", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_PAID = "paid"
    }
}
