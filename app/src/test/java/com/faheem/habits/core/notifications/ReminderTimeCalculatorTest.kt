package com.faheem.habits

import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.ReminderTimeCalculator
import com.faheem.habits.core.notifications.QuietHoursPolicy
import com.faheem.habits.core.notifications.ToDoReminderDeliveryPolicy
import com.faheem.habits.core.notifications.HabitReminderDeliveryPolicy
import com.faheem.habits.core.notifications.CustomNotificationDeliveryPolicy
import com.faheem.habits.core.notifications.CustomNotificationSnoozePolicy
import com.faheem.habits.core.notifications.ToDoRepeatPolicy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

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

    @Test
    fun repeatingTodoMovesToNextOccurrenceWhenCompletedOrSkipped() {
        val due = LocalDate.of(2025, 10, 30)
        val item = ToDoItem(
            id = 7, title = "Weekly review", dueEpochDay = due.toEpochDay(),
            repeatRule = "weekly", reminderHour = 9, reminderMinute = 0
        )
        val completed = ToDoRepeatPolicy.advance(item, due.toEpochDay())
        assertEquals(due.plusWeeks(1).toEpochDay(), completed.dueEpochDay)
        assertFalse(completed.completed)
        assertEquals(9, completed.reminderHour)

        val skipped = ToDoRepeatPolicy.skip(item, due.toEpochDay())
        assertEquals(due.plusWeeks(1).toEpochDay(), skipped.dueEpochDay)
        assertEquals(9, skipped.reminderHour)
    }

    @Test
    fun repeatRulesAdvanceAcrossCalendarBoundariesAndPastOccurrences() {
        val january31 = LocalDate.of(2025, 1, 31)
        assertEquals(LocalDate.of(2025, 2, 28), ToDoRepeatPolicy.nextDate("monthly", january31))
        assertEquals(LocalDate.of(2026, 1, 31), ToDoRepeatPolicy.nextDate("yearly", january31))

        val oldDaily = ToDoItem(
            id = 8, title = "Daily check", dueEpochDay = january31.toEpochDay(),
            repeatRule = "daily", reminderHour = 8, reminderMinute = 0
        )
        val advanced = ToDoRepeatPolicy.advance(oldDaily, LocalDate.of(2025, 2, 3).toEpochDay())
        assertEquals(LocalDate.of(2025, 2, 4).toEpochDay(), advanced.dueEpochDay)
    }

    @Test
    fun repeatingTodoReminderSchedulesNextDateAfterTodaysTimeHasPassed() {
        val now = ZonedDateTime.of(2025, 10, 30, 10, 0, 0, 0, zone)
        val repeating = ToDoItem(
            id = 9, title = "Daily check", dueEpochDay = LocalDate.of(2025, 10, 30).toEpochDay(),
            repeatRule = "daily", reminderHour = 9, reminderMinute = 30
        )
        assertEquals(
            ZonedDateTime.of(2025, 10, 31, 9, 30, 0, 0, zone),
            ReminderTimeCalculator.nextTodo(repeating, now)
        )
    }

    @Test
    fun staleTodoAlarmDoesNotDeliverWhenTaskOrNotificationsAreInactive() {
        assertFalse(ToDoReminderDeliveryPolicy.shouldDeliver(
            completed = true,
            masterEnabled = true,
            notificationsAllowed = true
        ))
        assertFalse(ToDoReminderDeliveryPolicy.shouldDeliver(
            completed = false,
            masterEnabled = false,
            notificationsAllowed = true
        ))
        assertFalse(ToDoReminderDeliveryPolicy.shouldDeliver(
            completed = false,
            masterEnabled = true,
            notificationsAllowed = false
        ))
        assertTrue(ToDoReminderDeliveryPolicy.shouldDeliver(
            completed = false,
            masterEnabled = true,
            notificationsAllowed = true
        ))
    }

    @Test
    fun habitOneTimeReminderIsPreservedWhenDeliveryIsDisabled() {
        assertFalse(HabitReminderDeliveryPolicy.shouldProcess(
            masterEnabled = false,
            habitRemindersEnabled = true,
            notificationsAllowed = true
        ))
        assertFalse(HabitReminderDeliveryPolicy.shouldProcess(
            masterEnabled = true,
            habitRemindersEnabled = false,
            notificationsAllowed = true
        ))
        assertFalse(HabitReminderDeliveryPolicy.shouldProcess(
            masterEnabled = true,
            habitRemindersEnabled = true,
            notificationsAllowed = false
        ))
        assertTrue(HabitReminderDeliveryPolicy.shouldProcess(
            masterEnabled = true,
            habitRemindersEnabled = true,
            notificationsAllowed = true
        ))
    }

    @Test
    fun customNotificationIsNotConsumedWhenDeliveryIsBlocked() {
        assertFalse(CustomNotificationDeliveryPolicy.shouldDeliver(
            masterEnabled = false,
            notificationsAllowed = true
        ))
        assertFalse(CustomNotificationDeliveryPolicy.shouldDeliver(
            masterEnabled = true,
            notificationsAllowed = false
        ))
        assertTrue(CustomNotificationDeliveryPolicy.shouldDeliver(
            masterEnabled = true,
            notificationsAllowed = true
        ))
    }

    @Test
    fun oneTimeCustomNotificationCanBeDeliveredAfterSnooze() {
        assertFalse(CustomNotificationSnoozePolicy.shouldDeliver(
            enabled = false, deleted = false, isSnooze = false,
            isOneTime = true, hasBeenDelivered = true
        ))
        assertTrue(CustomNotificationSnoozePolicy.shouldDeliver(
            enabled = false, deleted = false, isSnooze = true,
            isOneTime = true, hasBeenDelivered = true
        ))
        assertFalse(CustomNotificationSnoozePolicy.shouldDeliver(
            enabled = false, deleted = true, isSnooze = true,
            isOneTime = true, hasBeenDelivered = true
        ))
        assertFalse(CustomNotificationSnoozePolicy.shouldDeliver(
            enabled = false, deleted = false, isSnooze = true,
            isOneTime = true, hasBeenDelivered = false
        ))
        assertFalse(CustomNotificationSnoozePolicy.shouldDeliver(
            enabled = false, deleted = false, isSnooze = true,
            isOneTime = false, hasBeenDelivered = true
        ))
    }

    @Test
    fun reopenedTodoWithFutureReminderCanBeScheduledAgain() {
        val now = ZonedDateTime.of(2025, 10, 30, 10, 0, 0, 0, zone)
        val reopened = ToDoItem(
            id = 2,
            title = "Submit report",
            dueEpochDay = LocalDate.of(2025, 10, 31).toEpochDay(),
            completed = false,
            reminderHour = 9,
            reminderMinute = 15
        )

        assertEquals(
            ZonedDateTime.of(2025, 10, 31, 9, 15, 0, 0, zone),
            ReminderTimeCalculator.nextTodo(reopened, now)
        )
    }

    @Test
    fun weeklyReminderUsesTheCurrentZoneAfterTimezoneChange() {
        val reminder = HabitReminder(
            id = 4, habitId = 2, hour = 18, minute = 30, daysMask = 1 shl 2
        )
        val beforeTravel = ZonedDateTime.of(2025, 10, 28, 12, 0, 0, 0, ZoneId.of("Asia/Karachi"))
        val afterTravel = beforeTravel.withZoneSameInstant(ZoneId.of("Europe/London"))

        assertEquals(
            ZoneId.of("Europe/London"),
            ReminderTimeCalculator.nextHabit(reminder, afterTravel)?.zone
        )
        assertEquals(
            18,
            ReminderTimeCalculator.nextHabit(reminder, afterTravel)?.hour
        )
    }

    @Test
    fun oneTimeReminderMovesWithDeviceLocalTimeAfterClockChange() {
        val reminder = HabitReminder(
            id = 5,
            habitId = 2,
            hour = 9,
            minute = 0,
            daysMask = 0,
            dateEpochDay = LocalDate.of(2025, 11, 1).toEpochDay()
        )
        val now = ZonedDateTime.of(2025, 10, 31, 23, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2025, 11, 1, 9, 0, 0, 0, zone),
            ReminderTimeCalculator.nextHabit(reminder, now)
        )
    }
    @Test
    fun quietHoursSupportsOvernightWindowsAndEndsOnTheCorrectDay() {
        val beforeMidnight = Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
            set(2025, Calendar.OCTOBER, 29, 23, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val afterMidnight = Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
            set(2025, Calendar.OCTOBER, 30, 1, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(
            Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
                set(2025, Calendar.OCTOBER, 30, 7, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis,
            QuietHoursPolicy.nextEndMillis(beforeMidnight, 22, 7)
        )
        assertEquals(
            Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
                set(2025, Calendar.OCTOBER, 30, 7, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis,
            QuietHoursPolicy.nextEndMillis(afterMidnight, 22, 7)
        )
    }

    @Test
    fun quietHoursSupportsSameDayWindowsAndDisabledEqualEndpoints() {
        val duringDay = Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
            set(2025, Calendar.OCTOBER, 29, 13, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(true, QuietHoursPolicy.isQuietHours(13, 12, 15))
        assertEquals(false, QuietHoursPolicy.isQuietHours(15, 12, 15))
        assertEquals(false, QuietHoursPolicy.isQuietHours(23, 22, 22))
        assertEquals(
            Calendar.getInstance(java.util.TimeZone.getTimeZone(zone)).apply {
                set(2025, Calendar.OCTOBER, 29, 15, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis,
            QuietHoursPolicy.nextEndMillis(duringDay, 12, 15)
        )
        assertNull(QuietHoursPolicy.nextEndMillis(duringDay, 22, 22))
    }

}
