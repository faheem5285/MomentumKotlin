package com.faheem.habits.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReminderDeliveryPolicyTest {
    private val zone = ZoneOffset.UTC

    @Test
    fun quietHoursPreserveHabitReminderStateAndDefer() {
        val decision = ReminderDeliveryPolicy.habit(
            quietHours = true,
            postingAllowed = true,
            completedToday = false,
            oneTime = true
        )
        assertFalse(decision.post)
        assertFalse(decision.consume)
        assertTrue(decision.defer)
        assertFalse(decision.reschedule)
    }

    @Test
    fun deliveredHabitRemindersConsumeOnlyWhenActuallyPosted() {
        val oneTime = ReminderDeliveryPolicy.habit(false, true, false, true)
        assertTrue(oneTime.post)
        assertTrue(oneTime.consume)
        assertFalse(oneTime.defer)

        val recurring = ReminderDeliveryPolicy.habit(false, true, false, false)
        assertTrue(recurring.post)
        assertFalse(recurring.consume)
        assertTrue(recurring.reschedule)

        val blocked = ReminderDeliveryPolicy.habit(false, false, false, true)
        assertFalse(blocked.post)
        assertFalse(blocked.consume)
        assertFalse(blocked.defer)
    }

    @Test
    fun quietHoursPreserveTodoReminderTime() {
        val decision = ReminderDeliveryPolicy.todo(quietHours = true, postingAllowed = true, completed = false)
        assertFalse(decision.post)
        assertFalse(decision.consume)
        assertTrue(decision.defer)

        val delivered = ReminderDeliveryPolicy.todo(false, true, false)
        assertTrue(delivered.post)
        assertTrue(delivered.consume)

        val blocked = ReminderDeliveryPolicy.todo(false, false, false)
        assertFalse(blocked.post)
        assertFalse(blocked.consume)
    }

    @Test
    fun quietHoursEndHandlesSameNightAndWrappedMidnight() {
        val late = ZonedDateTime.of(2026, 1, 5, 23, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 1, 6, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ReminderDeliveryPolicy.quietHoursEndMillis(22, 7, late)
        )

        val early = ZonedDateTime.of(2026, 1, 6, 6, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 1, 6, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ReminderDeliveryPolicy.quietHoursEndMillis(22, 7, early)
        )

        val daytime = ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 1, 5, 17, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ReminderDeliveryPolicy.quietHoursEndMillis(9, 17, daytime)
        )
        assertEquals(
            ZonedDateTime.of(2026, 1, 6, 17, 0, 0, 0, zone).toInstant().toEpochMilli(),
            ReminderDeliveryPolicy.quietHoursEndMillis(9, 17, daytime.withHour(18))
        )
    }

    @Test
    fun quietHoursCopySaysRemindersAreDeferredNotDiscarded() {
        assertTrue(QUIET_HOURS_COPY.contains("held during these hours"))
        assertTrue(QUIET_HOURS_COPY.contains("not silently discarded"))
    }

    @Test
    fun disabledQuietHoursUsesBoundedRetry() {
        val now = ZonedDateTime.of(2026, 1, 5, 12, 0, 0, 0, zone)
        assertEquals(
            now.plusHours(1).toInstant().toEpochMilli(),
            ReminderDeliveryPolicy.quietHoursEndMillis(22, 22, now)
        )
    }
}
