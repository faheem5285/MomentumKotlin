package com.faheem.habits.core.notifications

import java.time.ZonedDateTime

/**
 * Pure delivery decision used before any notification is posted or reminder
 * state is changed. A blocked delivery must never silently consume a one-time
 * habit reminder or clear a to-do's scheduled time.
 */
internal data class ReminderDeliveryDecision(
    val post: Boolean,
    val consume: Boolean,
    val defer: Boolean,
    val reschedule: Boolean
)

internal const val QUIET_HOURS_COPY =
    "Habit and to-do reminders are held during these hours and retried when they end; one-time reminders are not silently discarded."

internal object ReminderDeliveryPolicy {
    fun habit(
        quietHours: Boolean,
        postingAllowed: Boolean,
        completedToday: Boolean,
        oneTime: Boolean
    ): ReminderDeliveryDecision = when {
        quietHours -> ReminderDeliveryDecision(
            post = false, consume = false, defer = true, reschedule = false
        )
        !postingAllowed -> ReminderDeliveryDecision(
            post = false, consume = false, defer = false, reschedule = !oneTime
        )
        completedToday -> ReminderDeliveryDecision(
            post = false, consume = oneTime, defer = false, reschedule = !oneTime
        )
        oneTime -> ReminderDeliveryDecision(
            post = true, consume = true, defer = false, reschedule = false
        )
        else -> ReminderDeliveryDecision(
            post = true, consume = false, defer = false, reschedule = true
        )
    }

    fun todo(quietHours: Boolean, postingAllowed: Boolean, completed: Boolean): ReminderDeliveryDecision =
        when {
            quietHours -> ReminderDeliveryDecision(
                post = false, consume = false, defer = true, reschedule = false
            )
            !postingAllowed -> ReminderDeliveryDecision(
                post = false, consume = false, defer = false, reschedule = false
            )
            completed -> ReminderDeliveryDecision(
                post = false, consume = true, defer = false, reschedule = false
            )
            else -> ReminderDeliveryDecision(
                post = true, consume = true, defer = false, reschedule = false
            )
        }

    /** Next quiet-hours exit after [now], always strictly in the future. */
    fun quietHoursEndMillis(startHour: Int, endHour: Int, now: ZonedDateTime): Long {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        if (start == end) return now.plusHours(1).toInstant().toEpochMilli()
        val endDate = when {
            start < end -> if (now.hour >= start && now.hour < end) now.toLocalDate() else now.toLocalDate().plusDays(1)
            now.hour >= start -> now.toLocalDate().plusDays(1)
            else -> now.toLocalDate()
        }
        var candidate = endDate.atTime(end, 0).atZone(now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }
}
