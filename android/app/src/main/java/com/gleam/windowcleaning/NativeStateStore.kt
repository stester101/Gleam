package com.gleam.windowcleaning

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object NativeStateStore {
    private const val PREFS = "gleam_native_state"
    private const val STATE = "state"
    private const val REVISION = "revision"

    fun setFromWeb(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(STATE, json)
            .putLong(REVISION, System.currentTimeMillis())
            .apply()
    }

    fun state(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(STATE, "")
            .orEmpty()

    fun revision(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(REVISION, 0L)

    fun quickComplete(context: Context, jobId: String, paid: Boolean): Boolean {
        val raw = state(context)
        if (raw.isBlank()) return false

        return runCatching {
            val root = JSONObject(raw)
            val jobs = root.optJSONArray("jobs") ?: return false
            var job: JSONObject? = null
            for (i in 0 until jobs.length()) {
                val candidate = jobs.optJSONObject(i) ?: continue
                if (candidate.optString("id") == jobId) {
                    job = candidate
                    break
                }
            }
            val j = job ?: return false
            if (j.optString("status", "active") != "active") return false

            val today = LocalDate.now()
            val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
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
            j.put("bal", ((oldBal + price - paidAmount) * 100.0).toInt() / 100.0)

            val updated = root.toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(STATE, updated)
                .putLong(REVISION, System.currentTimeMillis())
                .apply()

            SnapshotStore.updateFromState(context, updated)
            true
        }.getOrDefault(false)
    }
}
