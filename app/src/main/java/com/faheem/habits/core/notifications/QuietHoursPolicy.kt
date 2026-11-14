package com.faheem.habits.core.notifications

import java.util.Calendar

/** Pure quiet-hours rules shared by reminder delivery and unit tests. */
internal object QuietHoursPolicy {
    fun isQuietHours(hour: Int, startHour: Int, endHour: Int): Boolean {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        if (start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    /** Returns the next local quiet-hours end, or null when quiet hours are inactive. */
    fun nextEndMillis(now: Calendar, startHour: Int, endHour: Int): Long? {
        if (!isQuietHours(now.get(Calendar.HOUR_OF_DAY), startHour, endHour)) return null
        val end = endHour.coerceIn(0, 23)
        return (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, end)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
}
