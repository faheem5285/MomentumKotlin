package com.faheem.habits.core.data.sync

import android.content.Context
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "momentum_sync_identity"
    private const val KEY = "device_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        check(prefs.edit().putString(KEY, id).commit()) { "Could not persist sync device identity." }
        return id
    }
}

