package com.faheem.habits.core.settings

import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumSettingsMigrationTest {
    @Test
    fun legacySharedPreferencesAreMigratedIntoDataStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeName = "settings_migration_test_${UUID.randomUUID()}"
        val legacy = context.getSharedPreferences(storeName, 0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        legacy.edit()
            .putBoolean("module_enabled_Habits", false)
            .putLong("money_limit_food_minor_units", 12_345L)
            .commit()

        try {
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                migrations = listOf(SharedPreferencesMigration(context, storeName)),
                produceFile = { context.preferencesDataStoreFile(storeName) }
            )
            val migrated = store.data.first()
            assertEquals(false, migrated[booleanPreferencesKey("module_enabled_Habits")])
            assertEquals(12_345L, migrated[longPreferencesKey("money_limit_food_minor_units")])
        } finally {
            scope.cancel()
            legacy.edit().clear().commit()
            context.preferencesDataStoreFile(storeName).delete()
        }
    }
}
