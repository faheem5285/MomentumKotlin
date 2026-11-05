package com.faheem.habits.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.faheem.habits.R

object UsageLimitNotifications {
    const val CHANNEL_ID = "usage_limits"
    private const val SCREEN_ID = 7401
    private const val NETWORK_ID = 7402

    fun notifyScreenTime(context: Context, minutes: Int, limitMinutes: Int, period: String) {
        notify(
            context,
            SCREEN_ID,
            "$period screen time limit reached",
            "You used ${formatDuration(minutes)} of screen time; your $period limit is ${formatDuration(limitMinutes)}."
        )
    }

    fun notifyNetwork(context: Context, label: String, bytes: Long, alertNumber: Int, thresholdBytes: Long, period: String) {
        val notificationId = NETWORK_ID + (if (label == "Wi-Fi") 1_000 else 0) + alertNumber.coerceIn(1, 64)
        val nextThreshold = thresholdBytes.toDouble() * 1.5
        val nextThresholdText = if (nextThreshold < Long.MAX_VALUE.toDouble()) format(nextThreshold.toLong()) else "beyond the supported range"
        val text = "${format(bytes)} used; alert $alertNumber was triggered at ${format(thresholdBytes)}. Monitoring continues. Next alert at about $nextThresholdText."
        notify(
            context,
            notificationId,
            "$label $period usage alert $alertNumber",
            text
        )
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Usage limits", NotificationManager.IMPORTANCE_HIGH)
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_zylo)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun format(bytes: Long): String =
        if (bytes >= 1_000_000_000) "%.2f GB".format(bytes / 1_000_000_000.0)
        else "%.1f MB".format(bytes / 1_000_000.0)

    private fun formatDuration(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
