package com.faheem.habits.core.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Completion
import com.faheem.habits.core.data.Contact
import com.faheem.habits.core.data.Expense
import com.faheem.habits.core.data.FileItem
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.HabitReminder
import com.faheem.habits.core.data.HabitSkip
import com.faheem.habits.core.data.LifeRule
import com.faheem.habits.core.data.LifeRuleFile
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanAttachment
import com.faheem.habits.core.data.LoanPayment
import com.faheem.habits.core.data.LoanPerson
import com.faheem.habits.core.data.ToDoItem
import com.faheem.habits.core.data.CustomNotification
import com.faheem.habits.core.settings.MomentumSettingsBackup
import com.faheem.habits.core.settings.MomentumSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MomentumBackupRoundTripTest {
    @Test
    fun previousBackupSchemaRestoresWithSafeDefaults() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val legacyHabit = JSONObject()
                .put("uuid", UUID.randomUUID().toString())
                .put("updatedAt", 100L)
                .put("deleted", false)
                .put("dirty", true)
                .put("name", "Legacy habit")
                .put("createdEpochDay", 20_000L)
            val backup = JSONObject()
                .put("format", "momentum-backup")
                .put("formatVersion", 2)
                .put("schemaVersion", 13)
                .put("exportedAt", System.currentTimeMillis())
                .put("habits", JSONArray().put(legacyHabit))
                .put("completions", JSONArray())
                .put("reminders", JSONArray())
                .put("expenses", JSONArray())
                .put("loans", JSONArray())
                .put("files", JSONArray())
                .put("todos", JSONArray())
                .put("budgets", JSONObject())
                .put("settings", JSONObject())

            MomentumBackup.restoreBytes(context, backup.toString().toByteArray(), replace = true, db = db)

            val restored = db.habitDao().getAllForBackup().single()
            assertEquals("Legacy habit", restored.name)
            assertEquals("specific_days", restored.scheduleType)
            assertEquals(1, restored.targetQuantity)
            assertTrue(db.contactDao().getAllForBackup().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun failedReplacePreservesDatabaseAndExportedPreferences() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = MomentumSettingsRepository(context)
        val settingsBackup = MomentumSettingsBackup(context)
        val originalSettings = settingsBackup.exportPreferences()
        try {
            db.habitDao().insertHabit(Habit(name = "Keep this habit"))
            settings.setModuleVisible("backup_restore_test", true)
            settings.setCategoryBudgetMinorUnits("backup_restore_test", 500L)
            val invalidHabit = JSONObject()
                .put("uuid", UUID.randomUUID().toString())
                .put("name", "")
            val backup = JSONObject()
                .put("format", "momentum-backup")
                .put("formatVersion", 2)
                .put("schemaVersion", 13)
                .put("exportedAt", System.currentTimeMillis())
                .put("habits", JSONArray().put(invalidHabit))
                .put("completions", JSONArray())
                .put("reminders", JSONArray())
                .put("expenses", JSONArray())
                .put("loans", JSONArray())
                .put("files", JSONArray())
                .put("todos", JSONArray())

            var failed = false
            try {
                MomentumBackup.restoreBytes(context, backup.toString().toByteArray(), replace = true, db = db)
            } catch (_: IllegalArgumentException) {
                failed = true
            }

            assertTrue(failed)
            assertEquals("Keep this habit", db.habitDao().getAllForBackup().single().name)
            val preserved = settingsBackup.exportPreferences()
            assertEquals(true, preserved.first.getBoolean("module_enabled_backup_restore_test"))
            assertEquals(500L, preserved.second.getLong("money_limit_backup_restore_test_minor_units"))
        } finally {
            settingsBackup.importPreferences(originalSettings.first, originalSettings.second, replace = true)
            db.close()
        }
    }

    @Test
    fun exportImportRoundTripAndRepeatedMergeAreUuidIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = MomentumSettingsRepository(context)
        val settingsBackup = MomentumSettingsBackup(context)
        val originalSettings = settingsBackup.exportPreferences()
        try {
            settings.setModuleVisible("backup_round_trip_test", false)
            settings.setCategoryBudgetMinorUnits("backup_round_trip_test", 1_234L)
            val habitId = db.habitDao().insertHabit(
                Habit(name = "Round trip habit", scheduleType = "times_per_week", frequencyPerWeek = 4,
                    targetQuantity = 3, quantityUnit = "pages", timerMinutes = 15, category = "Health",
                    streakFreezes = 2)
            ).toInt()
            db.habitDao().insertCompletion(
                Completion(habitId, 20_000L, note = "Felt good", quantity = 2, durationSeconds = 600L,
                    completedAtEpochMillis = 1_800_000L)
            )
            db.habitDao().upsertSkip(HabitSkip(habitId, 20_001L, reason = "Travel"))
            db.habitDao().insertReminder(HabitReminder(habitId = habitId, hour = 8, minute = 30, daysMask = 31))
            db.expenseDao().insert(
                Expense(title = "Coffee", amountMinorUnits = 275L, category = "Food", epochDay = 20_000L,
                    account = "Card", recurringRule = "monthly", categoryIcon = "coffee", categoryColor = 3)
            )
            db.expenseDao().upsertBudget(CategoryBudget("Food", "2026-11", 20_000L))

            val personId = db.loanDao().insertPerson(LoanPerson(name = "Alex", phone = "555-0100")).toInt()
            val contactId = db.contactDao().insert(Contact(name = "Casey", email = "casey@example.test"))
            assertTrue(contactId > 0)
            val loanId = db.loanDao().insert(
                Loan(person = "Alex", amountMinorUnits = 5_000L, lent = true, note = "Trip",
                    epochDay = 20_000L, dueEpochDay = 20_030L, title = "Travel loan",
                    status = "pending", personId = personId)
            ).toInt()
            db.loanDao().insertPayment(
                LoanPayment(loanId = loanId, amountMinorUnits = 1_000L, epochDay = 20_010L, note = "First payment")
            )
            db.loanDao().insertAttachment(
                LoanAttachment(loanId = loanId, name = "Receipt", uri = "content://example/receipt", mime = "image/jpeg")
            )

            db.fileDao().insert(
                FileItem(name = "Notes.txt", uri = "content://example/notes", mime = "text/plain",
                    addedEpochDay = 20_000L, tags = "work,notes", folder = "Work",
                    lastAccessedEpochDay = 20_001L)
            )
            val parentTaskId = db.toDoDao().insert(
                ToDoItem(title = "Review backup", dueEpochDay = 20_001L, repeatRule = "weekly",
                    project = "Personal", labels = "important", sortOrder = 12L)
            ).toInt()
            db.toDoDao().insert(
                ToDoItem(title = "Check restore", dueEpochDay = 20_002L, parentId = parentTaskId)
            )
            val ruleId = db.lifeRuleDao().insert(LifeRule(title = "Keep learning", pinned = true)).toInt()
            db.lifeRuleDao().insertFile(
                LifeRuleFile(ruleId = ruleId, name = "Reference", uri = "content://example/reference", mime = "application/pdf",
                    folderPath = "Reading")
            )
            db.customNotificationDao().insert(
                CustomNotification(title = "Weekly review", repeatMode = "weekly", hour = 18, minute = 0)
            )

            val snapshot = MomentumBackup.exportBytes(context, db)
            assertTrue(String(snapshot, Charsets.UTF_8).contains("\"schemaVersion\": 20"))
            val preferenceBackup = JSONObject(String(snapshot, Charsets.UTF_8))
            assertEquals(
                false,
                preferenceBackup.getJSONObject("settings").getBoolean("module_enabled_backup_round_trip_test")
            )
            assertEquals(
                1_234L,
                preferenceBackup.getJSONObject("budgets").getLong("money_limit_backup_round_trip_test_minor_units")
            )
            settings.setModuleVisible("backup_round_trip_test", true)
            settings.setCategoryBudgetMinorUnits("backup_round_trip_test", 9_999L)
            MomentumBackup.restoreBytes(context, snapshot, replace = true, db = db)
            val restoredPreferences = settingsBackup.exportPreferences()
            assertEquals(
                false,
                restoredPreferences.first.getBoolean("module_enabled_backup_round_trip_test")
            )
            assertEquals(
                1_234L,
                restoredPreferences.second.getLong("money_limit_backup_round_trip_test_minor_units")
            )
            MomentumBackup.restoreBytes(context, snapshot, replace = false, db = db)
            MomentumBackup.restoreBytes(context, snapshot, replace = false, db = db)

            assertEquals(1, db.habitDao().getAllForBackup().size)
            assertEquals(1, db.habitDao().getCompletionsForBackup().size)
            assertEquals("Felt good", db.habitDao().getCompletionsForBackup().single().note)
            assertEquals("times_per_week", db.habitDao().getAllForBackup().single().scheduleType)
            assertEquals(1, db.habitDao().getAllSkipsForSync().size)
            assertEquals(1, db.habitDao().getRemindersForBackup().size)
            assertEquals(1, db.expenseDao().getAllForBackup().size)
            assertEquals(275L, db.expenseDao().getAllForBackup().single().amountMinorUnits)
            assertEquals("Card", db.expenseDao().getAllForBackup().single().account)
            assertEquals(1, db.expenseDao().getAllBudgetsForSync().size)
            assertEquals(1, db.loanDao().getAllPeopleForSync().size)
            assertEquals(1, db.contactDao().getAllForBackup().size)
            assertEquals(1, db.loanDao().getAllForBackup().size)
            assertEquals(20_030L, db.loanDao().getAllForBackup().single().dueEpochDay)
            assertEquals(1, db.loanDao().getAllPaymentsForSync().size)
            assertEquals(1, db.loanDao().getAllAttachmentsForSync().size)
            assertEquals("work,notes", db.fileDao().getAllForBackup().single().tags)
            val restoredTasks = db.toDoDao().getAllForBackup()
            assertEquals(2, restoredTasks.size)
            assertTrue(restoredTasks.single { it.title == "Check restore" }.parentId != null)
            assertEquals(1, db.lifeRuleDao().getAllForBackup().size)
            assertEquals(1, db.lifeRuleDao().getAllFilesForSync().size)
            assertEquals(1, db.customNotificationDao().getAllForBackup().size)
        } finally {
            settingsBackup.importPreferences(originalSettings.first, originalSettings.second, replace = true)
            db.close()
        }
    }
}
