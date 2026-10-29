package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HabitReminderManagerSheet(
    habit: Habit,
    reminders: List<HabitReminder>,
    onDismiss: () -> Unit,
    onAdd: (Int, Int, Int) -> Unit,
    onUpdate: (HabitReminder) -> Unit,
    onDelete: (HabitReminder) -> Unit
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++ }
    val masterEnabled = MomentumNotificationSettings.masterEnabled(context)
    val habitNotifications = MomentumNotificationSettings.isEnabled(context, "habits")
    val notificationPermission = Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val preciseAlarms = MomentumNotificationSettings.canSchedulePrecisely(context)
    val days = listOf("M" to 0, "Tu" to 1, "W" to 2, "Th" to 3, "F" to 4, "Sa" to 5, "Su" to 6)

    FormSheet("Reminders · ${habit.name}", onDismiss) {
        Text(
            "Add multiple times and choose the days each reminder repeats. For example, set eight times a day for water reminders.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Habit notifications", fontWeight = FontWeight.SemiBold)
                    Text(if (masterEnabled && habitNotifications) "Enabled" else "Turn on to receive this habit’s reminders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = masterEnabled && habitNotifications, onCheckedChange = { checked ->
                    MomentumNotificationSettings.setEnabled(context, "habits", checked)
                    if (checked && !masterEnabled) MomentumNotificationSettings.setMasterEnabled(context, true)
                    if (checked && Build.VERSION.SDK_INT >= 33 && !notificationPermission) requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    revision++
                })
            }
        }
        if (!notificationPermission) {
            OutlinedButton(onClick = { requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                Text("Allow notifications in Android")
            }
        } else if (!MomentumNotificationSettings.notificationsAllowed(context)) {
            OutlinedButton(onClick = { openNotificationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Momentum notifications in Android settings")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !preciseAlarms) {
            Text("Android may delay reminders until precise alarm access is allowed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { requestExactAlarmAccess(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Allow precise reminder times")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reminder times", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${reminders.size} set", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (reminders.isEmpty()) {
            Text("No reminders yet. Add a time to get a notification for this habit.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        reminders.forEach { reminder ->
            key(reminder.id, reminder.hour, reminder.minute, reminder.daysMask, revision) {
                ReminderTimeCard(
                    context = context,
                    reminder = reminder,
                    days = days,
                    onUpdate = { onUpdate(it); revision++ },
                    onDelete = { onDelete(reminder); revision++ }
                )
            }
        }
        OutlinedButton(
            onClick = {
                val initialDays = habit.daysMask.takeIf { it != 0 } ?: ALL_DAYS
                TimePickerDialog(context, { _, hour, minute ->
                    onAdd(hour, minute, initialDays)
                    if (!masterEnabled) MomentumNotificationSettings.setMasterEnabled(context, true)
                    if (!habitNotifications) MomentumNotificationSettings.setEnabled(context, "habits", true)
                    if (Build.VERSION.SDK_INT >= 33 && !notificationPermission) requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    revision++
                }, 9, 0, DateFormat.is24HourFormat(context)).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AddAlarm, null)
            Spacer(Modifier.width(8.dp))
            Text("Add reminder time")
        }
        Text("Times repeat on the selected weekdays using your device’s local time. Android’s notification and alarm permissions must be enabled for on-time delivery.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReminderTimeCard(
    context: Context,
    reminder: HabitReminder,
    days: List<Pair<String, Int>>,
    onUpdate: (HabitReminder) -> Unit,
    onDelete: () -> Unit
) {
    val current = remember(reminder.hour, reminder.minute) {
        Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, reminder.hour); set(Calendar.MINUTE, reminder.minute) }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> onUpdate(reminder.copy(hour = hour, minute = minute)) }, reminder.hour, reminder.minute, DateFormat.is24HourFormat(context)).show()
                }) { Text(DateFormat.getTimeFormat(context).format(current.time), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete reminder", tint = MaterialTheme.colorScheme.error) }
            }
            if (reminder.dateEpochDay != null) {
                val date = LocalDate.ofEpochDay(reminder.dateEpochDay)
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        onUpdate(reminder.copy(dateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay()))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text("One time · ${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}")
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    days.forEach { (label, bit) ->
                        val selected = ((reminder.daysMask shr bit) and 1) == 1
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newMask = reminder.daysMask xor (1 shl bit)
                                if (newMask != 0) onUpdate(reminder.copy(daysMask = newMask))
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Text(scheduleText(reminder.daysMask), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun requestExactAlarmAccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}
