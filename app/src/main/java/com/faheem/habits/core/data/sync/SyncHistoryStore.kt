package com.faheem.habits.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class SyncHistoryEvent(val timeMillis: Long, val title: String, val detail: String)

object SyncHistoryStore {
    private const val PREFS = "momentum_sync_history"
    private const val KEY_EVENTS = "events"
    private const val LIMIT = 10

    fun record(context: Context, title: String, detail: String) {
        val prefs = preferences(context)
        val updated = (listOf(SyncHistoryEvent(System.currentTimeMillis(), title, detail)) + read(prefs)).take(LIMIT)
        prefs.edit().putString(KEY_EVENTS, JSONArray().apply {
            updated.forEach { event ->
                put(JSONObject().put("time", event.timeMillis).put("title", event.title).put("detail", event.detail))
            }
        }.toString()).apply()
    }

    fun observe(context: Context): Flow<List<SyncHistoryEvent>> = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_EVENTS) trySend(read(prefs))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun read(prefs: SharedPreferences): List<SyncHistoryEvent> =
        prefs.getString(KEY_EVENTS, null)?.let { raw ->
            runCatching {
                val rows = JSONArray(raw)
                buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        add(SyncHistoryEvent(row.getLong("time"), row.getString("title"), row.getString("detail")))
                    }
                }
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
