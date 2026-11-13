@file:Suppress("TooManyFunctions")

package com.faheem.habits.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.faheem.habits.R
import com.faheem.habits.app.MainActivity

/**
 * Shared notification presentation for every Zylo alert.
 *
 * Channels are grouped so Android's notification settings read like the app,
 * while builders keep the small icon, accent colour, visibility and opening
 * behaviour consistent across reminders, custom alerts and foreground services.
 */
object ZyloNotifications {
    const val CHANNEL_HABITS = "reminder_habits"
    const val CHANNEL_MONEY = "reminder_money"
    const val CHANNEL_LOANS = "reminder_loans"
    const val CHANNEL_TODOS = "todo_reminders"
    const val CHANNEL_CUSTOM = "custom_notifications"
    const val CHANNEL_CUSTOM_SILENT = "custom_notifications_silent"
    const val CHANNEL_STATUS = "reminder_status"
    const val CHANNEL_BATTERY_ANIMATION = "charging_animation"
    const val CHANNEL_BATTERY_LISTENER = "background_charging_listener"
    const val CHANNEL_NETWORK = "persistent_network_indicator"

    const val GROUP_HABITS = "zylo.habits"
    const val GROUP_TODOS = "zylo.todos"
    const val GROUP_CUSTOM = "zylo.custom"
    const val GROUP_STATUS = "zylo.status"

    const val SUMMARY_HABITS = 58_001
    const val SUMMARY_TODOS = 58_002
    const val SUMMARY_CUSTOM = 58_003
    const val SUMMARY_STATUS = 58_004

    const val EXTRA_TARGET_MODULE = "com.faheem.habits.extra.TARGET_MODULE"
    const val EXTRA_TARGET_ITEM_ID = "com.faheem.habits.extra.TARGET_ITEM_ID"

    private const val CHANNEL_GROUP_REMINDERS = "zylo_reminders"
    private const val CHANNEL_GROUP_BACKGROUND = "zylo_background"
    private const val ACCENT = 0xFF8B5CF6.toInt()

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP_REMINDERS, "Zylo reminders")
        )
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP_BACKGROUND, "Zylo background tools")
        )
        create(
            manager, CHANNEL_HABITS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT,
            CHANNEL_GROUP_REMINDERS, "Scheduled check-ins for your habits."
        )
        create(
            manager, CHANNEL_MONEY, "Money review", NotificationManager.IMPORTANCE_DEFAULT,
            CHANNEL_GROUP_REMINDERS, "Weekly reviews of spending and budgets."
        )
        create(
            manager, CHANNEL_LOANS, "Loan follow-ups", NotificationManager.IMPORTANCE_DEFAULT,
            CHANNEL_GROUP_REMINDERS, "Weekly follow-ups for open loans."
        )
        create(
            manager, CHANNEL_TODOS, "To-do reminders", NotificationManager.IMPORTANCE_DEFAULT,
            CHANNEL_GROUP_REMINDERS, "Reminders for tasks scheduled in Zylo."
        )
        create(
            manager, CHANNEL_CUSTOM, "Custom notifications", NotificationManager.IMPORTANCE_DEFAULT,
            CHANNEL_GROUP_REMINDERS, "Alerts you write and schedule yourself."
        )
        create(
            manager, CHANNEL_CUSTOM_SILENT, "Custom notifications (quiet hours)",
            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_REMINDERS,
            "Custom alerts that arrive during quiet hours without sound or vibration."
        ) { channel ->
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.enableLights(false)
        }
        create(
            manager, CHANNEL_STATUS, "Reminder status", NotificationManager.IMPORTANCE_LOW,
            CHANNEL_GROUP_REMINDERS, "Test reminders and delivery checks."
        )
        create(
            manager, CHANNEL_BATTERY_ANIMATION, "Charging animation",
            NotificationManager.IMPORTANCE_DEFAULT, CHANNEL_GROUP_BACKGROUND,
            "A shortcut to your charging animation while the phone is charging."
        )
        create(
            manager, CHANNEL_BATTERY_LISTENER, "Background charging listener",
            NotificationManager.IMPORTANCE_LOW, CHANNEL_GROUP_BACKGROUND,
            "Shows while Zylo listens for a charger connection in the background."
        )
        create(
            manager, CHANNEL_NETWORK, "Network speed and usage", NotificationManager.IMPORTANCE_LOW,
            CHANNEL_GROUP_BACKGROUND, "Persistent live network speed and today's data totals."
        ) { channel ->
            channel.setShowBadge(false)
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.enableLights(false)
        }
    }

    private fun create(
        manager: NotificationManager,
        id: String,
        name: String,
        importance: Int,
        group: String,
        description: String,
        configure: (NotificationChannel) -> Unit = {}
    ) {
        val channel = NotificationChannel(id, name, importance).apply {
            this.group = group
            this.description = description
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            configure(this)
        }
        manager.createNotificationChannel(channel)
    }

    /** A builder with the shared Zylo icon, accent and privacy defaults. */
    fun base(context: Context, channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_zylo)
            .setColor(ACCENT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setShowWhen(true)

    fun openApp(
        context: Context,
        requestCode: Int,
        targetModule: String? = null,
        targetItemId: Int? = null
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        if (targetModule != null) intent.putExtra(EXTRA_TARGET_MODULE, targetModule)
        if (targetItemId != null) intent.putExtra(EXTRA_TARGET_ITEM_ID, targetItemId)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun post(context: Context, id: Int, notification: Notification) {
        if (!MomentumNotificationSettings.notificationsAllowed(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission can be revoked between the permission check and delivery.
        }
    }

    fun postGrouped(
        context: Context,
        group: String,
        channelId: String,
        childId: Int,
        notification: Notification,
        summaryId: Int,
        summaryTitle: String
    ) {
        post(context, childId, notification)
        syncSummary(context, group, channelId, summaryId, summaryTitle)
    }

    fun syncSummary(
        context: Context,
        group: String,
        channelId: String,
        summaryId: Int,
        summaryTitle: String
    ) {
        val manager = NotificationManagerCompat.from(context)
        val active = runCatching {
            manager.activeNotifications.count { item ->
                item.notification.group == group &&
                    (item.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
        }.getOrDefault(0)
        if (active > 1) {
            val summary = base(context, channelId)
                .setContentTitle(summaryTitle)
                .setContentText("$active reminders waiting")
                .setSubText("Zylo")
                .setGroup(group)
                .setGroupSummary(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(context, summaryId))
                .build()
            runCatching { manager.notify(summaryId, summary) }
        } else {
            manager.cancel(summaryId)
        }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
