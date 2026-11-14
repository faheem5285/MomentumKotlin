package com.faheem.habits.features.phase7

import com.faheem.habits.core.data.CategoryBudget
import com.faheem.habits.core.data.Completion
import com.faheem.habits.core.data.Habit
import com.faheem.habits.core.data.Loan
import com.faheem.habits.core.data.LoanPayment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun isHabitDue(habit: Habit, epochDay: Long, completedDays: Set<Long> = emptySet()): Boolean {
    if (habit.archived || epochDay < habit.createdEpochDay) return false
    return when (habit.scheduleType) {
        "times_per_week" -> {
            val weekStart = epochDay - (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)
            completedDays.count { it in weekStart..(weekStart + 6) } < habit.frequencyPerWeek
        }
        "every_n_days" -> (epochDay - habit.createdEpochDay) % habit.everyNDays.coerceAtLeast(1) == 0L
        else -> ((habit.daysMask shr (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)) and 1) == 1
    }
}

fun completionRate(completions: List<Completion>, scheduledDays: Int): Int =
    if (scheduledDays <= 0) 0 else (completions.size * 100 / scheduledDays).coerceIn(0, 100)

fun bestCompletionDay(completions: List<Completion>): DayOfWeek? =
    completions.groupingBy { LocalDate.ofEpochDay(it.epochDay).dayOfWeek }
        .eachCount().maxByOrNull { it.value }?.key

fun timeOfDayPattern(completions: List<Completion>, zone: ZoneId = ZoneId.systemDefault()): Map<Int, Int> =
    completions.filter { it.completedAtEpochMillis > 0 }
        .groupingBy {
            LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(it.completedAtEpochMillis),
                zone
            ).hour / 4
        }.eachCount()

data class QuickTask(
    val title: String,
    val dueEpochDay: Long,
    val dueEpochMillis: Long? = null
)

fun parseQuickTask(input: String, today: LocalDate = LocalDate.now()): QuickTask {
    val text = input.trim()
    require(text.isNotBlank()) { "Task title cannot be empty" }
    val timeMatch = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(text)
    val lower = text.lowercase()
    val date = when {
        "tomorrow" in lower -> today.plusDays(1)
        "today" in lower -> today
        else -> today
    }
    val cleanTitle = text
        .replace(Regex("""\btomorrow\b|\btoday\b""", RegexOption.IGNORE_CASE), "")
        .replace(timeMatch?.value.orEmpty(), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    val millis = timeMatch?.let {
        val time = LocalTime.parse(it.value, DateTimeFormatter.ofPattern("H:mm"))
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return QuickTask(cleanTitle.ifBlank { text }, date.toEpochDay(), millis)
}

data class BudgetStatus(val spentMinorUnits: Long, val limitMinorUnits: Long, val percent: Int) {
    val warning: Boolean get() = percent >= 80
    val exceeded: Boolean get() = percent >= 100
}

fun budgetStatus(expensesMinorUnits: Long, budget: CategoryBudget?): BudgetStatus =
    if (budget == null || budget.limitMinorUnits <= 0) {
        BudgetStatus(expensesMinorUnits, budget?.limitMinorUnits ?: 0, 0)
    } else {
        BudgetStatus(
            expensesMinorUnits,
            budget.limitMinorUnits,
            ((expensesMinorUnits * 100) / budget.limitMinorUnits).coerceAtLeast(0).toInt()
        )
    }

fun loanRemaining(loan: Loan, payments: List<LoanPayment>): Long =
    (loan.amountMinorUnits - payments.filter { it.loanId == loan.id }.sumOf { it.amountMinorUnits }).coerceAtLeast(0)

fun loanReminderText(loan: Loan, remainingMinorUnits: Long): String {
    val action = if (loan.lent) "repay" else "return"
    return "Hi ${loan.person}, just a gentle reminder about the ${action} of $remainingMinorUnits. No rush - please let me know when works for you."
}

fun monthKey(date: LocalDate = LocalDate.now()): String = YearMonth.from(date).toString()
