package com.faheem.habits.core.notifications

import com.faheem.habits.core.data.CustomNotification
import java.time.LocalDate
import java.time.ZonedDateTime

/** Calendar rules for user-created notifications, kept free of Android APIs. */
internal object CustomNotificationTimeCalculator {
    const val ONCE = "once"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"

    val repeatModes = listOf(ONCE, DAILY, WEEKLY, MONTHLY)

    fun next(notification: CustomNotification, now: ZonedDateTime): ZonedDateTime? {
        if (!notification.enabled) return null
        val hour = notification.hour.coerceIn(0, 23)
        val minute = notification.minute.coerceIn(0, 59)
        return when (notification.repeatMode) {
            ONCE -> notification.dateEpochDay?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay).atTime(hour, minute).atZone(now.zone)
                    .takeIf { it.isAfter(now) }
            }

            DAILY -> (0L..1L).asSequence()
                .map { now.toLocalDate().plusDays(it).atTime(hour, minute).atZone(now.zone) }
                .firstOrNull { it.isAfter(now) }

            WEEKLY -> if (notification.daysMask == 0) {
                null
            } else {
                (0L..7L).asSequence()
                    .map { now.toLocalDate().plusDays(it).atTime(hour, minute).atZone(now.zone) }
                    .firstOrNull { candidate ->
                        val mondayBitIndex = candidate.dayOfWeek.value - 1
                        ((notification.daysMask shr mondayBitIndex) and 1) == 1 && candidate.isAfter(now)
                    }
            }

            MONTHLY -> {
                val day = notification.dayOfMonth.coerceIn(1, 31)
                (0L..13L).asSequence()
                    .map { now.toLocalDate().withDayOfMonth(1).plusMonths(it) }
                    .map { month ->
                        month.withDayOfMonth(day.coerceAtMost(month.lengthOfMonth()))
                            .atTime(hour, minute).atZone(now.zone)
                    }
                    .firstOrNull { it.isAfter(now) }
            }

            else -> null
        }
    }
}


/** Prevents custom reminders from being consumed when Android cannot display them. */
internal object CustomNotificationDeliveryPolicy {
    fun shouldDeliver(masterEnabled: Boolean, notificationsAllowed: Boolean): Boolean =
        masterEnabled && notificationsAllowed
}


/** Allows a snoozed one-time notification to finish after its original delivery. */
internal object CustomNotificationSnoozePolicy {
    fun shouldDeliver(
        enabled: Boolean,
        deleted: Boolean,
        isSnooze: Boolean,
        isOneTime: Boolean,
        hasBeenDelivered: Boolean
    ): Boolean = !deleted && (enabled || (isSnooze && isOneTime && hasBeenDelivered))
}
