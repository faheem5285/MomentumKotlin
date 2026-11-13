package com.faheem.habits.features.screen_time

import android.content.Context
import android.app.usage.UsageStatsManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

internal const val SCREEN_TIME_PREFS = "screen_time"
internal const val PREF_SCREEN_LIMIT = "daily_screen_limit_minutes"
internal const val PREF_SCREEN_LIMIT_PERIOD = "screen_time_limit_period"
internal const val PREF_SCREEN_ALARM = "screen_time_alarm_minutes"
internal const val PREF_SCREEN_ALARM_PERIOD = "screen_time_alarm_period"
private const val PREF_SCREEN_ALARM_LAST_PERIOD = "screen_time_alarm_last_period"

internal enum class ScreenTimePeriod(val label: String, val key: String) {
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
        fun fromKey(key: String?): ScreenTimePeriod = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

internal fun screenTimeMinutes(context: Context, period: ScreenTimePeriod): Long? {
    if (!hasUsageAccess(context)) return null
    val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = period.startDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return runCatching {
        usageStats.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
            .filter { it.packageName != context.packageName && it.totalTimeInForeground >= 60_000 }
            .sumOf { it.totalTimeInForeground / 60_000 }
    }.getOrNull()
}

internal fun checkScreenTimeAlarm(context: Context) {
    val prefs = context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE)
    val limit = prefs.getInt(PREF_SCREEN_ALARM, 0)
    if (limit > 0) {
        val period = ScreenTimePeriod.fromKey(prefs.getString(PREF_SCREEN_ALARM_PERIOD, null))
        val periodId = period.periodId()
        if (prefs.getString(PREF_SCREEN_ALARM_LAST_PERIOD, "") != periodId) {
            screenTimeMinutes(context, period)
                ?.takeIf { it >= limit }
                ?.let { usedMinutes ->
                    com.faheem.habits.core.notifications.UsageLimitNotifications.notifyScreenTime(
                        context,
                        usedMinutes.toInt(),
                        limit,
                        period.label
                    )
                    prefs.edit().putString(PREF_SCREEN_ALARM_LAST_PERIOD, periodId).apply()
                }
        }
    }
}
