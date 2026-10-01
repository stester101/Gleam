package com.gleam.windowcleaning

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

data class SnapshotJob(
    val id: String,
    val address: String,
    val price: Double
)

data class GleamSnapshot(
    val todayCount: Int = 0,
    val todayJobs: List<SnapshotJob> = emptyList(),
    val tomorrowCount: Int = 0,
    val tomorrowRounds: String = "",
    val leftBehind: Int = 0
)

object SnapshotStore {
    private const val PREFS = "gleam_native"

    fun updateFromState(context: Context, stateJson: String) {
        runCatching {
            val state = JSONObject(stateJson)
            val jobs = state.optJSONArray("jobs") ?: return
            val roundsJson = state.optJSONArray("rounds")
            val rounds = mutableMapOf<String, String>()
            if (roundsJson != null) {
                for (i in 0 until roundsJson.length()) {
                    val r = roundsJson.optJSONObject(i) ?: continue
                    rounds[r.optString("id")] = r.optString("name", "Unassigned")
                }
            }

            data class Job(
                val id: String,
                val addr: String,
                val price: Double,
                val next: String,
                val roundId: String,
                val seq: Int,
                val active: Boolean
            )

            val parsed = mutableListOf<Job>()
            for (i in 0 until jobs.length()) {
                val j = jobs.optJSONObject(i) ?: continue
                parsed += Job(
                    id = j.optString("id"),
                    addr = j.optString("addr").ifBlank { j.optString("name", "Customer") },
                    price = j.optDouble("price", 0.0),
                    next = j.optString("next"),
                    roundId = j.optString("roundId"),
                    seq = j.optInt("seq", 999),
                    active = j.optString("status", "active") == "active"
                )
            }

            val today = LocalDate.now().toString()
            val tomorrow = LocalDate.now().plusDays(1).toString()
            val dayOrders = state.optJSONObject("dayOrders")

            fun orderedFor(date: String): List<Job> {
                val explicit = mutableMapOf<String, Int>()
                val arr = dayOrders?.optJSONArray(date)
                if (arr != null) {
                    for (i in 0 until arr.length()) explicit[arr.optString(i)] = i
                }
                return parsed
                    .filter { it.active && it.next == date }
                    .sortedWith(
                        compareBy<Job> { explicit[it.id] ?: Int.MAX_VALUE }
                            .thenBy { it.roundId }
                            .thenBy { it.seq }
                    )
            }

            val todayJobs = orderedFor(today)
            val tomorrowJobs = orderedFor(tomorrow)
            val tomorrowRounds = tomorrowJobs
                .mapNotNull { rounds[it.roundId] }
                .distinct()
                .take(3)
                .joinToString(" · ")

            val leftBehind = parsed.count {
                it.active && it.next.isNotBlank() && it.next < today
            }

            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("todayCount", todayJobs.size)
                .putInt("tomorrowCount", tomorrowJobs.size)
                .putString("tomorrowRounds", tomorrowRounds)
                .putInt("leftBehind", leftBehind)

            for (i in 0..2) {
                val j = todayJobs.getOrNull(i)
                edit.putString("todayId${i + 1}", j?.id.orEmpty())
                edit.putString("today${i + 1}", j?.addr.orEmpty())
                edit.putLong("todayPrice${i + 1}", java.lang.Double.doubleToRawLongBits(j?.price ?: 0.0))
            }
            edit.apply()
        }
    }

    fun read(context: Context): GleamSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val jobs = (1..3).mapNotNull { i ->
            val id = p.getString("todayId$i", "").orEmpty()
            val address = p.getString("today$i", "").orEmpty()
            if (id.isBlank() || address.isBlank()) null else SnapshotJob(
                id = id,
                address = address,
                price = java.lang.Double.longBitsToDouble(p.getLong("todayPrice$i", 0L))
            )
        }
        return GleamSnapshot(
            todayCount = p.getInt("todayCount", 0),
            todayJobs = jobs,
            tomorrowCount = p.getInt("tomorrowCount", 0),
            tomorrowRounds = p.getString("tomorrowRounds", "").orEmpty(),
            leftBehind = p.getInt("leftBehind", 0)
        )
    }
}
