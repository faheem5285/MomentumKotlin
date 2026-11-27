package com.faheem.habits.core.notifications

import com.faheem.habits.core.data.ALL_DAYS
import com.faheem.habits.core.data.CustomNotification
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CustomNotificationTimeCalculatorTest {
    @Test
    fun weekdayBitsUseMondayFirstMapping() {
        assertEquals(1, CustomNotificationTimeCalculator.bitFor(DayOfWeek.MONDAY))
        assertEquals(2, CustomNotificationTimeCalculator.bitFor(DayOfWeek.TUESDAY))
        assertEquals(4, CustomNotificationTimeCalculator.bitFor(DayOfWeek.WEDNESDAY))
        assertEquals(8, CustomNotificationTimeCalculator.bitFor(DayOfWeek.THURSDAY))
        assertEquals(16, CustomNotificationTimeCalculator.bitFor(DayOfWeek.FRIDAY))
        assertEquals(32, CustomNotificationTimeCalculator.bitFor(DayOfWeek.SATURDAY))
        assertEquals(64, CustomNotificationTimeCalculator.bitFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun repairLegacyMasksRotatesEveryCalendarWeekdayToStoredFormat() {
        val legacyToStored = mapOf(
            1 shl 0 to (1 shl 6), // Sunday
            1 shl 1 to (1 shl 0), // Monday
            1 shl 2 to (1 shl 1), // Tuesday
            1 shl 3 to (1 shl 2), // Wednesday
            1 shl 4 to (1 shl 3), // Thursday
            1 shl 5 to (1 shl 4), // Friday
            1 shl 6 to (1 shl 5)  // Saturday
        )

        legacyToStored.forEach { (legacy, expected) ->
            assertEquals(expected, CustomNotificationTimeCalculator.repairLegacyDaysMask(legacy))
        }
        assertEquals(ALL_DAYS, CustomNotificationTimeCalculator.repairLegacyDaysMask(ALL_DAYS))
        assertEquals(0, CustomNotificationTimeCalculator.repairLegacyDaysMask(0))
    }

    @Test
    fun weeklyScheduleUsesMondayFirstStoredBits() {
        val now = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, ZoneOffset.UTC) // Monday
        val wednesdayOnly = CustomNotification(
            id = 1,
            title = "Test",
            repeatMode = CustomNotificationTimeCalculator.WEEKLY,
            hour = 9,
            minute = 0,
            daysMask = CustomNotificationTimeCalculator.bitFor(DayOfWeek.WEDNESDAY)
        )

        val next = CustomNotificationTimeCalculator.next(wednesdayOnly, now)

        assertEquals(ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneOffset.UTC), next)
    }
}
