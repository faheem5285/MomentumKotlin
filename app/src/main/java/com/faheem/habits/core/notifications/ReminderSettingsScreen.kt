@file:Suppress("CyclomaticComplexMethod", "FunctionNaming")

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Security
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
    val quietHours = MomentumNotificationSettings.quietHours(context)
    var nextReminders by remember { mutableStateOf<List<ScheduledReminderPreview>>(emptyList()) }
    LaunchedEffect(revision) {
        nextReminders = MomentumReminderScheduler.nextTen(context)
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar("Reminders", onBack = onBack)
        key(revision) {
            androidx.compose.foundation.lazy.LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = MomentumTokens.screenPadding,
                    vertical = MomentumTokens.space1
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    HeroCard {
                        Icon(
                            if (notificationsAllowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            null, tint = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Stay on track", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                        Text(
                            "Choose which Zylo reminders you receive and when they arrive.",
                            fontSize = 13.sp, color = Color.White.copy(alpha = .9f)
                        )
                    }
                }
                item {
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reminder health", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text("How reliably reminders reach you", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusPill(
                                if (notificationsAllowed) "Active" else "Blocked",
                                if (notificationsAllowed) Positive else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        HealthRow("Notifications", if (notificationsAllowed) "Allowed" else "Blocked", notificationsAllowed)
                        HealthRow("Exact alarms", if (MomentumNotificationSettings.canSchedulePrecisely(context)) "Allowed" else "Fallback mode", MomentumNotificationSettings.canSchedulePrecisely(context))
                        HealthRow("Battery", if (MomentumNotificationSettings.isIgnoringBatteryOptimizations(context)) "Unrestricted" else "Optimized", MomentumNotificationSettings.isIgnoringBatteryOptimizations(context))
                        if (!MomentumNotificationSettings.isIgnoringBatteryOptimizations(context)) {
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton(
                                "Allow background delivery",
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                        )
                                    }
                                },
                                icon = Icons.Rounded.Security
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        PrimaryButton(
                            "Send test reminder in 10s",
                            onClick = { MomentumReminderScheduler.scheduleTestReminder(context) },
                            icon = Icons.Rounded.NotificationsActive
                        )
                    }
                }
                item {
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Quiet hours", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "${quietHours.first}:00 – ${quietHours.second}:00",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusPill("No alerts", MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                "Start",
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, _ ->
                                            MomentumNotificationSettings.setQuietHours(context, hour, quietHours.second)
                                            revision++
                                        },
                                        quietHours.first,
                                        0,
                                        DateFormat.is24HourFormat(context)
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Alarm
                            )
                            SecondaryButton(
                                "End",
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, hour, _ ->
                                            MomentumNotificationSettings.setQuietHours(context, quietHours.first, hour)
                                            revision++
                                        },
                                        quietHours.second,
                                        0,
                                        DateFormat.is24HourFormat(context)
                                    ).show()
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Alarm
                            )
                        }
                    }
                }
                item {
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Reminder schedule", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (master) "Active and scheduling reminders" else "Turn on to schedule reminders",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        text = "Android needs notification permission before Zylo can post reminders.",
                        button = "Allow notifications",
                        onClick = { requestPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                    )
                } else if (master && !notificationsAllowed) item {
                    PermissionCard(
                        text = "Notifications are blocked in Android settings. Allow Zylo notifications to receive reminders.",
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
                item {
                    SectionHeader("Next reminders", subtitle = if (nextReminders.isEmpty()) "Nothing scheduled" else "${nextReminders.size} upcoming")
                }
                if (nextReminders.isEmpty()) item {
                    EmptyState(Icons.Rounded.NotificationsOff, "No reminders scheduled", "Add reminder times to a habit or task to see them here")
                } else {
                    items(nextReminders.size) { index ->
                        val reminder = nextReminders[index]
                        val formatted = java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.SHORT,
                            java.text.DateFormat.SHORT,
                            Locale.getDefault()
                        ).format(java.util.Date(reminder.triggerAtMillis))
                        PremiumCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.Alarm, MaterialTheme.colorScheme.primary, 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(reminder.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(formatted, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Reminder types") }
                items(ReminderKinds.size) { index ->
                    ReminderCard(ReminderKinds[index], context, enabled = master, onChanged = { revision++ })
                }
                item {
                    InfoBanner(
                        icon = Icons.Rounded.Info,
                        text = "Reminder times use your device’s local time. Android may delay background reminders to save battery. Lock-screen visibility and sound are controlled by Android notification settings and each channel.",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, value: String, healthy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (healthy) Positive else MaterialTheme.colorScheme.tertiary
        )
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
    InfoBanner(
        icon = Icons.Rounded.Security,
        text = text,
        tint = MaterialTheme.colorScheme.tertiary,
        actionLabel = button,
        onAction = onClick
    )
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
    PremiumCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(kind.module.icon, kind.module.gradient.first(), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (kind.weekly) "Weekly reminder" else "Daily reminder",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = {
                MomentumNotificationSettings.setEnabled(context, kind.id, it)
                onChanged()
            })
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumOutlinedButton(
                text = DateFormat.getTimeFormat(context).format(
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                    }.time
                ),
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            MomentumNotificationSettings.setTime(context, kind.id, selectedHour, selectedMinute)
                            onChanged()
                        },
                        hour, minute, DateFormat.is24HourFormat(context)
                    ).show()
                },
                icon = Icons.Rounded.Alarm,
                enabled = enabled
            )
            Text(if (kind.weekly) "on the selected day" else "every day", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (kind.weekly) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { (day, label) ->
                    MomentumChip(
                        selected = weekday == day,
                        enabled = enabled,
                        onClick = { MomentumNotificationSettings.setWeekday(context, kind.id, day); onChanged() },
                        label = label
                    )
                }
            }
        }
        TextButton(enabled = true, onClick = { openChannelSettings(context, kind.id) }) {
            Text("Sound, vibration & lock-screen options")
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
