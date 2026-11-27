package com.faheem.habits.features.battery

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryPowerEventPolicyTest {
    @Test
    fun duplicateConnectWithinDebounceIsIgnored() {
        val decision = batteryPowerEventAction(
            action = Intent.ACTION_POWER_CONNECTED,
            nowElapsedMs = 12_000L,
            lastEventElapsedMs = 10_500L
        )

        assertEquals(BatteryPowerEventAction.IGNORE_DUPLICATE, decision)
    }

    @Test
    fun connectAtDebounceBoundaryIsHandled() {
        val decision = batteryPowerEventAction(
            action = Intent.ACTION_POWER_CONNECTED,
            nowElapsedMs = 12_000L,
            lastEventElapsedMs = 12_000L - BATTERY_EVENT_DEBOUNCE_MS
        )

        assertEquals(BatteryPowerEventAction.HANDLE, decision)
    }

    @Test
    fun disconnectIsAlwaysHandledAndOtherActionsAreIgnored() {
        assertEquals(
            BatteryPowerEventAction.HANDLE,
            batteryPowerEventAction(Intent.ACTION_POWER_DISCONNECTED, 1_000L, 999L)
        )
        assertEquals(
            BatteryPowerEventAction.IGNORE_EVENT,
            batteryPowerEventAction(Intent.ACTION_BATTERY_CHANGED, 1_000L, 0L)
        )
        assertEquals(
            BatteryPowerEventAction.IGNORE_EVENT,
            batteryPowerEventAction(null, 1_000L, 0L)
        )
        assertTrue(BATTERY_LISTENER_COPY.contains("Android may still stop background work"))
    }
}
