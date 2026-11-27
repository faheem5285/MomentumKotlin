package com.faheem.habits.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.faheem.habits.features.battery.BatteryChargingMonitorService
import com.faheem.habits.features.network.NetworkIndicatorService

/** Restarts persisted monitors after boot/update where Android permits, otherwise records a resume. */
class DeviceMonitorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // Android 12+ blocks most foreground-service starts from the background; the app will retry
        // when it next opens and a status notification explains the delay.
        val canStartForeground = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        recover(
            context = context,
            label = "Network monitoring",
            notificationId = NETWORK_RESTART_NOTIFICATION_ID,
            enabled = NetworkIndicatorService.enabled(context),
            pendingResume = NetworkIndicatorService.pendingResume(context),
            canStartForeground = canStartForeground,
            start = { NetworkIndicatorService.startIfEnabled(context) },
            markPending = { NetworkIndicatorService.markResumePending(context) },
            bootOrUpdate = true
        )
        recover(
            context = context,
            label = "Charging listener",
            notificationId = BATTERY_RESTART_NOTIFICATION_ID,
            enabled = BatteryChargingMonitorService.enabled(context),
            pendingResume = BatteryChargingMonitorService.pendingResume(context),
            canStartForeground = canStartForeground,
            start = { BatteryChargingMonitorService.startIfEnabled(context) },
            markPending = { BatteryChargingMonitorService.markResumePending(context) },
            bootOrUpdate = true
        )
    }

    private fun recover(
        context: Context,
        label: String,
        notificationId: Int,
        enabled: Boolean,
        pendingResume: Boolean,
        bootOrUpdate: Boolean,
        canStartForeground: Boolean,
        start: () -> Boolean,
        markPending: () -> Unit
    ) {
        when (
            deviceMonitorRecoveryAction(
                enabled = enabled,
                bootOrUpdate = bootOrUpdate,
                pendingResume = pendingResume,
                canStartForeground = canStartForeground
            )
        ) {
            DeviceMonitorRecoveryAction.NONE -> Unit
            DeviceMonitorRecoveryAction.START -> { start() }
            DeviceMonitorRecoveryAction.REMEMBER_PENDING -> {
                markPending()
                ZyloNotifications.postStatus(
                    context, notificationId, "Monitoring paused after restart",
                    monitorRestartCopy(label)
                )
            }
        }
    }

    private companion object {
        const val NETWORK_RESTART_NOTIFICATION_ID = 9_023
        const val BATTERY_RESTART_NOTIFICATION_ID = 7_823
    }
}
