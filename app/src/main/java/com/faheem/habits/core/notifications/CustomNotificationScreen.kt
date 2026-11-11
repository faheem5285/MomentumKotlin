@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")

package com.faheem.habits.core.notifications

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faheem.habits.core.data.ALL_DAYS
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.CustomNotification
import com.faheem.habits.core.ui.FormSheet
import com.faheem.habits.core.ui.IconBubble
import com.faheem.habits.core.ui.MomentumChip
import com.faheem.habits.core.ui.MomentumOutlinedButton
import com.faheem.habits.core.ui.MomentumTextField
import com.faheem.habits.core.ui.PremiumCard
import com.faheem.habits.core.ui.PrimaryButton
import com.faheem.habits.core.ui.SectionHeader
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

private val customModes = listOf(
    CustomNotificationTimeCalculator.ONCE to "Once",
    CustomNotificationTimeCalculator.DAILY to "Daily",
    CustomNotificationTimeCalculator.WEEKLY to "Weekly",
    CustomNotificationTimeCalculator.MONTHLY to "Monthly"
)

private val customDays = listOf(
    Calendar.MONDAY to "M", Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W",
    Calendar.THURSDAY to "T", Calendar.FRIDAY to "F", Calendar.SATURDAY to "S", Calendar.SUNDAY to "S"
)

/** User-created notifications: a compact list with an inline editor. */
@Composable
fun CustomNotificationsSection(onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.get(context).customNotificationDao() }
    val notifications by dao.getAll().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomNotification?>(null) }

    Column {
        SectionHeader(
            "Custom notifications",
            subtitle = if (notifications.isEmpty()) "Write your own alerts" else "${notifications.count { it.enabled }} active",
            actionLabel = "New",
            onAction = { creating = true }
        )
        if (notifications.isEmpty()) {
            PremiumCard(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.NotificationsNone, MaterialTheme.colorScheme.primary, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No custom notifications yet",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            notifications.forEach { notification ->
                CustomNotificationCard(
                    notification = notification,
                    onEdit = { editing = notification },
                    onToggle = { enabled ->
                        scope.launch {
                            val updated = notification.copy(
                                enabled = enabled,
                                updatedAt = System.currentTimeMillis(),
                                dirty = true
                            )
                            dao.update(updated)
                            if (enabled) CustomNotificationScheduler.schedule(context, updated)
                            else CustomNotificationScheduler.cancel(context, notification.id)
                            onChanged()
                        }
                    },
                    onDelete = {
                        scope.launch {
                            CustomNotificationScheduler.cancel(context, notification.id)
                            dao.update(
                                notification.copy(
                                    deleted = true,
                                    enabled = false,
                                    updatedAt = System.currentTimeMillis(),
                                    dirty = true
                                )
                            )
                            onChanged()
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    if (creating || editing != null) {
        CustomNotificationEditorSheet(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { draft ->
                scope.launch {
                    val id = if (draft.id == 0) dao.insert(draft).toInt() else {
                        dao.update(draft); draft.id
                    }
                    dao.getById(id)?.let { CustomNotificationScheduler.schedule(context, it) }
                    creating = false
                    editing = null
                    onChanged()
                }
            }
        )
    }
}

@Composable
private fun CustomNotificationCard(
    notification: CustomNotification,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    PremiumCard(
        onClick = onEdit,
        contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.NotificationsNone, MaterialTheme.colorScheme.primary, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notification.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    customScheduleSummary(context, notification),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = notification.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    "Delete notification",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CustomNotificationEditorSheet(
    initial: CustomNotification?,
    onDismiss: () -> Unit,
    onSave: (CustomNotification) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var message by remember(initial?.id) { mutableStateOf(initial?.message.orEmpty()) }
    var mode by remember(initial?.id) { mutableStateOf(initial?.repeatMode ?: CustomNotificationTimeCalculator.ONCE) }
    var hour by remember(initial?.id) { mutableIntStateOf(initial?.hour ?: 9) }
    var minute by remember(initial?.id) { mutableIntStateOf(initial?.minute ?: 0) }
    var daysMask by remember(initial?.id) { mutableIntStateOf(initial?.daysMask ?: ALL_DAYS) }
    var dayOfMonth by remember(initial?.id) { mutableIntStateOf(initial?.dayOfMonth ?: 1) }
    var dateEpochDay by remember(initial?.id) { mutableStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }

    FormSheet(if (initial == null) "New notification" else "Edit notification", onDismiss) {
        MomentumTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true
        )
        MomentumTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message (optional)") },
            minLines = 2,
            maxLines = 4
        )
        SectionHeader("Repeats")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            customModes.forEach { (value, label) ->
                MomentumChip(selected = mode == value, onClick = { mode = value }, label = label)
            }
        }
        MomentumOutlinedButton(
            text = DateFormat.getTimeFormat(context).format(
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                }.time
            ),
            onClick = {
                TimePickerDialog(
                    context,
                    { _, selectedHour, selectedMinute -> hour = selectedHour; minute = selectedMinute },
                    hour, minute, DateFormat.is24HourFormat(context)
                ).show()
            },
            icon = Icons.Rounded.Alarm
        )
        when (mode) {
            CustomNotificationTimeCalculator.ONCE -> MomentumOutlinedButton(
                text = customDateLabel(context, dateEpochDay),
                onClick = { openCustomDatePicker(context, dateEpochDay) { dateEpochDay = it } },
                icon = Icons.Rounded.Event
            )

            CustomNotificationTimeCalculator.WEEKLY -> {
                SectionHeader("On")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    customDays.forEach { (day, label) ->
                        val bit = 1 shl (day - 1)
                        MomentumChip(
                            selected = (daysMask and bit) != 0,
                            onClick = { daysMask = daysMask xor bit },
                            label = label
                        )
                    }
                }
            }

            CustomNotificationTimeCalculator.MONTHLY -> {
                SectionHeader("Day of month")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (day in 1..31) {
                        MomentumChip(selected = dayOfMonth == day, onClick = { dayOfMonth = day }, label = "$day")
                    }
                }
            }
        }
        PrimaryButton(
            text = if (initial == null) "Schedule notification" else "Save changes",
            enabled = title.isNotBlank(),
            onClick = {
                if (title.isNotBlank()) {
                    val base = initial ?: CustomNotification(title = "")
                    onSave(
                        base.copy(
                            title = title.trim(),
                            message = message.trim(),
                            repeatMode = mode,
                            hour = hour,
                            minute = minute,
                            dateEpochDay = if (mode == CustomNotificationTimeCalculator.ONCE) dateEpochDay else base.dateEpochDay,
                            daysMask = if (mode == CustomNotificationTimeCalculator.WEEKLY) daysMask else ALL_DAYS,
                            dayOfMonth = dayOfMonth,
                            updatedAt = System.currentTimeMillis(),
                            deleted = false,
                            dirty = true
                        )
                    )
                }
            }
        )
    }
}

private fun openCustomDatePicker(context: Context, epochDay: Long, onPick: (Long) -> Unit) {
    val date = LocalDate.ofEpochDay(epochDay)
    DatePickerDialog(
        context,
        { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day).toEpochDay()) },
        date.year, date.monthValue - 1, date.dayOfMonth
    ).show()
}

private fun customDateLabel(context: Context, epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, date.year)
        set(Calendar.MONTH, date.monthValue - 1)
        set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
    }
    return DateFormat.getDateFormat(context).format(calendar.time)
}

private fun customScheduleSummary(context: Context, notification: CustomNotification): String {
    val time = DateFormat.getTimeFormat(context).format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, notification.hour); set(Calendar.MINUTE, notification.minute)
        }.time
    )
    return when (notification.repeatMode) {
        CustomNotificationTimeCalculator.DAILY -> "Daily · $time"
        CustomNotificationTimeCalculator.WEEKLY -> {
            val days = customDays.filter { (it.first - 1).let { bit -> (notification.daysMask shr bit) and 1 == 1 } }
                .joinToString(", ") { it.second }
            "Weekly · ${days.ifBlank { "No days" }} · $time"
        }
        CustomNotificationTimeCalculator.MONTHLY -> "Monthly · day ${notification.dayOfMonth} · $time"
        else -> {
            val date = notification.dateEpochDay?.let { customDateLabel(context, it) } ?: "No date"
            "Once · $date · $time"
        }
    }
}
