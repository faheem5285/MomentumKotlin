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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

@Composable
private fun EditorLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Create (initial == null) or edit a habit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    initial: Habit?,
    onDismiss: () -> Unit,
    onSave: (Habit, List<HabitReminderDraft>) -> Unit,
    initialReminders: List<HabitReminder> = emptyList()
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var what by remember { mutableStateOf(initial?.description ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "star") }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 0) }
    var time by remember { mutableIntStateOf(initial?.timeOfDay ?: 0) }
    var mask by remember { mutableIntStateOf(initial?.daysMask ?: ALL_DAYS) }
    var reminders by remember(initial?.id) {
        mutableStateOf(initialReminders.map { HabitReminderDraft(it.hour, it.minute, it.daysMask, it.dateEpochDay) })
    }
    var notificationAllowed by remember { mutableStateOf(MomentumNotificationSettings.notificationsAllowed(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        notificationAllowed = allowed && MomentumNotificationSettings.notificationsAllowed(context)
    }
    val accent = habitColor(color)

    FormSheet(if (initial == null) "New habit" else "Edit habit", onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Habit name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = what,
            onValueChange = { what = it },
            label = { Text("What do I need to do?") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        EditorLabel("When")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TIME_LABELS.forEachIndexed { i, label ->
                FilterChip(selected = time == i, onClick = { time = i }, label = { Text(label) })
            }
        }

        EditorLabel("Repeat on")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DAY_LETTERS.forEachIndexed { i, letter ->
                DayToggle(letter, ((mask shr i) and 1) == 1) { mask = mask xor (1 shl i) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { mask = ALL_DAYS }) { Text("Every day") }
            TextButton(onClick = { mask = WEEKDAYS }) { Text("Weekdays") }
            TextButton(onClick = { mask = WEEKENDS }) { Text("Weekends") }
        }

        EditorLabel("Reminders")
        Text("Add weekly reminders or choose a date for a one-time reminder. Each reminder has its own date and time.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reminders.forEachIndexed { index, reminder ->
            HabitReminderDraftCard(
                index = index,
                reminder = reminder,
                onChange = { changed -> reminders = reminders.toMutableList().also { it[index] = changed } },
                onDelete = { reminders = reminders.toMutableList().also { it.removeAt(index) } }
            )
        }
        OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    reminders = reminders + HabitReminderDraft(hour, minute, mask.takeIf { it != 0 } ?: ALL_DAYS)
                }, 9, 0, DateFormat.is24HourFormat(context)).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AddAlarm, null)
            Spacer(Modifier.width(8.dp))
            Text("Add reminder time")
        }
        OutlinedButton(
            onClick = {
                val start = LocalDate.now()
                DatePickerDialog(context, { _, year, month, day ->
                    val pickedDate = LocalDate.of(year, month + 1, day)
                    TimePickerDialog(context, { _, hour, minute ->
                        reminders = reminders + HabitReminderDraft(hour, minute, 0, pickedDate.toEpochDay())
                    }, 9, 0, DateFormat.is24HourFormat(context)).show()
                }, start.year, start.monthValue - 1, start.dayOfMonth).apply {
                    datePicker.minDate = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }.show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Event, null)
            Spacer(Modifier.width(8.dp))
            Text("Add reminder for a specific date")
        }
        if (reminders.isNotEmpty() && !notificationAllowed) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow notifications so these reminders can appear on your device.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else openHabitNotificationSettings(context)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Enable habit notifications") }
                }
            }
        }
        if (reminders.isNotEmpty() && !MomentumNotificationSettings.canSchedulePrecisely(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("Android may delay reminders until precise alarm access is allowed.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))) }
            }) { Text("Allow precise reminder times") }
        }

        EditorLabel("Islamic icons")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IslamicHabitIconKeys.forEach { key ->
                val vector = habitIcon(key)
                val sel = key == icon
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (sel) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (sel) Modifier.border(2.dp, accent, CircleShape) else Modifier)
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        vector, key.replaceFirstChar { it.uppercase() },
                        tint = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        EditorLabel("More icons")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitIcons.filterKeys { it !in IslamicHabitIconKeys }.forEach { (key, vector) ->
                val sel = key == icon
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(if (sel) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (sel) Modifier.border(2.dp, accent, CircleShape) else Modifier)
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(vector, key.replaceFirstChar { it.uppercase() }, tint = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        EditorLabel("Colour")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HabitColors.forEachIndexed { i, c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(c).clickable { color = i },
                    contentAlignment = Alignment.Center
                ) {
                    if (color == i) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }

        BigButton("Save habit") {
            if (name.isNotBlank()) {
                val base = initial ?: Habit(name = "")
                onSave(
                    base.copy(
                        name = name.trim(),
                        description = what.trim(),
                        icon = icon,
                        colorIndex = color,
                        timeOfDay = time,
                        daysMask = if (mask == 0) ALL_DAYS else mask
                    ),
                    reminders
                )
                onDismiss()
            }
        }
    }
}

@Composable
private fun HabitReminderDraftCard(
    index: Int,
    reminder: HabitReminderDraft,
    onChange: (HabitReminderDraft) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dayLabels = listOf("M" to 0, "Tu" to 1, "W" to 2, "Th" to 3, "F" to 4, "Sa" to 5, "Su" to 6)
    val date = reminder.dateEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    val time = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, reminder.hour); set(Calendar.MINUTE, reminder.minute) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminder ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> onChange(reminder.copy(hour = hour, minute = minute)) }, reminder.hour, reminder.minute, DateFormat.is24HourFormat(context)).show()
                }) { Text(DateFormat.getTimeFormat(context).format(time.time)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove reminder", tint = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = reminder.dateEpochDay == null,
                    onClick = { if (reminder.dateEpochDay != null) onChange(reminder.copy(dateEpochDay = null, daysMask = maskOrAll(reminder.daysMask))) },
                    label = { Text("Repeat weekly") }
                )
                FilterChip(
                    selected = reminder.dateEpochDay != null,
                    onClick = { if (reminder.dateEpochDay == null) onChange(reminder.copy(dateEpochDay = today(), daysMask = 0)) },
                    label = { Text("One-time date") }
                )
            }
            if (reminder.dateEpochDay != null) {
                OutlinedButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        onChange(reminder.copy(dateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay()))
                    }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                        datePicker.minDate = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }.show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Schedule, null)
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                }
            } else {
                Text("Repeat on", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (label, bit) ->
                        val selected = ((reminder.daysMask shr bit) and 1) == 1
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = reminder.daysMask xor (1 shl bit)
                                if (next != 0) onChange(reminder.copy(daysMask = next))
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

private fun maskOrAll(mask: Int) = mask.takeIf { it != 0 } ?: ALL_DAYS

private fun openHabitNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
}
