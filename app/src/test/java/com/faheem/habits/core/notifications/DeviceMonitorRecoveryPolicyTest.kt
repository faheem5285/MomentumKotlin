package com.faheem.habits.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMonitorRecoveryPolicyTest {
    @Test
    fun disabledMonitorIsNeverRestarted() {
        assertEquals(
            DeviceMonitorRecoveryAction.NONE,
            deviceMonitorRecoveryAction(
                enabled = false,
                bootOrUpdate = true,
                pendingResume = true,
                canStartForeground = true
            )
        )
    }

    @Test
    fun bootStartsMonitorWhenForegroundStartIsAllowed() {
        assertEquals(
            DeviceMonitorRecoveryAction.START,
            deviceMonitorRecoveryAction(
                enabled = true,
                bootOrUpdate = true,
                pendingResume = false,
                canStartForeground = true
            )
        )
    }

    @Test
    fun blockedBootStartRecordsAPendingResume() {
        assertEquals(
            DeviceMonitorRecoveryAction.REMEMBER_PENDING,
            deviceMonitorRecoveryAction(
                enabled = true,
                bootOrUpdate = true,
                pendingResume = false,
                canStartForeground = false
            )
        )
    }

    @Test
    fun pendingResumeIsRetriedWhenTheAppOpens() {
        assertEquals(
            DeviceMonitorRecoveryAction.START,
            deviceMonitorRecoveryAction(
                enabled = true,
                bootOrUpdate = false,
                pendingResume = true,
                canStartForeground = true
            )
        )
        assertEquals(
            DeviceMonitorRecoveryAction.REMEMBER_PENDING,
            deviceMonitorRecoveryAction(
                enabled = true,
                bootOrUpdate = false,
                pendingResume = true,
                canStartForeground = false
            )
        )
    }

    @Test
    fun restartCopyExplainsTheUserActionWithoutClaimingSuccess() {
        val copy = monitorRestartCopy("Network monitoring")

        assertTrue(copy.contains("Android blocked"))
        assertTrue(copy.contains("open Zylo to resume"))
    }
}
