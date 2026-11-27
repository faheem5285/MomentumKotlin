package com.faheem.habits.features.habits

import java.time.LocalDate

/** daysMask: bit0 = Monday ... bit6 = Sunday */
fun isScheduled(mask: Int, epochDay: Long): Boolean {
    val dow = LocalDate.ofEpochDay(epochDay).dayOfWeek.value
    return ((mask shr (dow - 1)) and 1) == 1
}

data class StreakInfo(val current: Int, val best: Int)

/**
 * Streaks count consecutive scheduled days that were completed.
 * Days the habit is not scheduled never break a streak, and today
 * does not break the streak until the day is over.
 */
fun computeStreaks(days: Set<Long>, mask: Int, today: Long): StreakInfo {
    if (days.isEmpty() || mask == 0) return StreakInfo(0, 0)
    val first = days.minOrNull() ?: return StreakInfo(0, 0)
    var best = 0
    var run = 0
    for (day in first..today) {
        if (!isScheduled(mask, day)) continue
        if (day in days) {
            run++
            best = maxOf(best, run)
        } else if (day != today) {
            run = 0
        }
    }

    // Today remains available until midnight, so an unchecked habit does not
    // break yesterday's streak early.
    var current = 0
    var cursor = today
    if (isScheduled(mask, cursor) && cursor !in days) cursor--
    while (cursor >= first) {
        if (isScheduled(mask, cursor)) {
            if (cursor !in days) break
            current++
        }
        cursor--
    }
    return StreakInfo(current, best)
}
