@file:Suppress("LongParameterList", "CyclomaticComplexMethod", "ComplexCondition")

package com.faheem.habits.core.notifications
import com.faheem.habits.app.MainActivity
import com.faheem.habits.core.data.*
import com.faheem.habits.core.ui.*

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class ReminderKind(
    val id: String,
    val title: String,
    val message: String,
    val module: AppModule,
    val weekly: Boolean
)

data class ScheduledReminderPreview(val title: String, val triggerAtMillis: Long)

val ReminderKinds = listOf(
    ReminderKind("habits", "Habit check-in", "Take a moment to check in with today's habits.", AppModule.Habits, false),
    ReminderKind("money", "Weekly money review", "Review spending and plan the week ahead.", AppModule.Money, true),
    ReminderKind("loans", "Loan follow-up", "Review any open loans and follow-ups.", AppModule.Loans, true)
)

private const val PREFS = "momentum_notifications"
private const val KEY_MASTER = "notifications_enabled"
private const val KEY_HOUR = "hour_"
private const val KEY_MINUTE = "minute_"
private const val KEY_DAY = "weekday_"
private const val KEY_QUIET_START = "quiet_start"
private const val KEY_QUIET_END = "quiet_end"
private const val SAFETY_NET_WORK = "momentum_reminder_safety_net"
private const val ACTION_DONE = "com.faheem.habits.REMINDER_ACTION_DONE"
private const val ACTION_SNOOZE = "com.faheem.habits.REMINDER_ACTION_SNOOZE"
private const val ACTION_SKIP = "com.faheem.habits.REMINDER_ACTION_SKIP"
private const val ACTION_TEST = "com.faheem.habits.REMINDER_TEST"

private fun enabledKey(id: String) = "enabled_$id"
private fun alarmCode(id: String) = 5400 + (ReminderKinds.indexOfFirst { it.id == id }.coerceAtLeast(0))
private fun channelId(id: String) = "reminder_$id"

private fun actionPendingIntent(
    context: Context,
    action: String,
    requestCode: Int,
    idKey: String,
    id: Int,
    snoozeMinutes: Int? = null
) = PendingIntent.getBroadcast(
    context,
    60_000 + requestCode,
    Intent(context, MomentumReminderReceiver::class.java).setAction(action)
        .putExtra(idKey, id)
        .apply { snoozeMinutes?.let { putExtra("snooze_minutes", it) } },
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

object MomentumNotificationSettings {
    fun masterEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MASTER, false)
    fun setMasterEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_MASTER, enabled).apply()
        MomentumReminderScheduler.scheduleAll(context)
    }

    fun isEnabled(context: Context, id: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(enabledKey(id), id == "habits")
    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(enabledKey(id), enabled).apply()
        MomentumReminderScheduler.schedule(context, id)
        if (id == "habits") HabitReminderScheduler.rescheduleAll(context)
    }
    fun hour(context: Context, id: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOUR + id, if (id == "habits") 20 else 18)
    fun minute(context: Context, id: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTE + id, 0)
    fun setTime(context: Context, id: String, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_HOUR + id, hour).putInt(KEY_MINUTE + id, minute).apply()
        MomentumReminderScheduler.schedule(context, id)
    }
    fun weekday(context: Context, id: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAY + id, if (id == "loans") Calendar.MONDAY else Calendar.SUNDAY)
    fun setWeekday(context: Context, id: String, day: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DAY + id, day).apply()
        MomentumReminderScheduler.schedule(context, id)
    }
    fun notificationsAllowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    fun channelIdFor(id: String) = channelId(id)
    fun canSchedulePrecisely(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarms.canScheduleExactAlarms()
    }

    fun quietHours(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_QUIET_START, 22) to prefs.getInt(KEY_QUIET_END, 7)
    }

    fun setQuietHours(context: Context, startHour: Int, endHour: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_QUIET_START, startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, endHour.coerceIn(0, 23))
            .apply()
        MomentumReminderScheduler.scheduleAll(context)
    }

    fun isQuietHours(context: Context, now: Calendar = Calendar.getInstance()): Boolean {
        val (start, end) = quietHours(context)
        if (start == end) return false
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return if (start < end) hour >= start && hour < end else hour >= start || hour < end
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }
}

object MomentumReminderScheduler {
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ReminderKinds.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(channelId(kind.id), kind.title, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Scheduled ${kind.title.lowercase()} notifications from Momentum."
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
            )
        }
        manager.createNotificationChannel(
            NotificationChannel("todo_reminders", "To-do reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for tasks scheduled in Momentum."
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        scheduleSafetyNet(context)
    }

    private fun scheduleSafetyNet(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderVerificationWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_NET_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleAll(context: Context) {
        createChannels(context)
        ReminderKinds.forEach { schedule(context, it.id) }
        HabitReminderScheduler.rescheduleAll(context)
        ToDoReminderScheduler.rescheduleAll(context)
    }

    suspend fun rescheduleAllNow(context: Context) {
        ReminderKinds.forEach { schedule(context, it.id) }
        HabitReminderScheduler.rescheduleAllNow(context)
        ToDoReminderScheduler.rescheduleAllNow(context)
    }

    suspend fun nextTen(context: Context): List<ScheduledReminderPreview> {
        val now = ZonedDateTime.now()
        val db = AppDatabase.get(context)
        val habitDao = db.habitDao()
        val habits = habitDao.getRemindersForScheduling().mapNotNull { reminder ->
            val habit = habitDao.getHabit(reminder.habitId) ?: return@mapNotNull null
            ReminderTimeCalculator.nextHabit(reminder, now)?.let {
                ScheduledReminderPreview(habit.name, it.toInstant().toEpochMilli())
            }
        }
        val todos = db.toDoDao().getItemsForScheduling().mapNotNull { item ->
            ReminderTimeCalculator.nextTodo(item, now)?.let {
                ScheduledReminderPreview(item.title, it.toInstant().toEpochMilli())
            }
        }
        return (habits + todos).sortedBy { it.triggerAtMillis }.take(10)
    }

    fun scheduleTestReminder(context: Context) {
        val intent = Intent(context, MomentumReminderReceiver::class.java).setAction(ACTION_TEST)
        val pending = PendingIntent.getBroadcast(
            context, 59_999, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager,
            System.currentTimeMillis() + 10_000L,
            pending
        )
    }

    fun schedule(context: Context, id: String) {
        val kind = ReminderKinds.firstOrNull { it.id == id } ?: return
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, kind.id)
        if (!MomentumNotificationSettings.masterEnabled(context) || !MomentumNotificationSettings.isEnabled(context, kind.id)) {
            alarms.cancel(pendingIntent)
            return
        }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, MomentumNotificationSettings.hour(context, kind.id))
            set(Calendar.MINUTE, MomentumNotificationSettings.minute(context, kind.id))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (kind.weekly) {
                set(Calendar.DAY_OF_WEEK, MomentumNotificationSettings.weekday(context, kind.id))
                if (timeInMillis <= now.timeInMillis) add(Calendar.WEEK_OF_YEAR, 1)
            } else if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        setAlarm(alarms, next.timeInMillis, pendingIntent)
    }

    fun postAndReschedule(context: Context, id: String) {
        val kind = ReminderKinds.firstOrNull { it.id == id } ?: return
        if (MomentumNotificationSettings.masterEnabled(context) && MomentumNotificationSettings.isEnabled(context, id) && MomentumNotificationSettings.notificationsAllowed(context)) {
            createChannels(context)
            val openApp = PendingIntent.getActivity(
                context, alarmCode(id), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId(id))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(kind.title)
                .setContentText(kind.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(kind.message))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .setGroup("momentum_${kind.id}")
                .build()
            if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { NotificationManagerCompat.from(context).notify(alarmCode(id), notification) }
            }
        }
        schedule(context, id)
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        context, alarmCode(id), Intent(context, MomentumReminderReceiver::class.java).setAction("com.faheem.habits.REMINDER_$id"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun setAlarm(alarms: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }
}

object HabitReminderScheduler {
    private const val ACTION = "com.faheem.habits.HABIT_REMINDER"
    private fun requestCode(id: Int) = 20_000 + id

    fun cancel(context: Context, reminderId: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(context, reminderId))
    }

    fun schedule(context: Context, reminder: HabitReminder) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = pendingIntent(context, reminder.id)
        if (!MomentumNotificationSettings.masterEnabled(context) ||
            !MomentumNotificationSettings.isEnabled(context, "habits") ||
            !reminder.enabled || (reminder.daysMask == 0 && reminder.dateEpochDay == null)
        ) {
            alarms.cancel(alarmIntent)
            return
        }
        val next = ReminderTimeCalculator.nextHabit(reminder, ZonedDateTime.now())
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
                Log.e("MomentumReminders", "Habit reminders could not be rescheduled.", e)
            } catch (e: SecurityException) {
                Log.e("MomentumReminders", "Habit reminder scheduling is not permitted.", e)
            }
        }
    }

    suspend fun rescheduleAllNow(context: Context) {
        val db = AppDatabase.get(context)
        db.habitDao().getRemindersForScheduling().forEach { schedule(context, it) }
    }

    suspend fun deliver(context: Context, reminderId: Int) {
        val dao = AppDatabase.get(context).habitDao()
        val reminder = dao.getReminder(reminderId) ?: return
        val habit = dao.getHabit(reminder.habitId) ?: return
        val completedToday = dao.getCompletion(reminder.habitId, java.time.LocalDate.now().toEpochDay()) != null
        if (!habit.archived && !completedToday && reminder.enabled && !MomentumNotificationSettings.isQuietHours(context) &&
            MomentumNotificationSettings.masterEnabled(context) &&
            MomentumNotificationSettings.isEnabled(context, "habits") && MomentumNotificationSettings.notificationsAllowed(context)
        ) {
            MomentumReminderScheduler.createChannels(context)
            val notification = NotificationCompat.Builder(context, channelId("habits"))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(habit.name)
                .setContentText("Your habit reminder is ready.")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(
                    context, requestCode(reminderId), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
                .setGroup("momentum_habits")
                .addAction(0, "Done", actionPendingIntent(context, ACTION_DONE, requestCode(reminderId), "reminder_id", reminderId))
                .addAction(0, "Snooze 5m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(reminderId) + 1, "reminder_id", reminderId, 5))
                .addAction(0, "Snooze 15m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(reminderId) + 2, "reminder_id", reminderId, 15))
                .addAction(0, "Snooze 60m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(reminderId) + 3, "reminder_id", reminderId, 60))
                .addAction(0, "Skip", actionPendingIntent(context, ACTION_SKIP, requestCode(reminderId) + 4, "reminder_id", reminderId))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(requestCode(reminderId), notification) }
        }
        if (reminder.dateEpochDay != null) {
            dao.updateReminder(reminder.copy(enabled = false))
            cancel(context, reminder.id)
        } else schedule(context, reminder)
    }

    private fun pendingIntent(context: Context, reminderId: Int) = PendingIntent.getBroadcast(
        context, requestCode(reminderId), Intent(context, MomentumReminderReceiver::class.java)
            .setAction(ACTION).putExtra("reminder_id", reminderId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun snooze(context: Context, reminderId: Int, minutes: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        MomentumReminderScheduler.setAlarm(
            alarms,
            System.currentTimeMillis() + minutes.coerceIn(5, 60) * 60_000L,
            pendingIntent(context, reminderId)
        )
    }

    suspend fun skip(context: Context, reminderId: Int) {
        val reminder = AppDatabase.get(context).habitDao().getReminder(reminderId) ?: return
        cancel(context, reminderId)
        schedule(context, reminder)
    }
}

object ToDoReminderScheduler {
    private const val ACTION = "com.faheem.habits.TODO_REMINDER"
    private fun requestCode(id: Int) = 40_000 + id

    private fun pendingIntent(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, requestCode(id), Intent(context, MomentumReminderReceiver::class.java)
            .setAction(ACTION).putExtra("todo_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancel(context: Context, id: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pendingIntent(context, id))
    }

    fun schedule(context: Context, item: ToDoItem) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = pendingIntent(context, item.id)
        if (item.completed || item.reminderHour == null || item.reminderMinute == null ||
            !MomentumNotificationSettings.masterEnabled(context) || !MomentumNotificationSettings.notificationsAllowed(context)
        ) {
            alarms.cancel(alarmIntent)
            return
        }
        val next = ReminderTimeCalculator.nextTodo(item, ZonedDateTime.now())
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
                Log.e("MomentumReminders", "To-do reminders could not be rescheduled.", e)
            } catch (e: SecurityException) {
                Log.e("MomentumReminders", "To-do reminder scheduling is not permitted.", e)
            }
        }
    }

    suspend fun rescheduleAllNow(context: Context) {
        AppDatabase.get(context).toDoDao().getItemsForScheduling().forEach { schedule(context, it) }
    }

    suspend fun deliver(context: Context, id: Int) {
        val dao = AppDatabase.get(context).toDoDao()
        val item = dao.getById(id) ?: return
        if (!item.completed && !MomentumNotificationSettings.isQuietHours(context) &&
            MomentumNotificationSettings.masterEnabled(context) && MomentumNotificationSettings.notificationsAllowed(context)) {
            MomentumReminderScheduler.createChannels(context)
            val openApp = PendingIntent.getActivity(
                context, requestCode(id), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, "todo_reminders")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(item.title)
                .setContentText("Scheduled for today")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .setGroup("momentum_todos")
                .addAction(0, "Done", actionPendingIntent(context, ACTION_DONE, requestCode(id), "todo_id", id))
                .addAction(0, "Snooze 5m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(id) + 1, "todo_id", id, 5))
                .addAction(0, "Snooze 15m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(id) + 2, "todo_id", id, 15))
                .addAction(0, "Snooze 60m", actionPendingIntent(context, ACTION_SNOOZE, requestCode(id) + 3, "todo_id", id, 60))
                .addAction(0, "Skip", actionPendingIntent(context, ACTION_SKIP, requestCode(id) + 4, "todo_id", id))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(requestCode(id), notification) }
        }
        dao.update(item.copy(reminderHour = null, reminderMinute = null))
        cancel(context, id)
    }

    fun snooze(context: Context, id: Int, minutes: Int) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        MomentumReminderScheduler.setAlarm(
            alarms,
            System.currentTimeMillis() + minutes.coerceIn(5, 60) * 60_000L,
            pendingIntent(context, id)
        )
    }

    suspend fun skip(context: Context, id: Int) {
        val dao = AppDatabase.get(context).toDoDao()
        val item = dao.getById(id) ?: return
        dao.update(item.copy(reminderHour = null, reminderMinute = null))
        cancel(context, id)
    }
}

class MomentumReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TEST -> {
                MomentumReminderScheduler.createChannels(context)
                if (MomentumNotificationSettings.notificationsAllowed(context) &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(
                            context,
                            "android.permission.POST_NOTIFICATIONS"
                        ) == PackageManager.PERMISSION_GRANTED)
                ) {
                    NotificationManagerCompat.from(context).notify(
                        59_999,
                        NotificationCompat.Builder(context, channelId("habits"))
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle("Momentum test reminder")
                            .setContentText("Reminder delivery is working.")
                            .setCategory(NotificationCompat.CATEGORY_REMINDER)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
            ACTION_DONE, ACTION_SNOOZE, ACTION_SKIP -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val reminderId = intent.getIntExtra("reminder_id", -1)
                        val todoId = intent.getIntExtra("todo_id", -1)
                        val minutes = intent.getIntExtra("snooze_minutes", 15)
                        when {
                            reminderId >= 0 -> {
                                NotificationManagerCompat.from(context).cancel(20_000 + reminderId)
                                when (intent.action) {
                                    ACTION_DONE -> {
                                        val dao = AppDatabase.get(context).habitDao()
                                        val reminder = dao.getReminder(reminderId)
                                        if (reminder != null) {
                                            dao.insertCompletion(
                                                Completion(reminder.habitId, java.time.LocalDate.now().toEpochDay())
                                            )
                                            HabitReminderScheduler.schedule(context, reminder)
                                        }
                                    }

                                    ACTION_SNOOZE -> HabitReminderScheduler.snooze(context, reminderId, minutes)
                                    ACTION_SKIP -> HabitReminderScheduler.skip(context, reminderId)
                                }
                            }
                            todoId >= 0 -> {
                                NotificationManagerCompat.from(context).cancel(40_000 + todoId)
                                when (intent.action) {
                                    ACTION_DONE -> {
                                        val dao = AppDatabase.get(context).toDoDao()
                                        dao.getById(todoId)?.let {
                                            dao.update(it.copy(completed = true, reminderHour = null, reminderMinute = null))
                                        }
                                        ToDoReminderScheduler.cancel(context, todoId)
                                    }
                                    ACTION_SNOOZE -> ToDoReminderScheduler.snooze(context, todoId, minutes)
                                    ACTION_SKIP -> ToDoReminderScheduler.skip(context, todoId)
                                }
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        MomentumReminderScheduler.createChannels(context)
                        MomentumReminderScheduler.rescheduleAllNow(context)
                    } finally { pending.finish() }
                }
            }

            "com.faheem.habits.HABIT_REMINDER" -> {
                val reminderId = intent.getIntExtra("reminder_id", -1)
                if (reminderId >= 0) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try { HabitReminderScheduler.deliver(context, reminderId) }
                        finally { pending.finish() }
                    }
                }

            }
            "com.faheem.habits.TODO_REMINDER" -> {
                val taskId = intent.getIntExtra("todo_id", -1)
                if (taskId >= 0) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try { ToDoReminderScheduler.deliver(context, taskId) }
                        finally { pending.finish() }
                    }
                }
            }
            else -> ReminderKinds.firstOrNull { intent?.action == "com.faheem.habits.REMINDER_${it.id}" }
                ?.let { MomentumReminderScheduler.postAndReschedule(context, it.id) }
        }
    }
}
