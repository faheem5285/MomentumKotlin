package com.faheem.habits.core.notifications

import android.content.Context
import com.faheem.habits.core.ui.AppModule

/** Shared, permission-aware and grouped presentation for usage-limit alerts. */
object UsageLimitNotifications {
    private const val SCREEN_ID = 7_401
    private const val NETWORK_ID = 7_402

    fun notifyScreenTime(context: Context, minutes: Int, limitMinutes: Int, period: String) {
        val (title, body) = screenTimeAlertCopy(minutes, limitMinutes, period)
        ZyloNotifications.postUsageLimit(
            context, SCREEN_ID, title, body, AppModule.Screen.name
        )
    }

    fun notifyNetwork(
        context: Context,
        label: String,
        bytes: Long,
        alertNumber: Int,
        thresholdBytes: Long,
        period: String,
        collapsedOlderAlerts: Int = 0
    ) {
        ZyloNotifications.postUsageLimit(
            context,
            networkNotificationId(label, alertNumber),
            networkAlertTitle(label, period, alertNumber),
            networkAlertBody(bytes, alertNumber, thresholdBytes, period, collapsedOlderAlerts),
            AppModule.Network.name
        )
    }

    fun clearNetworkAlerts(context: Context) {
        val manager = androidx.core.app.NotificationManagerCompat.from(context)
        runCatching {
            repeat(64) { index ->
                manager.cancel(networkNotificationId("Mobile", index + 1))
                manager.cancel(networkNotificationId("Wi-Fi", index + 1))
            }
            ZyloNotifications.syncSummary(
                context,
                ZyloNotifications.GROUP_USAGE,
                ZyloNotifications.CHANNEL_USAGE_LIMITS,
                ZyloNotifications.SUMMARY_USAGE,
                "Usage alerts"
            )
        }
    }

    private fun networkNotificationId(label: String, alertNumber: Int): Int =
        NETWORK_ID + (if (label == "Wi-Fi") 1_000 else 0) + alertNumber.coerceIn(1, 64)
}
