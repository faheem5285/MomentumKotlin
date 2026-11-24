package com.faheem.habits.core.data.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class SyncModule(val label: String, val description: String, val prefKey: String) {
    Habits("Habits", "Habits, completions, skips and reminders", "sync_habits"),
    ToDos("To-dos", "Tasks and their due dates", "sync_todos"),
    Money("Money", "Expenses and category budgets", "sync_money"),
    Loans("Loans", "People, loans, payments and attachments", "sync_loans"),
    Contacts("Contacts", "Your saved contacts", "sync_contacts"),
    Files("Files", "File records and cloud file copies", "sync_files"),
    LifeRules("Life rules", "Rules and attached file records", "sync_life_rules"),
    Reminders("Reminders", "Custom reminders and notifications", "sync_reminders"),
    Vault("Vault", "Encrypted vault records", "sync_vault")
}

object SyncModuleSettings {
    private const val PREFS = "momentum_cloud_sync_settings"

    fun isEnabled(context: Context, module: SyncModule): Boolean = preferences(context).getBoolean(module.prefKey, true)

    fun setEnabled(context: Context, module: SyncModule, enabled: Boolean) {
        preferences(context).edit().putBoolean(module.prefKey, enabled).apply()
    }

    fun observe(context: Context): Flow<Map<SyncModule, Boolean>> = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot(prefs)) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun snapshot(prefs: SharedPreferences): Map<SyncModule, Boolean> =
        SyncModule.entries.associateWith { prefs.getBoolean(it.prefKey, true) }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
