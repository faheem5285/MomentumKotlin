package com.faheem.habits.features.phase7

import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Completion
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanPayment
import com.faheem.habits.features.habits.computeStreaks
import com.faheem.habits.features.loans.data.isValidLoanPayment
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase7LogicTest {
    @Test
    fun flexibleHabitSchedulesAreEvaluated() {
        val day = LocalDate.of(2026, 10, 31).toEpochDay()
        val weekly = Habit(1, "Weekly", scheduleType = "times_per_week", frequencyPerWeek = 3, createdEpochDay = day)
        assertTrue(isHabitDue(weekly, day))
        val interval = weekly.copy(scheduleType = "every_n_days", everyNDays = 2)
        assertTrue(isHabitDue(interval, day + 2))
    }

    @Test
    fun streakFreezePreservesCurrentAndBestStreakWithoutAddingCheckIn() {
        val monday = LocalDate.of(2026, 10, 26).toEpochDay()
        val completed = setOf(monday, monday + 2)
        val result = computeStreaks(completed, 127, monday + 2, setOf(monday + 1))

        assertEquals(2, result.current)
        assertEquals(2, result.best)
    }

    @Test
    fun anUnfrozenMissedDayStillBreaksTheStreak() {
        val monday = LocalDate.of(2026, 10, 26).toEpochDay()
        val completed = setOf(monday, monday + 2)
        val result = computeStreaks(completed, 127, monday + 2)

        assertEquals(1, result.current)
        assertEquals(1, result.best)
    }

    @Test
    fun quickAddParsesRelativeDateAndTime() {
        val parsed = parseQuickTask("call mom tomorrow 18:00", LocalDate.of(2026, 10, 31))
        assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), parsed.dueEpochDay)
        assertEquals("call mom", parsed.title)
        assertTrue(parsed.dueEpochMillis != null)
    }

    @Test
    fun budgetAndLoanCalculationsWarnAtThresholds() {
        val status = budgetStatus(800, CategoryBudget("Food", "2026-10", 1000))
        assertTrue(status.warning)
        val loan = Loan(4, "A", 1000, true, "", 1)
        assertEquals(400, loanRemaining(loan, listOf(LoanPayment(loanId = 4, amountMinorUnits = 600, epochDay = 1))))
    }

    @Test
    fun loanPaymentsCannotBeZeroNegativeOrExceedTheRemainingBalance() {
        assertTrue(isValidLoanPayment(totalMinorUnits = 1000, paidMinorUnits = 400, paymentMinorUnits = 600))
        assertTrue(!isValidLoanPayment(totalMinorUnits = 1000, paidMinorUnits = 400, paymentMinorUnits = 601))
        assertTrue(!isValidLoanPayment(totalMinorUnits = 1000, paidMinorUnits = 400, paymentMinorUnits = 0))
        assertTrue(!isValidLoanPayment(totalMinorUnits = 1000, paidMinorUnits = 400, paymentMinorUnits = -1))
    }

    @Test
    fun loanRemainingIgnoresOtherLoansAndClampsOverpayment() {
        val loan = Loan(4, "A", 1000, true, "", 1)
        val payments = listOf(
            LoanPayment(loanId = 4, amountMinorUnits = 250, epochDay = 1),
            LoanPayment(loanId = 9, amountMinorUnits = 900, epochDay = 1)
        )

        assertEquals(750, loanRemaining(loan, payments))
        assertEquals(0, loanRemaining(loan, listOf(LoanPayment(loanId = 4, amountMinorUnits = 1200, epochDay = 2))))
    }

    @Test
    fun insightsFindBestDayAndTimeBuckets() {
        val monday = LocalDate.of(2026, 10, 26).toEpochDay()
        val completions = listOf(
            Completion(1, monday),
            Completion(1, monday + 7, completedAtEpochMillis = 9L * 60 * 60 * 1000)
        )
        assertEquals(DayOfWeek.MONDAY, bestCompletionDay(completions))
        assertEquals(1, timeOfDayPattern(completions, java.time.ZoneOffset.UTC)[2])
    }
}
