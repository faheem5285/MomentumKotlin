@file:Suppress("TooManyFunctions")

package com.faheem.habits.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.CustomNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Schedules and delivers custom notifications through the shared reminder receiver. */
object CustomNotificationScheduler {
    private const val ACTION = "com.faheem.habits.CUSTOM_NOTIFICATION"
    private const val ACTION_SNOOZE_ALARM = "com.faheem.habits.CUSTOM_SNOOZE"
    private const val ACTION_DONE = "com.faheem.habits.REMINDER_ACTION_DONE"
    private const val ACTION_SNOOZE = "com.faheem.habits.REMINDER_ACTION_SNOOZE"
    private const val KEY_CUSTOM_ID = "custom_id"

    private fun requestCode(id: Int) = 70_000 + id
    private fun snoozeRequestCode(id: Int) = 80_000 + id
    private fun notificationId(id: Int) = 70_000 + id

    fun schedule(context: Context, notification: CustomNotification) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = pendingIntent(context, notification.id)
        if (notification.deleted || !notification.enabled ||
            !MomentumNotificationSettings.masterEnabled(context)
        ) {
            alarms.cancel(alarmIntent)
            return
        }
        val next = CustomNotificationTimeCalculator.next(notification, ZonedDateTime.now())
        if (next == null) {
            alarms.cancel(alarmIntent)
            return
        }
        MomentumReminderScheduler.setAlarm(alarms, next.toInstant().toEpochMilli(), alarmIntent)
    }

    fun rescheduleAll(context: Context) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                rescheduleAllNow(context)
            } catch (e: IllegalStateException) {
                Log.e("MomentumReminders", "Custom notifications could not be rescheduled.", e)
            } catch (e: SecurityException) {
                Log.e("MomentumReminders", "Custom notification scheduling is not permitted.", e)
            }
        }
    }

    suspend fun rescheduleAllNow(context: Context) {
        AppDatabase.get(context).customNotificationDao().getForScheduling()
            .forEach { schedule(context, it) }
    }

    fun cancel(context: Context, id: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(context, id))
        alarms.cancel(snoozePendingIntent(context, id))
        cancelNotification(context, id)
    }

    fun cancelNotification(context: Context, id: Int) {
        ZyloNotifications.cancel(context, notificationId(id))
        ZyloNotifications.syncSummary(
            context, ZyloNotifications.GROUP_CUSTOM, ZyloNotifications.CHANNEL_CUSTOM,
            ZyloNotifications.SUMMARY_CUSTOM, "Custom notifications"
        )
    }

    suspend fun deliver(context: Context, id: Int) {
        val dao = AppDatabase.get(context).customNotificationDao()
        val notification = dao.getById(id) ?: return
        if (notification.deleted || !notification.enabled) {
            cancel(context, id)
            return
        }
        if (!MomentumNotificationSettings.masterEnabled(context)) return

        ZyloNotifications.ensureChannels(context)
        val channel = if (MomentumNotificationSettings.isQuietHours(context)) {
            ZyloNotifications.CHANNEL_CUSTOM_SILENT
        } else {
            ZyloNotifications.CHANNEL_CUSTOM
        }
        val body = notification.message.ifBlank { "Your scheduled Zylo notification is here." }
        val built = ZyloNotifications.base(context, channel)
            .setContentTitle(notification.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText("Custom notification")
            .setWhen(System.currentTimeMillis())
            .setContentIntent(ZyloNotifications.openApp(context, notificationId(id)))
            .setGroup(ZyloNotifications.GROUP_CUSTOM)
            .addAction(
                0, "Done",
                actionPendingIntent(context, ACTION_DONE, requestCode(id), id)
            )
            .addAction(
                0, "Snooze 10m",
                actionPendingIntent(context, ACTION_SNOOZE, requestCode(id) + 1, id, 10)
            )
            .addAction(
                0, "Snooze 1h",
                actionPendingIntent(context, ACTION_SNOOZE, requestCode(id) + 2, id, 60)
            )
            .build()

        ZyloNotifications.postGrouped(
            context,
            ZyloNotifications.GROUP_CUSTOM,
            channel,
            notificationId(id),
            built,
            ZyloNotifications.SUMMARY_CUSTOM,
            "Custom notifications"
        )

        val now = System.currentTimeMillis()
        if (notification.repeatMode == CustomNotificationTimeCalculator.ONCE) {
            dao.update(notification.copy(enabled = false, lastDeliveredEpochMillis = now, updatedAt = now, dirty = true))
            cancel(context, id)
        } else {
            val refreshed = notification.copy(lastDeliveredEpochMillis = now, updatedAt = now, dirty = true)
            dao.update(refreshed)
            schedule(context, refreshed)
        }
    }

    fun snooze(context: Context, id: Int, minutes: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        MomentumReminderScheduler.setAlarm(
            alarms,
            System.currentTimeMillis() + minutes.coerceIn(5, 120) * 60_000L,
            snoozePendingIntent(context, id)
        )
    }

    suspend fun dismiss(context: Context, id: Int) {
        val dao = AppDatabase.get(context).customNotificationDao()
        val notification = dao.getById(id)
        cancel(context, id)
        if (notification != null && notification.repeatMode == CustomNotificationTimeCalculator.ONCE) {
            val now = System.currentTimeMillis()
            dao.update(notification.copy(enabled = false, updatedAt = now, dirty = true))
        }
    }

    private fun pendingIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context,
        requestCode(id),
        Intent(context, MomentumReminderReceiver::class.java)
            .setAction(ACTION)
            .putExtra(KEY_CUSTOM_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun snoozePendingIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context,
        snoozeRequestCode(id),
        Intent(context, MomentumReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE_ALARM)
            .putExtra(KEY_CUSTOM_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        id: Int,
        snoozeMinutes: Int? = null
    ) = PendingIntent.getBroadcast(
        context,
        60_000 + requestCode,
        Intent(context, MomentumReminderReceiver::class.java)
            .setAction(action)
            .putExtra(KEY_CUSTOM_ID, id)
            .apply { snoozeMinutes?.let { putExtra("snooze_minutes", it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
