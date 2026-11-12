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
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_PAID = "paid"
    }
}
