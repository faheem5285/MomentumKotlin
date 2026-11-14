package com.faheem.habits.features.network

import java.time.DayOfWeek
import java.time.LocalDate

internal enum class NetworkLimitPeriod(val label: String, val key: String) {
    DAILY("Daily", "daily"),
    WEEKLY("Weekly", "weekly"),
    MONTHLY("Monthly", "monthly");

    fun startDate(date: LocalDate = LocalDate.now()): LocalDate = when (this) {
        DAILY -> date
        WEEKLY -> date.with(DayOfWeek.MONDAY)
        MONTHLY -> date.withDayOfMonth(1)
    }

    fun periodId(date: LocalDate = LocalDate.now()): String = "$key:${startDate(date)}"

    companion object {
        fun fromKey(key: String?): NetworkLimitPeriod = entries.firstOrNull { it.key == key } ?: DAILY
    }
}
