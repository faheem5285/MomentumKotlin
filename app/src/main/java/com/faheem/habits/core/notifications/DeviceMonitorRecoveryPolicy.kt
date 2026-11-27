package com.faheem.habits.core.notifications

/** Pure decision for restarting persisted monitors after a reboot or package replacement. */

internal enum class DeviceMonitorRecoveryAction { NONE, START, REMEMBER_PENDING }

internal fun deviceMonitorRecoveryAction(
    enabled: Boolean,
    bootOrUpdate: Boolean,
    pendingResume: Boolean,
    canStartForeground: Boolean
): DeviceMonitorRecoveryAction = when {
    !enabled -> DeviceMonitorRecoveryAction.NONE
    !bootOrUpdate && !pendingResume -> DeviceMonitorRecoveryAction.NONE
    canStartForeground -> DeviceMonitorRecoveryAction.START
    else -> DeviceMonitorRecoveryAction.REMEMBER_PENDING
}

internal fun monitorRestartCopy(label: String): String =
    "$label was on before the restart. Android blocked a background start; open Zylo to resume it."
