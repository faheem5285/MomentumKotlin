package com.faheem.habits.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentum_app_settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "momentum_app_settings")) }
)

private val Context.moneySettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "momentum_money",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, "momentum")) }
)

@Singleton
class MomentumSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appSettings = context.appSettingsDataStore
    private val moneySettings = context.moneySettingsDataStore

    val permissionSetupSeen: Flow<Boolean> = appSettings.data.map { it[PERMISSION_SETUP_SEEN] ?: false }

    fun moduleVisibility(moduleNames: List<String>): Flow<Map<String, Boolean>> =
        appSettings.data.map { preferences ->
            moduleNames.associateWith { name ->
                preferences[booleanPreferencesKey("module_enabled_$name")] ?: true
            }
        }

    val budgetMinorUnits: Flow<Long> = moneySettings.data.map { it[BUDGET_MINOR_UNITS] ?: 0L }
    val loansAccountBalanceMinorUnits: Flow<Long?> =
        moneySettings.data.map { it[LOANS_ACCOUNT_BALANCE_MINOR_UNITS] }

    fun categoryBudgetMinorUnits(category: String): Flow<Long> =
        moneySettings.data.map { it[longPreferencesKey("money_limit_${category}_minor_units")] ?: 0L }

    suspend fun setPermissionSetupSeen(value: Boolean) {
        appSettings.edit { it[PERMISSION_SETUP_SEEN] = value }
    }

    suspend fun setModuleVisible(moduleName: String, visible: Boolean) {
        appSettings.edit { it[booleanPreferencesKey("module_enabled_$moduleName")] = visible }
    }

    suspend fun showAllModules(moduleNames: List<String>) {
        appSettings.edit { preferences ->
            moduleNames.forEach { name ->
                preferences[booleanPreferencesKey("module_enabled_$name")] = true
            }
        }
    }

    suspend fun setBudgetMinorUnits(value: Long) {
        moneySettings.edit { it[BUDGET_MINOR_UNITS] = value }
    }

    suspend fun setLoansAccountBalanceMinorUnits(value: Long) {
        moneySettings.edit { it[LOANS_ACCOUNT_BALANCE_MINOR_UNITS] = value }
    }

    suspend fun setCategoryBudgetMinorUnits(category: String, value: Long) {
        moneySettings.edit { it[longPreferencesKey("money_limit_${category}_minor_units")] = value }
    }

    suspend fun clearForProjectWipe() {
        appSettings.edit { it.clear() }
        moneySettings.edit { it.clear() }
    }

    private companion object {
        val PERMISSION_SETUP_SEEN = booleanPreferencesKey("permission_setup_seen")
        val BUDGET_MINOR_UNITS = longPreferencesKey("budget_minor_units")
        val LOANS_ACCOUNT_BALANCE_MINOR_UNITS = longPreferencesKey("loans_account_balance_minor_units")
    }
}

class MomentumSettingsBackup(context: Context) {
    private val appSettings = context.appSettingsDataStore
    private val moneySettings = context.moneySettingsDataStore

    suspend fun exportPreferences(): Pair<JSONObject, JSONObject> {
        val app = appSettings.data.first().toBackupJson(
            exactKeys = setOf("permission_setup_seen"),
            prefix = "module_enabled_"
        )
        val money = moneySettings.data.first().toBackupJson(
            exactKeys = setOf("budget", "budget_minor_units"),
            prefix = "money_limit_"
        )
        return app to money
    }

    suspend fun importPreferences(settings: JSONObject?, budgets: JSONObject?, replace: Boolean) {
        appSettings.edit { preferences ->
            if (replace) preferences.removeMatching {
                it == "permission_setup_seen" || it.startsWith("module_enabled_")
            }
            settings?.putInto(preferences, setOf("permission_setup_seen"), "module_enabled_")
        }
        moneySettings.edit { preferences ->
            if (replace) preferences.removeMatching {
                it in setOf("budget", "budget_minor_units") || it.startsWith("money_limit_")
            }
            budgets?.putInto(preferences, setOf("budget", "budget_minor_units"), "money_limit_")
        }
    }
}

private fun Preferences.toBackupJson(exactKeys: Set<String>, prefix: String) = JSONObject().apply {
    asMap().forEach { (key, value) ->
        if (key.name !in exactKeys && !key.name.startsWith(prefix)) return@forEach
        when (value) {
            is Boolean, is Int, is Long, is String -> put(key.name, value)
            is Float -> put(key.name, value.toDouble())
        }
    }
}

private fun JSONObject.putInto(
    preferences: MutablePreferences,
    exactKeys: Set<String>,
    prefix: String
) {
    for (index in 0 until length()) {
        val key = names()?.getString(index)
        if (key != null && (key in exactKeys || key.startsWith(prefix))) {
            when (val value = get(key)) {
                is Boolean -> preferences[booleanPreferencesKey(key)] = value
                is Int -> preferences[intPreferencesKey(key)] = value
                is Long -> preferences[longPreferencesKey(key)] = value
                is Double -> preferences[floatPreferencesKey(key)] = value.toFloat()
                is String -> preferences[stringPreferencesKey(key)] = value
                else -> error("Unsupported backup preference value for '$key'.")
            }
        }
    }
}

private fun MutablePreferences.removeMatching(predicate: (String) -> Boolean) {
    asMap().forEach { (key, value) ->
        if (!predicate(key.name)) return@forEach
        when (value) {
            is Boolean -> remove(booleanPreferencesKey(key.name))
            is Int -> remove(intPreferencesKey(key.name))
            is Long -> remove(longPreferencesKey(key.name))
            is Float -> remove(floatPreferencesKey(key.name))
            is String -> remove(stringPreferencesKey(key.name))
        }
    }
}
