package com.faheem.habits.features.money.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.settings.MomentumSettingsBackup
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.features.money.OVERALL_BUDGET_CATEGORY
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseRepositoryMigrationTest {
    @Test
    fun legacyDataStoreBudgetsAreImportedIntoRoomOnlyOnce() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = MomentumSettingsRepository(context)
        val settingsBackup = MomentumSettingsBackup(context)
        val original = settingsBackup.exportPreferences()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = ExpenseRepository(db, settings)
        try {
            settingsBackup.importPreferences(JSONObject(), JSONObject(), replace = true)
            settings.setBudgetMinorUnits(150_000L)
            settings.setCategoryBudgetMinorUnits("MigrationFood", 20_000L)

            repository.migrateLegacyBudgets()
            val imported = db.expenseDao().getAllBudgetsForSync()
            assertEquals(2, imported.size)
            assertEquals(
                150_000L,
                imported.single { it.category == OVERALL_BUDGET_CATEGORY }.limitMinorUnits
            )
            assertEquals(20_000L, imported.single { it.category == "MigrationFood" }.limitMinorUnits)

            settings.setBudgetMinorUnits(999L)
            settings.setCategoryBudgetMinorUnits("MigrationFood", 9_999L)
            repository.migrateLegacyBudgets()
            assertEquals(2, db.expenseDao().getAllBudgetsForSync().size)
            assertEquals(
                150_000L,
                db.expenseDao().getAllBudgetsForSync()
                    .single { it.category == OVERALL_BUDGET_CATEGORY }.limitMinorUnits
            )
        } finally {
            settingsBackup.importPreferences(original.first, original.second, replace = true)
            db.close()
        }
    }
}
