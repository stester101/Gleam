package com.gleam.windowcleaning

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Automatic recovery copies, independent of manual JSON backups.
 * - daily-YYYY-MM-DD.json: latest state of each day (rewritten at most every 10 min), last 7 days kept.
 * - before-<label>-<millis>.json: taken before a restore/reset, last 3 kept.
 * - weekly copy to Downloads/Gleam so one survives an uninstall.
 * All copies live in app-private storage (files/recovery).
 */
object RecoveryStore {
    private const val DIR = "recovery"
    private const val PREFS = "gleam_recovery"
    private const val LAST_WRITE = "lastWrite"
    private const val LAST_WEEKLY = "lastWeekly"
    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L
    private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    private const val KEEP_DAILY = 7
    private const val KEEP_BEFORE = 3

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun looksLikeData(json: String): Boolean = runCatching {
        (JSONObject(json).optJSONArray("jobs")?.length() ?: 0) > 0
    }.getOrDefault(false)

    private fun writeAtomic(file: File, text: String) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun prune(context: Context, prefix: String, keep: Int) {
        dir(context).listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    @Synchronized
    fun onSync(context: Context, json: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()
        val daily = File(dir(context), "daily-$today.json")
        if (daily.exists() && now - prefs.getLong(LAST_WRITE, 0L) < MIN_INTERVAL_MS) return
        if (!looksLikeData(json)) return
        runCatching {
            writeAtomic(daily, json)
            prefs.edit().putLong(LAST_WRITE, now).apply()
            prune(context, "daily-", KEEP_DAILY)
        }
        if (now - prefs.getLong(LAST_WEEKLY, 0L) >= WEEK_MS) {
            if (Downloads.write(context, "gleam-auto-backup-$today.json", "application/json", json)) {
                prefs.edit().putLong(LAST_WEEKLY, now).apply()
            }
        }
    }

    @Synchronized
    fun saveBefore(context: Context, label: String, json: String) {
        if (!looksLikeData(json)) return
        val safe = label.filter { it.isLetterOrDigit() || it == '-' }.take(20).ifBlank { "change" }
        runCatching {
            writeAtomic(File(dir(context), "before-$safe-${System.currentTimeMillis()}.json"), json)
            prune(context, "before-", KEEP_BEFORE)
        }
    }

    /** JSON list of copies, newest first: [{name, modified, jobs}] */
    fun list(context: Context): String {
        val out = JSONArray()
        dir(context).listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { f ->
                val jobs = runCatching {
                    JSONObject(AtomicFile(f).readFully().toString(Charsets.UTF_8))
                        .optJSONArray("jobs")?.length() ?: 0
                }.getOrDefault(0)
                out.put(JSONObject().apply {
                    put("name", f.name)
                    put("modified", f.lastModified())
                    put("jobs", jobs)
                })
            }
        return out.toString()
    }

    fun read(context: Context, name: String): String {
        val f = File(dir(context), File(name).name)
        if (!f.exists()) return ""
        return runCatching { AtomicFile(f).readFully().toString(Charsets.UTF_8) }.getOrDefault("")
    }
}
