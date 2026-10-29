package com.faheem.habits.core.notifications
import com.faheem.habits.core.data.*
import com.faheem.habits.core.ui.*

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

@Composable
fun ReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        MomentumReminderScheduler.scheduleAll(context)
        revision++
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        revision++
    }
    val permissionGranted = Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val notificationsAllowed = MomentumNotificationSettings.notificationsAllowed(context)
    val master = MomentumNotificationSettings.masterEnabled(context)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings") }
            Text("Notifications & reminders", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        key(revision) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HeroCard {
                        Icon(if (notificationsAllowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, null, tint = Color.White)
                        Spacer(Modifier.height(6.dp))
                        Text("Stay on track", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Choose which Momentum reminders you receive and when they arrive.", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow reminders", fontWeight = FontWeight.Bold)
                                Text(if (master) "Reminder schedule is active" else "Turn on to schedule selected reminders", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = master, onCheckedChange = { checked ->
                                MomentumNotificationSettings.setMasterEnabled(context, checked)
                                revision++
                                if (checked && Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
                                    requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            })
                        }
                    }
                }
                if (master && !permissionGranted) item {
                    PermissionCard(
                        text = "Android needs notification permission before Momentum can post reminders.",
                        button = "Allow notifications",
                        onClick = { requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    )
                } else if (master && !notificationsAllowed) item {
                    PermissionCard(
                        text = "Notifications are blocked in Android settings. Allow Momentum notifications to receive reminders.",
                        button = "Open notification settings",
                        onClick = { openAppNotificationSettings(context) }
                    )
                }
                if (master && !MomentumNotificationSettings.canSchedulePrecisely(context)) item {
                    PermissionCard(
                        text = "Precise alarm access helps reminders arrive close to the selected time. Without it, Android may delay them.",
                        button = "Allow precise alarms",
                        onClick = { requestExactAlarms(context) }
                    )
                }
                item { SectionLabel("Reminder schedule") }
                items(ReminderKinds.size) { index ->
                    ReminderCard(ReminderKinds[index], context, enabled = master, onChanged = { revision++ })
                }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "Reminder times use your device’s local time. Android may delay background reminders to save battery. Lock-screen visibility and sound are controlled by Android notification settings and each notification channel.",
                            Modifier.padding(16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun requestExactAlarms(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }
}

@Composable
private fun PermissionCard(text: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun ReminderCard(kind: ReminderKind, context: Context, enabled: Boolean, onChanged: () -> Unit) {
    val checked = MomentumNotificationSettings.isEnabled(context, kind.id)
    val hour = MomentumNotificationSettings.hour(context, kind.id)
    val minute = MomentumNotificationSettings.minute(context, kind.id)
    val weekday = MomentumNotificationSettings.weekday(context, kind.id)
    val days = listOf(
        Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T", Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S"
    )
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(kind.module.icon, kind.module.gradient.first(), 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.title, fontWeight = FontWeight.SemiBold)
                    Text(if (kind.weekly) "Weekly reminder" else "Daily reminder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = checked, enabled = enabled, onCheckedChange = {
                    MomentumNotificationSettings.setEnabled(context, kind.id, it)
                    onChanged()
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = enabled, onClick = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            MomentumNotificationSettings.setTime(context, kind.id, selectedHour, selectedMinute)
                            onChanged()
                        },
                        hour, minute, DateFormat.is24HourFormat(context)
                    ).show()
                }) {
                    Icon(Icons.Rounded.Alarm, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(DateFormat.getTimeFormat(context).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }.time))
                }
                Text(if (kind.weekly) "on" else "every day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (kind.weekly) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { (day, label) ->
                        FilterChip(
                            selected = weekday == day,
                            enabled = enabled,
                            onClick = { MomentumNotificationSettings.setWeekday(context, kind.id, day); onChanged() },
                            label = { Text(label) }
                        )
                    }
                }
            }
            TextButton(enabled = true, onClick = { openChannelSettings(context, kind.id) }) {
                Text("Sound, vibration & lock-screen options")
            }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

private fun openChannelSettings(context: Context, id: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, MomentumNotificationSettings.channelIdFor(id))
        runCatching { context.startActivity(intent) }
    } else openAppNotificationSettings(context)
}
