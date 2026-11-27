package com.faheem.habits.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncUpsertPreservesChildrenTest {
    @Test
    fun habitSyncUpsertPreservesCompletionsRemindersAndSkips() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.habitDao()
            val habitId = dao.insertHabit(Habit(name = "Original", uuid = "habit-uuid")).toInt()
            dao.insertCompletion(Completion(habitId, 100L, uuid = "completion-uuid"))
            dao.upsertSkip(HabitSkip(habitId, 100L, uuid = "skip-uuid"))
            dao.insertReminder(HabitReminder(habitId = habitId, hour = 8, minute = 0, uuid = "reminder-uuid"))

            dao.upsertHabit(
                Habit(id = 0, name = "Synced", uuid = "habit-uuid", updatedAt = 200L)
            )

            val habit = dao.getAllForBackup().single()
            assertEquals(habitId, habit.id)
            assertEquals("Synced", habit.name)
            assertEquals(1, dao.getCompletions().first().size)
            assertEquals(1, dao.getSkips().first().size)
            assertEquals(1, dao.getReminders().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun loanSyncUpsertPreservesPaymentsAndAttachments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.loanDao()
            val personId = dao.insertPerson(LoanPerson(name = "Friend", uuid = "person-uuid")).toInt()
            val loanId = dao.insert(
                Loan(
                    person = "Friend", amountMinorUnits = 10_000L, lent = true,
                    note = "Original", epochDay = 100L, uuid = "loan-uuid", personId = personId
                )
            ).toInt()
            dao.insertPayment(
                LoanPayment(loanId = loanId, amountMinorUnits = 500L, epochDay = 101L, uuid = "payment-uuid")
            )
            dao.insertAttachment(
                LoanAttachment(loanId = loanId, name = "Receipt", uri = "content://receipt", uuid = "attachment-uuid")
            )

            dao.upsertLoan(
                Loan(
                    id = 0, person = "Friend", amountMinorUnits = 12_000L, lent = true,
                    note = "Synced", epochDay = 100L, uuid = "loan-uuid", updatedAt = 200L,
                    personId = personId
                )
            )

            val loan = dao.getAllForBackup().single()
            assertEquals(loanId, loan.id)
            assertEquals(12_000L, loan.amountMinorUnits)
            assertEquals(1, dao.getPayments(loanId).first().size)
            assertEquals(1, dao.getAttachments(loanId).first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun lifeRuleSyncUpsertPreservesFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.lifeRuleDao()
            val ruleId = dao.insert(LifeRule(title = "Original", uuid = "rule-uuid")).toInt()
            dao.insertFile(
                LifeRuleFile(ruleId = ruleId, name = "Notes", uri = "content://notes", uuid = "file-uuid")
            )

            dao.upsertForSync(LifeRule(id = 0, title = "Synced", uuid = "rule-uuid", updatedAt = 200L))

            val rule = dao.getAllForBackup().single()
            assertEquals(ruleId, rule.id)
            assertEquals("Synced", rule.title)
            assertEquals(1, dao.getFiles(ruleId).first().size)
        } finally {
            db.close()
        }
    }
}
