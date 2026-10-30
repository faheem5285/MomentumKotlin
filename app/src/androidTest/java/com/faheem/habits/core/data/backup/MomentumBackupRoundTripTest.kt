package com.faheem.habits.core.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.Completion
import com.faheem.habits.core.data.Expense
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.ToDoItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumBackupRoundTripTest {
    @Test
    fun exportImportRoundTripAndRepeatedMergeAreUuidIdempotent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val habitId = db.habitDao().insertHabit(Habit(name = "Round trip habit")).toInt()
            db.habitDao().insertCompletion(Completion(habitId, 20_000L))
            db.expenseDao().insert(Expense(title = "Coffee", amountMinorUnits = 275L, category = "Food", epochDay = 20_000L))
            db.toDoDao().insert(ToDoItem(title = "Review backup", dueEpochDay = 20_001L))

            val snapshot = MomentumBackup.exportBytes(context, db)
            db.toDoDao().deleteAll()
            db.expenseDao().deleteAll()
            db.habitDao().deleteAllCompletions()
            db.habitDao().deleteAllHabits()

            MomentumBackup.restoreBytes(context, snapshot, replace = false, db = db)
            MomentumBackup.restoreBytes(context, snapshot, replace = false, db = db)

            assertEquals(1, db.habitDao().getAllForBackup().size)
            assertEquals(1, db.habitDao().getCompletionsForBackup().size)
            assertEquals(1, db.expenseDao().getAllForBackup().size)
            assertEquals(275L, db.expenseDao().getAllForBackup().single().amountMinorUnits)
            assertEquals(1, db.toDoDao().getAllForBackup().size)
            assertTrue(String(snapshot, Charsets.UTF_8).contains("\"schemaVersion\": 11"))
        } finally {
            db.close()
        }
    }
}
