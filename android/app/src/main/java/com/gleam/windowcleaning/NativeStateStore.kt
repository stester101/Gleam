package com.gleam.windowcleaning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object NativeStateStore {
    private const val PREFS = "gleam_native_state"
    private const val STATE = "state"
    private const val REVISION = "revision"
    private const val DIRTY = "nativeDirty"
    private const val OPS = "pendingOps"

    enum class QuickResult { DONE, NOT_DUE, FAILED }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ops(context: Context): JSONArray =
        runCatching { JSONArray(prefs(context).getString(OPS, "[]").orEmpty().ifBlank { "[]" }) }
            .getOrDefault(JSONArray())

    @Synchronized
    fun setFromWeb(context: Context, json: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DIRTY, false)) return false
        prefs.edit()
            .putString(STATE, json)
            .putLong(REVISION, System.currentTimeMillis())
            .apply()
        return true
    }

    fun state(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(STATE, "")
            .orEmpty()

    /**
     * Pending widget changes for the WebView. Normally a list of per-job operations that the
     * web layer replays idempotently onto its own state, so newer app edits are never
     * overwritten. Falls back to the full mirror for a legacy (Alpha 2) dirty state.
     */
    @Synchronized
    fun pendingState(context: Context): String {
        val prefs = prefs(context)
        if (!prefs.getBoolean(DIRTY, false)) return ""
        val ops = ops(context)
        if (ops.length() > 0) return JSONObject().put("ops", ops).toString()
        return prefs.getString(STATE, "").orEmpty()
    }

    fun hasPendingNativeChange(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(DIRTY, false)

    @Synchronized
    fun acknowledge(context: Context) {
        prefs(context).edit()
            .putBoolean(DIRTY, false)
            .putString(OPS, "[]")
            .commit()
    }

    /** Clears only the operations the web layer has applied and saved. */
    @Synchronized
    fun acknowledgeOps(context: Context, idsJson: String) {
        val done = runCatching { JSONArray(idsJson) }.getOrNull() ?: return
        val ids = (0 until done.length()).map { done.optString(it) }.toSet()
        val remaining = JSONArray()
        val current = ops(context)
        for (i in 0 until current.length()) {
            val op = current.optJSONObject(i) ?: continue
            if (op.optString("id") !in ids) remaining.put(op)
        }
        prefs(context).edit()
            .putString(OPS, remaining.toString())
            .putBoolean(DIRTY, remaining.length() > 0)
            .commit()
    }

    fun revision(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(REVISION, 0L)

    @Synchronized
    fun quickComplete(context: Context, jobId: String, paid: Boolean): QuickResult {
        val raw = state(context)
        if (raw.isBlank()) return QuickResult.FAILED

        return runCatching {
            val root = JSONObject(raw)
            val jobs = root.optJSONArray("jobs") ?: return QuickResult.FAILED
            var job: JSONObject? = null
            for (i in 0 until jobs.length()) {
                val candidate = jobs.optJSONObject(i) ?: continue
                if (candidate.optString("id") == jobId) {
                    job = candidate
                    break
                }
            }
            val j = job ?: return QuickResult.FAILED
            if (j.optString("status", "active") != "active") return QuickResult.NOT_DUE

            val today = LocalDate.now()
            val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // Guard against double taps and stale widgets: only a job that is due
            // (today or left behind) and not already done today can be completed.
            val due = j.optString("next")
            if (due.isBlank() || due > todayIso) return QuickResult.NOT_DUE
            if (j.optString("last") == todayIso) return QuickResult.NOT_DUE
            val price = j.optDouble("price", 0.0)
            val paidAmount = if (paid) price else 0.0

            j.put("last", todayIso)

            val weeks = j.optInt("weeks", 0)
            if (weeks > 0) {
                val anchor = j.optString("anchor")
                val currentNext = j.optString("next")
                val next = if (anchor.isNotBlank()) {
                    var n = LocalDate.parse(anchor).plusWeeks(weeks.toLong())
                    while (!n.isAfter(today)) n = n.plusWeeks(weeks.toLong())
                    j.remove("anchor")
                    n
                } else {
                    val due = runCatching { LocalDate.parse(currentNext) }.getOrNull()
                    val basis = if (due != null && due.isAfter(today)) due else today
                    basis.plusWeeks(weeks.toLong())
                }
                j.put("next", next.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }

            val hist = j.optJSONArray("hist") ?: org.json.JSONArray().also { j.put("hist", it) }
            val entry = JSONObject().apply {
                put("id", "hnative" + UUID.randomUUID().toString().replace("-", "").take(14))
                put("d", todayIso)
                put("seqn", System.currentTimeMillis())
                put("t", "clean")
                put("a", price)
                put("p", paidAmount)
                put("m", if (paidAmount > 0) j.optString("pay", "Cash") else "")
                put("n", when {
                    price <= 0 -> "Done"
                    paidAmount >= price -> "Paid"
                    else -> "Not paid"
                })
            }
            hist.put(entry)

            val oldBal = j.optDouble("bal", 0.0)
            j.put("bal", Math.round((oldBal + price - paidAmount) * 100.0) / 100.0)

            val pending = ops(context).put(JSONObject().apply {
                put("id", "op" + UUID.randomUUID().toString().replace("-", "").take(14))
                put("job", jobId)
                put("entry", entry)
            })

            val updated = root.toString()
            prefs(context).edit()
                .putString(STATE, updated)
                .putString(OPS, pending.toString())
                .putLong(REVISION, System.currentTimeMillis())
                .putBoolean(DIRTY, true)
                .commit()

            SnapshotStore.updateFromState(context, updated)
            QuickResult.DONE
        }.getOrDefault(QuickResult.FAILED)
    }
}
