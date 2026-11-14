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
        var dueDate = LocalDate.ofEpochDay(item.dueEpochDay)
        var candidate = dueDate.atTime(hour, minute).atZone(now.zone)
        if (item.repeatRule.isNotBlank()) {
            while (!candidate.isAfter(now)) {
                dueDate = ToDoRepeatPolicy.nextDate(item.repeatRule, dueDate) ?: return null
                candidate = dueDate.atTime(hour, minute).atZone(now.zone)
            }
            return candidate
        }
        return candidate.takeIf { it.isAfter(now) }
    }


    /** Next occurrence of one of the built-in reminder types. */
    fun nextScheduled(
        hour: Int,
        minute: Int,
        weekly: Boolean,
        calendarWeekday: Int,
        now: ZonedDateTime
    ): ZonedDateTime? {
        val targetDay = if (weekly) {
            java.time.DayOfWeek.of(((calendarWeekday + 5) % 7) + 1)
        } else {
            null
        }
        return (0L..7L).asSequence()
            .map { now.toLocalDate().plusDays(it).atTime(hour, minute).atZone(now.zone) }
            .firstOrNull { candidate ->
                candidate.isAfter(now) && (targetDay == null || candidate.dayOfWeek == targetDay)
            }
    }
}


/** Recurrence rules shared by task completion, Skip, and reminder rescheduling. */
internal object ToDoRepeatPolicy {
    fun nextDate(rule: String, from: LocalDate): LocalDate? = when (rule.lowercase()) {
        "daily" -> from.plusDays(1)
        "weekly" -> from.plusWeeks(1)
        "monthly" -> from.plusMonths(1)
        "yearly" -> from.plusYears(1)
        else -> null
    }

    fun advance(item: ToDoItem, todayEpochDay: Long = LocalDate.now().toEpochDay()): ToDoItem {
        val today = LocalDate.ofEpochDay(todayEpochDay)
        var next = nextDate(item.repeatRule, LocalDate.ofEpochDay(item.dueEpochDay))
        while (next != null && !next.isAfter(today)) {
            next = nextDate(item.repeatRule, next)
        }
        return if (next == null) {
            item.copy(
                completed = true,
                reminderHour = null,
                reminderMinute = null,
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        } else {
            item.copy(
                dueEpochDay = next.toEpochDay(),
                dueEpochMillis = null,
                completed = false,
                updatedAt = System.currentTimeMillis(),
                dirty = true
            )
        }
    }

    fun skip(item: ToDoItem, todayEpochDay: Long = LocalDate.now().toEpochDay()): ToDoItem =
        if (item.repeatRule.isBlank()) item.copy(
            reminderHour = null, reminderMinute = null,
            updatedAt = System.currentTimeMillis(), dirty = true
        ) else advance(item, todayEpochDay).copy(updatedAt = System.currentTimeMillis(), dirty = true)
}

/** Delivery gate kept pure so stale alarms cannot consume saved reminder settings. */
internal object ToDoReminderDeliveryPolicy {
    fun shouldDeliver(completed: Boolean, masterEnabled: Boolean, notificationsAllowed: Boolean): Boolean =
        !completed && masterEnabled && notificationsAllowed
}


/** Prevents stale alarms from consuming one-time reminders while delivery is disabled. */
internal object HabitReminderDeliveryPolicy {
    fun shouldProcess(
        masterEnabled: Boolean,
        habitRemindersEnabled: Boolean,
        notificationsAllowed: Boolean
    ): Boolean = masterEnabled && habitRemindersEnabled && notificationsAllowed
}
