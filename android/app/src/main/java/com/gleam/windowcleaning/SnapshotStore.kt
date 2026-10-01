package com.gleam.windowcleaning

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

data class GleamSnapshot(
    val todayCount: Int = 0,
    val todayAddresses: List<String> = emptyList(),
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

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt("todayCount", todayJobs.size)
                .putString("today1", todayJobs.getOrNull(0)?.addr.orEmpty())
                .putString("today2", todayJobs.getOrNull(1)?.addr.orEmpty())
                .putString("today3", todayJobs.getOrNull(2)?.addr.orEmpty())
                .putInt("tomorrowCount", tomorrowJobs.size)
                .putString("tomorrowRounds", tomorrowRounds)
                .putInt("leftBehind", leftBehind)
                .apply()
        }
    }

    fun read(context: Context): GleamSnapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return GleamSnapshot(
            todayCount = p.getInt("todayCount", 0),
            todayAddresses = listOf(
                p.getString("today1", "").orEmpty(),
                p.getString("today2", "").orEmpty(),
                p.getString("today3", "").orEmpty()
            ).filter { it.isNotBlank() },
            tomorrowCount = p.getInt("tomorrowCount", 0),
            tomorrowRounds = p.getString("tomorrowRounds", "").orEmpty(),
            leftBehind = p.getInt("leftBehind", 0)
        )
    }
}
