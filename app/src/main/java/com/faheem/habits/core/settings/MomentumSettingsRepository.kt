package com.faheem.habits.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentumSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appSettings: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("momentum_app_settings.preferences_pb") },
        migrations = listOf(SharedPreferencesMigration(context, "momentum_app_settings"))
    )

    private val moneySettings: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("momentum_money.preferences_pb") },
        migrations = listOf(SharedPreferencesMigration(context, "momentum"))
    )

    val permissionSetupSeen: Flow<Boolean> = appSettings.data.map { it[PERMISSION_SETUP_SEEN] ?: false }

    fun moduleVisible(moduleName: String): Flow<Boolean> =
        appSettings.data.map { it[booleanPreferencesKey("module_enabled_$moduleName")] ?: true }

    val budgetMinorUnits: Flow<Long> = moneySettings.data.map { it[BUDGET_MINOR_UNITS] ?: 0L }

    fun categoryBudgetMinorUnits(category: String): Flow<Long> =
        moneySettings.data.map { it[longPreferencesKey("money_limit_${category}_minor_units")] ?: 0L }

    suspend fun setPermissionSetupSeen(value: Boolean) {
        appSettings.edit { it[PERMISSION_SETUP_SEEN] = value }
    }

    suspend fun setModuleVisible(moduleName: String, visible: Boolean) {
        appSettings.edit { it[booleanPreferencesKey("module_enabled_$moduleName")] = visible }
    }

    suspend fun setBudgetMinorUnits(value: Long) {
        moneySettings.edit { it[BUDGET_MINOR_UNITS] = value }
    }

    suspend fun setCategoryBudgetMinorUnits(category: String, value: Long) {
        moneySettings.edit { it[longPreferencesKey("money_limit_${category}_minor_units")] = value }
    }

    private companion object {
        val PERMISSION_SETUP_SEEN = booleanPreferencesKey("permission_setup_seen")
        val BUDGET_MINOR_UNITS = longPreferencesKey("budget_minor_units")
    }
}
