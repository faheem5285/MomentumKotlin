package com.faheem.habits.features.battery

import android.content.Intent

/** Pure debounce for the single manifest-owned power-event handler. */
internal const val BATTERY_EVENT_DEBOUNCE_MS = 2_000L

internal const val BATTERY_LISTENER_COPY =
    "Keeps a status notification active while Android delivers power events to Zylo. Android may still stop background work; reopen Zylo if it does."

internal enum class BatteryPowerEventAction { HANDLE, IGNORE_DUPLICATE, IGNORE_EVENT }

internal fun batteryPowerEventAction(
    action: String?,
    nowElapsedMs: Long,
    lastEventElapsedMs: Long,
    debounceMs: Long = BATTERY_EVENT_DEBOUNCE_MS
): BatteryPowerEventAction = when (action) {
    Intent.ACTION_POWER_CONNECTED ->
        if (lastEventElapsedMs > 0L && nowElapsedMs - lastEventElapsedMs < debounceMs) {
            BatteryPowerEventAction.IGNORE_DUPLICATE
        } else {
            BatteryPowerEventAction.HANDLE
        }
    Intent.ACTION_POWER_DISCONNECTED -> BatteryPowerEventAction.HANDLE
    else -> BatteryPowerEventAction.IGNORE_EVENT
}
