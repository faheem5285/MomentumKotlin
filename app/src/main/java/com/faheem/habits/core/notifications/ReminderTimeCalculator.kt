package com.faheem.habits.core.notifications
import com.faheem.habits.core.data.*

import java.time.LocalDate
import java.time.ZonedDateTime

/** Pure calendar calculations kept separate from Android alarm APIs for reliable testing. */
internal object ReminderTimeCalculator {
    fun nextHabit(reminder: HabitReminder, now: ZonedDateTime): ZonedDateTime? {
        if (!reminder.enabled || (reminder.daysMask == 0 && reminder.dateEpochDay == null)) return null
        if (reminder.dateEpochDay != null) {
            return LocalDate.ofEpochDay(reminder.dateEpochDay)
                .atTime(reminder.hour, reminder.minute).atZone(now.zone)
                .takeIf { it.isAfter(now) }
        }
        return (0L..7L).asSequence()
            .map { now.toLocalDate().plusDays(it).atTime(reminder.hour, reminder.minute).atZone(now.zone) }
            .firstOrNull { candidate ->
                val mondayBitIndex = candidate.dayOfWeek.value - 1
                ((reminder.daysMask shr mondayBitIndex) and 1) == 1 && candidate.isAfter(now)
            }
    }

    fun nextTodo(item: ToDoItem, now: ZonedDateTime): ZonedDateTime? {
        if (item.completed) return null
        val hour = item.reminderHour ?: return null
        val minute = item.reminderMinute ?: return null
        return LocalDate.ofEpochDay(item.dueEpochDay).atTime(hour, minute).atZone(now.zone)
            .takeIf { it.isAfter(now) }
    }
}
