package com.faheem.habits

import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.ReminderTimeCalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Karachi")

    @Test
    fun weeklyHabitUsesMondayFirstDayMaskAndFindsNextSelectedDay() {
        val now = ZonedDateTime.of(2025, 10, 29, 10, 0, 0, 0, zone) // Wednesday
        val reminder = HabitReminder(
            id = 7, habitId = 2, hour = 9, minute = 30,
            daysMask = 1 shl 3 // Thursday, with Monday at bit zero
        )

        assertEquals(
            ZonedDateTime.of(2025, 10, 30, 9, 30, 0, 0, zone),
            ReminderTimeCalculator.nextHabit(reminder, now)
        )
    }

    @Test
    fun dailyHabitMovesToTomorrowWhenTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2025, 10, 29, 9, 0, 0, 0, zone)
        val reminder = HabitReminder(id = 1, habitId = 1, hour = 8, minute = 0, daysMask = 127)

        assertEquals(
            ZonedDateTime.of(2025, 10, 30, 8, 0, 0, 0, zone),
            ReminderTimeCalculator.nextHabit(reminder, now)
        )
    }

    @Test
    fun datedHabitDoesNotRepeatAfterItsDateHasPassed() {
        val now = ZonedDateTime.of(2025, 10, 30, 12, 0, 0, 0, zone)
        val reminder = HabitReminder(
            id = 1, habitId = 1, hour = 9, minute = 0,
            daysMask = 0, dateEpochDay = LocalDate.of(2025, 10, 30).toEpochDay()
        )

        assertNull(ReminderTimeCalculator.nextHabit(reminder, now))
    }

    @Test
    fun disabledHabitReminderIsNotScheduled() {
        val reminder = HabitReminder(id = 1, habitId = 1, hour = 9, minute = 0, enabled = false)

        assertNull(ReminderTimeCalculator.nextHabit(reminder, ZonedDateTime.now(zone)))
    }

    @Test
    fun todoReminderIsNotScheduledIfCompletedOrAlreadyDue() {
        val now = ZonedDateTime.of(2025, 10, 30, 10, 0, 0, 0, zone)
        val completed = ToDoItem(
            id = 1, title = "Doctor", dueEpochDay = LocalDate.of(2025, 10, 30).toEpochDay(),
            completed = true, reminderHour = 11, reminderMinute = 0
        )
        val missed = completed.copy(completed = false, reminderHour = 9)

        assertNull(ReminderTimeCalculator.nextTodo(completed, now))
        assertNull(ReminderTimeCalculator.nextTodo(missed, now))
    }
}
