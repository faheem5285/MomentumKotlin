package com.faheem.habits.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAlertPolicyTest {
    @Test
    fun noAlertBelowFirstThreshold() {
        val plan = planNetworkAlerts(bytes = 999_999L, limitMb = 1L, lastAlertIndex = -1)

        assertTrue(plan.alerts.isEmpty())
        assertEquals(0, plan.collapsedOlderAlerts)
        assertEquals(-1, plan.lastAlertIndex)
    }

    @Test
    fun crossedThresholdsAreMarkedHandledInOrder() {
        val plan = planNetworkAlerts(bytes = 2_500_000L, limitMb = 1L, lastAlertIndex = -1)

        assertEquals(listOf(0, 1, 2), plan.alerts.map { it.index })
        assertEquals(
            listOf(1_000_000L, 1_500_000L, 2_250_000L),
            plan.alerts.map { it.thresholdBytes }
        )
        assertEquals(0, plan.collapsedOlderAlerts)
        assertEquals(2, plan.lastAlertIndex)
    }

    @Test
    fun burstCollapsesOlderAlertsAndStillAdvancesTheCursor() {
        val plan = planNetworkAlerts(bytes = 20_000_000L, limitMb = 1L, lastAlertIndex = -1)

        assertEquals(MAX_NETWORK_ALERTS_PER_CHECK, plan.alerts.size)
        assertTrue(plan.collapsedOlderAlerts > 0)
        assertEquals(plan.alerts.last().index, plan.lastAlertIndex)
        assertTrue(plan.alerts.all { it.thresholdBytes <= 20_000_000L })
    }

    @Test
    fun noReplayOnTheNextCheck() {
        val first = planNetworkAlerts(bytes = 20_000_000L, limitMb = 1L, lastAlertIndex = -1)
        val second = planNetworkAlerts(bytes = 20_000_000L, limitMb = 1L, lastAlertIndex = first.lastAlertIndex)

        assertTrue(second.alerts.isEmpty())
        assertEquals(first.lastAlertIndex, second.lastAlertIndex)
    }

    @Test
    fun overflowThresholdStopsPlanningWithoutCrashing() {
        val plan = planNetworkAlerts(bytes = Long.MAX_VALUE, limitMb = Long.MAX_VALUE, lastAlertIndex = -1)

        assertTrue(plan.alerts.isEmpty())
        assertEquals(-1, plan.lastAlertIndex)
    }

    @Test
    fun networkCopyMentionsCollapsedAlertsAndUsesReadableUnits() {
        val body = networkAlertBody(
            bytes = 20_000_000L,
            alertNumber = 4,
            thresholdBytes = 3_375_000L,
            period = "Daily",
            collapsedOlderAlerts = 2
        )

        assertTrue(body.contains("20.0 MB used"))
        assertTrue(body.contains("3.4 MB"))
        assertTrue(body.contains("2 earlier thresholds were passed and combined"))
        assertEquals("Wi-Fi Daily usage alert 4", networkAlertTitle("Wi-Fi", "Daily", 4))
        assertTrue(networkLimitHelperCopy("Daily").contains("combined when several thresholds"))
    }

    @Test
    fun screenTimeCopyUsesHoursWhenNeeded() {
        val (title, body) = screenTimeAlertCopy(125, 120, "Daily")

        assertEquals("Daily screen time limit reached", title)
        assertTrue(body.contains("2h 5m"))
        assertTrue(body.contains("2h 0m"))
    }
}
