package com.faheem.habits.features.money

import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanPayment
import com.faheem.habits.core.data.MAX_MINOR_UNITS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyRulesTest {
    @Test
    fun budgetLimitUsesCurrentMonthAndIgnoresDeletedRows() {
        val budgets = listOf(
            CategoryBudget("Food", "2026-11", 20_000L),
            CategoryBudget("Food", "2026-10", 10_000L),
            CategoryBudget("Food", "2026-11", 5_000L, deleted = true),
            CategoryBudget(OVERALL_BUDGET_CATEGORY, "2026-11", 100_000L)
        )
        assertEquals(20_000L, budgetLimit(budgets, "Food", "2026-11"))
        assertEquals(10_000L, budgetLimit(budgets, "Food", "2026-10"))
        assertEquals(0L, budgetLimit(budgets, "Transport", "2026-11"))
        assertEquals(100_000L, budgetLimit(budgets, OVERALL_BUDGET_CATEGORY, "2026-11"))
    }

    @Test
    fun budgetProgressIsClampedAndSaturating() {
        assertEquals(0f, budgetFraction(500L, 0L))
        assertEquals(1f, budgetFraction(500L, 100L))
        assertEquals(0.5f, budgetFraction(50L, 100L))
        assertEquals(0L, budgetRemaining(100L, 150L))
        assertEquals(50L, budgetRemaining(100L, 50L))
        assertEquals(0L, budgetOverage(100L, 50L))
        assertEquals(50L, budgetOverage(100L, 150L))
    }

    @Test
    fun outstandingBalancesIgnoreDeletedPaymentsAndClampAtZero() {
        val loans = listOf(
            Loan(id = 1, person = "A", amountMinorUnits = 10_000L, lent = true, note = "", epochDay = 1L),
            Loan(id = 2, person = "B", amountMinorUnits = 7_000L, lent = false, note = "", epochDay = 1L),
            Loan(id = 3, person = "C", amountMinorUnits = 5_000L, lent = true, note = "", epochDay = 1L)
        )
        val payments = listOf(
            LoanPayment(loanId = 1, amountMinorUnits = 2_500L, epochDay = 2L),
            LoanPayment(loanId = 1, amountMinorUnits = 9_999L, epochDay = 3L, deleted = true),
            LoanPayment(loanId = 2, amountMinorUnits = 9_000L, epochDay = 2L),
            LoanPayment(loanId = 9, amountMinorUnits = 4_000L, epochDay = 2L)
        )
        val balances = outstandingByLoan(loans, payments)
        assertEquals(7_500L, balances[1])
        assertEquals(0L, balances[2])
        assertEquals(5_000L, balances[3])
        assertEquals(3, balances.size)
    }

    @Test
    fun legacyBudgetsImportUsesOverallSentinelAndCapsExtremes() {
        val rows = legacyBudgetsToRoom(
            overallMinorUnits = MAX_MINOR_UNITS + 1L,
            categoryMinorUnits = mapOf(" Food " to 2_000L, "  " to 500L, OVERALL_BUDGET_CATEGORY to 300L, "Empty" to 0L),
            monthKey = "2026-11",
            updatedAt = 42L
        )
        assertEquals(2, rows.size)
        assertEquals(OVERALL_BUDGET_CATEGORY, rows[0].category)
        assertEquals(MAX_MINOR_UNITS, rows[0].limitMinorUnits)
        assertEquals("2026-11", rows[0].monthKey)
        assertEquals(42L, rows[0].updatedAt)
        assertEquals("Food", rows[1].category)
        assertEquals(2_000L, rows[1].limitMinorUnits)
        assertTrue(rows.all { !it.deleted && it.dirty })
    }

    @Test
    fun currentMonthKeyUsesIsoYearMonth() {
        assertEquals("2026-11", currentMonthKey(LocalDate.of(2026, 11, 26)))
    }
}
