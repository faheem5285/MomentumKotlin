package com.faheem.habits.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import com.faheem.habits.core.data.parseMinorUnits
import com.faheem.habits.core.data.toMinorUnits
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

data class LegacyMoneyBudgets(
    val overallMinorUnits: Long?,
    val categoryMinorUnits: Map<String, Long>
)

@Singleton
class MomentumSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appSettings = context.appSettingsDataStore
    private val moneySettings = context.moneySettingsDataStore

    suspend fun clearAll() {
        appSettings.edit { it.clear() }
        moneySettings.edit { it.clear() }
    }

    val permissionSetupSeen: Flow<Boolean> = appSettings.data.map { it[PERMISSION_SETUP_SEEN] ?: false }
    val accountChoiceSeen: Flow<Boolean> = appSettings.data.map { it[ACCOUNT_CHOICE_SEEN] ?: false }

    /** Null only before any DataStore appearance key has been written. */
    val appearanceValues: Flow<AppearanceValues?> = appSettings.data.map { preferences ->
        val raw = preferences.asMap().entries.associate { (key, value) -> key.name to value }
        if (AppearanceBackupPolicy.KEYS.none { it in raw }) null
        else AppearanceBackupPolicy.fromRaw(raw)
    }

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

    /**
     * Legacy DataStore budgets are import staging only. They are read once and then
     * written to Room, which is the runtime source of truth.
     */
    suspend fun legacyBudgetsForRoom(): LegacyMoneyBudgets? {
        val preferences = moneySettings.data.first()
        if (preferences[BUDGET_ROOM_MIGRATED] == true) return null
        val entries = preferences.asMap()
        fun minorValue(name: String): Long? {
            val value = entries.entries.firstOrNull { it.key.name == name }?.value ?: return null
            return when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Float -> runCatching { value.toDouble().toMinorUnits() }.getOrNull()
                is Double -> runCatching { value.toMinorUnits() }.getOrNull()
                is String -> parseMinorUnits(value)
                else -> null
            }
        }
        val overall = minorValue("budget_minor_units") ?: minorValue("budget")
        val categories = entries.entries.mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith("money_limit_") || !name.endsWith("_minor_units")) return@mapNotNull null
            val category = name.removePrefix("money_limit_").removeSuffix("_minor_units")
            if (category.isBlank()) return@mapNotNull null
            val amount = when (value) {
                is Long -> value
                is Int -> value.toLong()
                is Float -> runCatching { value.toDouble().toMinorUnits() }.getOrNull()
                is Double -> runCatching { value.toMinorUnits() }.getOrNull()
                is String -> parseMinorUnits(value)
                else -> null
            } ?: return@mapNotNull null
            category to amount
        }.toMap()
        if (overall == null && categories.isEmpty()) {
            moneySettings.edit { it[BUDGET_ROOM_MIGRATED] = true }
            return null
        }
        return LegacyMoneyBudgets(overall, categories)
    }

    suspend fun markLegacyBudgetsMigrated() {
        moneySettings.edit { it[BUDGET_ROOM_MIGRATED] = true }
    }

    suspend fun setPermissionSetupSeen(value: Boolean) {
        appSettings.edit { it[PERMISSION_SETUP_SEEN] = value }
    }

    suspend fun setAccountChoiceSeen(value: Boolean) {
        appSettings.edit { it[ACCOUNT_CHOICE_SEEN] = value }
    }

    suspend fun setAppearance(values: AppearanceValues) {
        appSettings.edit { preferences ->
            AppearanceBackupPolicy.toRaw(values).forEach { (key, value) ->
                when (value) {
                    is Boolean -> preferences[booleanPreferencesKey(key)] = value
                    is String -> preferences[stringPreferencesKey(key)] = value
                }
            }
        }
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
        val ACCOUNT_CHOICE_SEEN = booleanPreferencesKey("account_choice_seen")
        val BUDGET_MINOR_UNITS = longPreferencesKey("budget_minor_units")
        val BUDGET_ROOM_MIGRATED = booleanPreferencesKey("budget_migrated_to_room")
        val LOANS_ACCOUNT_BALANCE_MINOR_UNITS = longPreferencesKey("loans_account_balance_minor_units")
    }
}

class MomentumSettingsBackup(context: Context) {
    private val appSettings = context.appSettingsDataStore
    private val moneySettings = context.moneySettingsDataStore

    suspend fun exportPreferences(): Pair<JSONObject, JSONObject> {
        val app = appSettings.data.first().toBackupJson(
            exactKeys = setOf("permission_setup_seen"),
            prefixes = setOf("module_enabled_", AppearanceBackupPolicy.PREFIX)
        )
        val money = moneySettings.data.first().toBackupJson(
            exactKeys = setOf("budget", "budget_minor_units"),
            prefixes = setOf("money_limit_")
        )
        return app to money
    }

    suspend fun importPreferences(settings: JSONObject?, budgets: JSONObject?, replace: Boolean) {
        appSettings.edit { preferences ->
            if (replace) preferences.removeMatching {
                it == "permission_setup_seen" || it.startsWith("module_enabled_") ||
                    it.startsWith(AppearanceBackupPolicy.PREFIX)
            }
            settings?.putInto(
                preferences,
                exactKeys = setOf("permission_setup_seen"),
                prefixes = setOf("module_enabled_", AppearanceBackupPolicy.PREFIX)
            )
        }
        moneySettings.edit { preferences ->
            if (replace) {
                preferences.removeMatching {
                    it in setOf("budget", "budget_minor_units") || it.startsWith("money_limit_")
                }
                // Re-evaluate legacy budgets after a replace restore; Room rows are restored separately.
                preferences.remove(booleanPreferencesKey("budget_migrated_to_room"))
            }
            budgets?.putInto(preferences, setOf("budget", "budget_minor_units"), setOf("money_limit_"))
        }
    }
}

private fun Preferences.toBackupJson(exactKeys: Set<String>, prefixes: Set<String>) = JSONObject().apply {
    asMap().forEach { (key, value) ->
        if (key.name !in exactKeys && prefixes.none { key.name.startsWith(it) }) return@forEach
        when (value) {
            is Boolean, is Int, is Long, is String -> put(key.name, value)
            is Float -> put(key.name, value.toDouble())
        }
    }
}

private fun JSONObject.putInto(
    preferences: MutablePreferences,
    exactKeys: Set<String>,
    prefixes: Set<String>
) {
    for (index in 0 until length()) {
        val key = names()?.getString(index)
        if (key != null && (key in exactKeys || prefixes.any { key.startsWith(it) })) {
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
