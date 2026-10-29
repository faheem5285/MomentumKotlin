package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StreakChip(count: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Warning.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(16.dp), tint = Warning)
        Spacer(Modifier.width(4.dp))
        Text("$count", fontWeight = FontWeight.Bold, color = Warning, fontSize = 13.sp)
    }
}

@Composable
private fun DayChip(day: Long, selected: Boolean, complete: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dow = LocalDate.ofEpochDay(day).dayOfWeek.value
    val onSel = Color.White
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            DAY_NAMES[dow - 1], fontSize = 11.sp,
            color = if (selected) onSel.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${LocalDate.ofEpochDay(day).dayOfMonth}",
            fontWeight = FontWeight.Bold,
            color = if (selected) onSel else MaterialTheme.colorScheme.onSurface
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (complete) (if (selected) onSel else Positive) else Color.Transparent)
        )
    }
}

@Composable
private fun HabitRow(
    ui: HabitUi,
    done: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val h = ui.habit
    val color = habitColor(h.colorIndex)
    val bg by animateColorAsState(if (done) color else Color.Transparent, label = "chk")

    val cardColor by animateColorAsState(
        if (done) color.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        label = "habit-card"
    )
    Card(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (done) 0.dp else 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(habitIcon(h.icon), color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    h.name,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (h.description.isNotBlank()) {
                    Text(
                        h.description,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(13.dp), tint = color)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${TIME_LABELS[h.timeOfDay]}  ·  ${scheduleText(h.daysMask)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .border(2.dp, if (done) color else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) Icon(Icons.Rounded.Check, null, Modifier.size(19.dp), tint = Color.White)
                }
                if (ui.current > 0) StreakChip(ui.current)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HabitDetailSheet(ui: HabitUi, onDismiss: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit, onManageReminders: () -> Unit) {
    val h = ui.habit
    val color = habitColor(h.colorIndex)
    val t = today()

    FormSheet(h.name, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(habitIcon(h.icon), color, 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "${scheduleText(h.daysMask)} · ${TIME_LABELS[h.timeOfDay]}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "What to do",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(h.description.ifBlank { "No instructions yet. Tap Edit to describe exactly what to do." })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Streak", "${ui.current}", Modifier.weight(1f))
            MiniStat("Best", "${ui.best}", Modifier.weight(1f))
            MiniStat("Check-ins", "${ui.days.size}", Modifier.weight(1f))
        }
        Card(
            Modifier.fillMaxWidth().clickable(onClick = onManageReminders),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.NotificationsActive, color, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reminders", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ui.reminders.isEmpty()) "Add times and days for this habit"
                        else "${ui.reminders.size} reminder time${if (ui.reminders.size == 1) "" else "s"} set",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        Text(
            "Last 14 days",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (t - 13..t).forEach { d ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                d in ui.days -> color
                                isScheduled(h.daysMask, d) -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            }
                        )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            OutlinedButton(onClick = onArchive, modifier = Modifier.weight(1f)) { Text("Archive") }
        }
    }
}

@Composable
fun TodayScreen(vm: HabitViewModel, onBrowseTemplates: () -> Unit) {
    val all by vm.all.collectAsState()
    val todayDay = today()
    var selected by remember { mutableLongStateOf(todayDay) }
    var detailId by remember { mutableStateOf<Int?>(null) }
    var reminderHabitId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<HabitUi?>(null) }
    var creating by remember { mutableStateOf(false) }

    val active = all.filter { !it.habit.archived }
    fun scheduledOn(d: Long): List<HabitUi> =
        active.filter { isScheduled(it.habit.daysMask, d) && it.habit.createdEpochDay <= d }

    val list = scheduledOn(selected)
    val doneCount = list.count { selected in it.days }
    val progress = if (list.isEmpty()) 0f else doneCount.toFloat() / list.size
    val isToday = selected == todayDay
    val weekScheduled = (todayDay - 6..todayDay).sumOf { d -> scheduledOn(d).size }
    val weekDone = (todayDay - 6..todayDay).sumOf { d -> scheduledOn(d).count { d in it.days } }

    detailId?.let { id ->
        all.firstOrNull { it.habit.id == id }?.let { ui ->
            HabitDetailSheet(
                ui,
                onDismiss = { detailId = null },
                onEdit = { editing = ui; detailId = null },
                onArchive = { vm.setArchived(ui.habit, true); detailId = null },
                onManageReminders = { reminderHabitId = ui.habit.id; detailId = null }
            )
        }
    }
    reminderHabitId?.let { id ->
        all.firstOrNull { it.habit.id == id }?.let { ui ->
            HabitReminderManagerSheet(
                habit = ui.habit,
                reminders = ui.reminders,
                onDismiss = { reminderHabitId = null },
                onAdd = { hour, minute, days -> vm.addReminder(ui.habit, hour, minute, days) },
                onUpdate = vm::updateReminder,
                onDelete = vm::deleteReminder
            )
        }
    }
    editing?.let { ui ->
        HabitEditorSheet(
            ui.habit,
            onDismiss = { editing = null },
            onSave = { habit, reminders -> vm.saveWithReminders(habit, reminders) },
            initialReminders = ui.reminders
        )
    }
    if (creating) {
        HabitEditorSheet(null, onDismiss = { creating = false }, onSave = { habit, reminders -> vm.saveWithReminders(habit, reminders) })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isToday) greeting() else "Looking back",
                                fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                LocalDate.ofEpochDay(selected).format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    list.isEmpty() -> "Nothing scheduled"
                                    doneCount == list.size -> "All done 🎉"
                                    else -> "${list.size - doneCount} left to do"
                                },
                                fontSize = 15.sp
                            )
                        }
                        ProgressRing(progress, "$doneCount/${list.size}")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (todayDay - 6..todayDay).forEach { d ->
                        val sched = scheduledOn(d)
                        DayChip(
                            day = d,
                            selected = d == selected,
                            complete = sched.isNotEmpty() && sched.all { d in it.days },
                            modifier = Modifier.weight(1f)
                        ) { selected = d }
                    }
                }
            }
            if (active.isNotEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("This week", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (weekScheduled == 0) "Your weekly progress will show here"
                                        else "$weekDone of $weekScheduled check-ins completed",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (weekScheduled == 0) "—" else "${weekDone * 100 / weekScheduled}%",
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            ProgressBar(
                                if (weekScheduled == 0) 0f else weekDone.toFloat() / weekScheduled,
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.surfaceVariant,
                                height = 6.dp
                            )
                        }
                    }
                }
            }
            if (active.isEmpty()) {
                item {
                    Column {
                        EmptyState(Icons.Rounded.CheckCircle, "No habits yet", "Start from 100 ready-made habits")
                        Button(onClick = onBrowseTemplates, modifier = Modifier.fillMaxWidth()) {
                            Text("Browse the library")
                        }
                    }
                }
            } else if (list.isEmpty()) {
                item { EmptyState(Icons.Rounded.WbSunny, "Rest day", "No habits are scheduled for this day") }
            }

            val groups = listOf(1 to "Morning", 2 to "Afternoon", 3 to "Evening", 0 to "Anytime")
            groups.forEach { (t, label) ->
                val g = list.filter { it.habit.timeOfDay == t }
                    .sortedWith(compareBy<HabitUi> { selected in it.days }.thenBy { it.habit.name.lowercase() })
                if (g.isNotEmpty()) {
                    item(key = "header-$t") {
                        SectionLabel("$label · ${g.count { selected in it.days }}/${g.size}")
                    }
                    items(g, key = { it.habit.id }) { ui ->
                        HabitRow(
                            ui = ui,
                            done = selected in ui.days,
                            onToggle = { vm.toggle(ui, selected) },
                            onOpen = { detailId = ui.habit.id },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New habit") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}
