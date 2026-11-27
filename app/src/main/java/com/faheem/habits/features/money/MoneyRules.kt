package com.faheem.habits.features.money

import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanPayment
import com.faheem.habits.core.data.MAX_MINOR_UNITS
import com.faheem.habits.core.data.saturatingSubtract
import com.faheem.habits.core.data.sumMinorUnits
import java.time.LocalDate
import java.time.YearMonth

/** Sentinel used by the Room-backed overall monthly budget. */
const val OVERALL_BUDGET_CATEGORY = "__overall__"

fun currentMonthKey(date: LocalDate = LocalDate.now()): String = YearMonth.from(date).toString()

fun budgetLimit(budgets: List<CategoryBudget>, category: String, monthKey: String): Long =
    budgets.firstOrNull { it.category == category && it.monthKey == monthKey && !it.deleted }
        ?.limitMinorUnits?.coerceIn(0, MAX_MINOR_UNITS) ?: 0L

fun budgetFraction(spentMinorUnits: Long, limitMinorUnits: Long): Float =
    if (limitMinorUnits <= 0) 0f else (spentMinorUnits.toFloat() / limitMinorUnits).coerceIn(0f, 1f)

fun budgetRemaining(limitMinorUnits: Long, spentMinorUnits: Long): Long =
    saturatingSubtract(limitMinorUnits, spentMinorUnits).coerceAtLeast(0L)

fun budgetOverage(limitMinorUnits: Long, spentMinorUnits: Long): Long =
    saturatingSubtract(spentMinorUnits, limitMinorUnits).coerceAtLeast(0L)

/** Maps every active loan to principal minus non-tombstoned payments, never below zero. */
fun outstandingByLoan(loans: List<Loan>, payments: List<LoanPayment>): Map<Int, Long> {
    val paidByLoan = payments.asSequence()
        .filter { !it.deleted }
        .groupBy { it.loanId }
        .mapValues { (_, rows) -> rows.sumMinorUnits { it.amountMinorUnits } }
    return loans.associate { loan ->
        loan.id to saturatingSubtract(loan.amountMinorUnits, paidByLoan[loan.id] ?: 0L).coerceAtLeast(0L)
    }
}

/** One-time conversion of legacy DataStore budgets into Room rows. */
fun legacyBudgetsToRoom(
    overallMinorUnits: Long?,
    categoryMinorUnits: Map<String, Long>,
    monthKey: String,
    updatedAt: Long = System.currentTimeMillis()
): List<CategoryBudget> = buildList {
    val positiveOverall = overallMinorUnits?.takeIf { it > 0 }?.coerceAtMost(MAX_MINOR_UNITS)
    if (positiveOverall != null) {
        add(CategoryBudget(OVERALL_BUDGET_CATEGORY, monthKey, positiveOverall, updatedAt = updatedAt))
    }
    categoryMinorUnits.forEach { (name, amount) ->
        val category = name.trim()
        val positive = amount.takeIf { it > 0 }?.coerceAtMost(MAX_MINOR_UNITS)
        if (category.isNotEmpty() && positive != null && category != OVERALL_BUDGET_CATEGORY) {
            add(CategoryBudget(category, monthKey, positive, updatedAt = updatedAt))
        }
    }
}
