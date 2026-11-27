package com.faheem.habits.features.screen_time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeEventPolicyTest {
    private val target = InstagramScrollCounterService.INSTAGRAM_PACKAGE

    @Test
    fun scrollEventAtDebounceBoundaryIsCountedAndUpdatesTimestamp() {
        val decision = scrollCountDecision(
            kind = ScreenTimeAccessibilityEventKind.VIEW_SCROLLED,
            packageName = target,
            targetPackage = target,
            eventTimeMs = 10_000L,
            lastCountedEventMs = 10_000L - INSTAGRAM_SCROLL_DEBOUNCE_MS
        )

        assertTrue(decision.shouldCount)
        assertEquals(10_000L, decision.lastCountedEventMs)
    }

    @Test
    fun scrollEventInsideDebounceIsIgnoredWithoutMovingTimestamp() {
        val decision = scrollCountDecision(
            kind = ScreenTimeAccessibilityEventKind.VIEW_SCROLLED,
            packageName = target,
            targetPackage = target,
            eventTimeMs = 10_000L,
            lastCountedEventMs = 10_500L
        )

        assertFalse(decision.shouldCount)
        assertEquals(10_500L, decision.lastCountedEventMs)
    }

    @Test
    fun outOfOrderEventIsIgnored() {
        val decision = scrollCountDecision(
            kind = ScreenTimeAccessibilityEventKind.VIEW_SCROLLED,
            packageName = target,
            targetPackage = target,
            eventTimeMs = 9_000L,
            lastCountedEventMs = 10_000L
        )

        assertFalse(decision.shouldCount)
        assertEquals(10_000L, decision.lastCountedEventMs)
    }

    @Test
    fun otherPackagesAndEventKindsAreFiltered() {
        val otherPackage = scrollCountDecision(
            kind = ScreenTimeAccessibilityEventKind.VIEW_SCROLLED,
            packageName = "com.other.app",
            targetPackage = target,
            eventTimeMs = 20_000L,
            lastCountedEventMs = 0L
        )
        val windowEvent = scrollCountDecision(
            kind = ScreenTimeAccessibilityEventKind.WINDOW_STATE_CHANGED,
            packageName = target,
            targetPackage = target,
            eventTimeMs = 20_000L,
            lastCountedEventMs = 0L
        )

        assertFalse(otherPackage.shouldCount)
        assertEquals(0L, otherPackage.lastCountedEventMs)
        assertFalse(windowEvent.shouldCount)
    }

    @Test
    fun overlayVisibilityOnlyFollowsForegroundPackage() {
        val show = overlayAction(
            ScreenTimeAccessibilityEventKind.WINDOW_STATE_CHANGED,
            target,
            target,
            "com.faheem.habits"
        )
        val hide = overlayAction(
            ScreenTimeAccessibilityEventKind.WINDOW_STATE_CHANGED,
            "com.other.app",
            target,
            "com.faheem.habits"
        )
        val ignoreOwn = overlayAction(
            ScreenTimeAccessibilityEventKind.WINDOW_STATE_CHANGED,
            "com.faheem.habits",
            target,
            "com.faheem.habits"
        )
        val scrollDoesNotChangeVisibility = overlayAction(
            ScreenTimeAccessibilityEventKind.VIEW_SCROLLED,
            "com.other.app",
            target,
            "com.faheem.habits"
        )

        assertEquals(ScreenTimeOverlayAction.SHOW, show)
        assertEquals(ScreenTimeOverlayAction.HIDE_DELAYED, hide)
        assertEquals(ScreenTimeOverlayAction.IGNORE, ignoreOwn)
        assertEquals(ScreenTimeOverlayAction.IGNORE, scrollDoesNotChangeVisibility)
    }

    @Test
    fun alarmAndPermissionCopyExplainRevocation() {
        assertEquals("Set alarm", screenAlarmLabel(true))
        assertTrue(screenAlarmLabel(false).contains("Usage access"))
        assertNull(usageAccessLimitationCopy(true))
        assertTrue(usageAccessLimitationCopy(false)!!.contains("paused"))
        assertTrue(USAGE_ACCESS_REVOCATION_COPY.contains("revoked"))
        assertTrue(SCREEN_ALARM_HELPER.contains("pause"))
        assertTrue(REELS_PERMISSION_LIMITATION_COPY.contains("not verified Reels plays"))
    }

    @Test
    fun overlayPermissionCopyCoversBothFloatingFeatures() {
        val copy = overlayPermissionCopy()

        assertTrue(copy.benefit.contains("network indicator"))
        assertTrue(copy.benefit.contains("scroll counter"))
        assertTrue(copy.whenDenied.contains("cannot appear"))
    }

    @Test
    fun usageLimitWorkOnlyRunsWithAccessAndConfiguredAlarm() {
        assertFalse(shouldScheduleUsageLimitWork(false, 30))
        assertFalse(shouldScheduleUsageLimitWork(true, 0))
        assertTrue(shouldScheduleUsageLimitWork(true, 30))
    }
}
