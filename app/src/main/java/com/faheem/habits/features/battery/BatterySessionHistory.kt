package com.faheem.habits.features.battery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One completed charging session, from plug-in to unplug. */
internal data class BatteryChargeSession(
    val startedAt: Long,
    val endedAt: Long,
    val startPercent: Int,
    val endPercent: Int,
    val source: String
) {
    val gainedPercent: Int get() = (endPercent - startPercent).coerceAtLeast(0)
    val durationMinutes: Int get() = ((endedAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
}

/**
 * Small local log of charging sessions.
 *
 * Sessions are opened when Android reports ACTION_POWER_CONNECTED and closed on
 * ACTION_POWER_DISCONNECTED. Duplicate broadcasts (manifest receiver plus the
 * foreground listener) are filtered with a short time window so a single plug
 * event never creates two entries.
 */
internal object BatterySessionHistory {
    private const val PREFS = "momentum_battery_sessions"
    private const val KEY_ACTIVE = "active_session"
    private const val KEY_HISTORY = "session_history"
    private const val MAX_SESSIONS = 60
    private const val DUPLICATE_WINDOW_MS = 10_000L
    private const val MINIMUM_SESSION_MS = 2_000L

    fun start(context: Context, percent: Int, source: String) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val active = readActive(prefs)
        if (active != null && now - active.startedAt < DUPLICATE_WINDOW_MS) return
        prefs.edit().putString(
            KEY_ACTIVE,
            JSONObject().apply {
                put("startedAt", now)
                put("startPercent", percent.coerceIn(0, 100))
                put("source", source)
            }.toString()
        ).apply()
    }

    fun finish(context: Context, percent: Int) {
        val prefs = prefs(context)
        val active = readActive(prefs) ?: return
        val now = System.currentTimeMillis()
        if (now - active.startedAt < MINIMUM_SESSION_MS) {
            prefs.edit().remove(KEY_ACTIVE).apply()
            return
        }
        val session = BatteryChargeSession(
            startedAt = active.startedAt,
            endedAt = now,
            startPercent = active.startPercent,
            endPercent = percent.coerceIn(0, 100),
            source = active.source
        )
        val updated = (listOf(session) + readHistory(prefs)).take(MAX_SESSIONS)
        prefs.edit()
            .remove(KEY_ACTIVE)
            .putString(KEY_HISTORY, JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString())
            .apply()
    }

    fun recent(context: Context, limit: Int = 12): List<BatteryChargeSession> =
        readHistory(prefs(context)).take(limit)

    fun active(context: Context): Boolean = readActive(prefs(context)) != null

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_ACTIVE).remove(KEY_HISTORY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readActive(prefs: android.content.SharedPreferences): ActiveSession? =
        prefs.getString(KEY_ACTIVE, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)
                ActiveSession(
                    startedAt = json.getLong("startedAt"),
                    startPercent = json.getInt("startPercent"),
                    source = json.optString("source", "Charging")
                )
            }.getOrNull()
        }

    private fun readHistory(prefs: android.content.SharedPreferences): List<BatteryChargeSession> =
        prefs.getString(KEY_HISTORY, null)?.let { raw ->
            runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val json = array.getJSONObject(index)
                        add(
                            BatteryChargeSession(
                                startedAt = json.getLong("startedAt"),
                                endedAt = json.getLong("endedAt"),
                                startPercent = json.getInt("startPercent"),
                                endPercent = json.getInt("endPercent"),
                                source = json.optString("source", "Charging")
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        } ?: emptyList()

    private data class ActiveSession(val startedAt: Long, val startPercent: Int, val source: String)

    private fun BatteryChargeSession.toJson() = JSONObject().apply {
        put("startedAt", startedAt)
        put("endedAt", endedAt)
        put("startPercent", startPercent)
        put("endPercent", endPercent)
        put("source", source)
    }
}
