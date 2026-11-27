package com.faheem.habits.features.habits

import com.faheem.habits.core.data.ALL_DAYS
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ComputeStreaksTest {
    private val monday = LocalDate.of(2026, 1, 5).toEpochDay()

    @Test
    fun emptyHistoryOrEmptyScheduleHasNoStreak() {
        assertEquals(StreakInfo(0, 0), computeStreaks(emptySet(), ALL_DAYS, monday))
        assertEquals(StreakInfo(0, 0), computeStreaks(setOf(monday), 0, monday))
    }

    @Test
    fun todayStillOpenDoesNotBreakYesterdaysRun() {
        val days = (monday..monday + 2).toSet()
        assertEquals(StreakInfo(3, 3), computeStreaks(days, ALL_DAYS, monday + 3))
        assertEquals(StreakInfo(3, 3), computeStreaks(days, ALL_DAYS, monday + 2))
    }

    @Test
    fun unscheduledDaysDoNotBreakAStreak() {
        // Monday at bit 0, Wednesday at bit 2, Friday at bit 4.
        val monWedFri = 1 or (1 shl 2) or (1 shl 4)
        val days = setOf(monday, monday + 2)
        assertEquals(StreakInfo(2, 2), computeStreaks(days, monWedFri, monday + 4))
    }

    @Test
    fun aMissedScheduledDayBreaksTheCurrentStreak() {
        val days = setOf(monday, monday + 2)
        assertEquals(StreakInfo(0, 1), computeStreaks(days, ALL_DAYS, monday + 4))
    }

    @Test
    fun bestStreakIsTheLongestCompletedRun() {
        val days = setOf(monday, monday + 1, monday + 3, monday + 4, monday + 5)
        assertEquals(StreakInfo(3, 3), computeStreaks(days, ALL_DAYS, monday + 6))
    }

    @Test
    fun completionsAfterTodayAreIgnored() {
        val days = setOf(monday, monday + 1, monday + 8)
        assertEquals(StreakInfo(2, 2), computeStreaks(days, ALL_DAYS, monday + 1))
    }
}
