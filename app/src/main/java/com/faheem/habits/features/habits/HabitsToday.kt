@file:Suppress("FunctionNaming")

package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.faheem.habits.features.phase7.isHabitDue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun StreakChip(count: Int) {
    StatusPill(
        text = "$count day${if (count == 1) "" else "s"}",
        color = Warning,
        icon = Icons.Rounded.LocalFireDepartment
    )
}

@Composable
private fun DayChip(day: Long, selected: Boolean, complete: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dow = LocalDate.ofEpochDay(day).dayOfWeek.value
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusSmall + 2.dp)
    val background by animateColorAsState(
        if (selected) colors.primary else glass.cardTop,
        label = "day-bg"
    )
    Column(
        modifier
            .then(
                if (selected) {
                    Modifier.shadow(
                        10.dp,
                        shape,
                        clip = false,
                        ambientColor = colors.primary.copy(alpha = 0.55f),
                        spotColor = colors.primary.copy(alpha = 0.35f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(background)
            .border(
                1.dp,
                if (selected) Color.Transparent else glass.panelBorder,
                shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            DAY_NAMES[dow - 1],
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) colors.onPrimary.copy(alpha = 0.82f) else colors.onSurfaceVariant
        )
        Text(
            "${LocalDate.ofEpochDay(day).dayOfMonth}",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (selected) colors.onPrimary else colors.onSurface
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        complete && selected -> colors.onPrimary
                        complete -> Positive
                        else -> Color.Transparent
                    }
                )
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
    val colors = MaterialTheme.colorScheme
    PremiumCard(
        modifier = modifier,
        onClick = onOpen,
        accent = if (done) Positive else color.copy(alpha = 0.55f),
        shape = RoundedCornerShape(MomentumTokens.radiusMedium),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = if (done) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(habitIcon(h.icon), null, Modifier.size(20.dp), tint = if (done) Positive else color)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    h.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (done) Positive else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ui.current > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(13.dp), tint = Warning)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${ui.current} day streak",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Warning.copy(alpha = 0.95f)
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            CompletionToggle(checked = done, color = color, onClick = onToggle, size = 32.dp)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    GlassCard(
        modifier = modifier,
        level = GlassLevel.Low,
        shape = RoundedCornerShape(MomentumTokens.radiusSmall + 2.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HabitDetailSheet(ui: HabitUi, onDismiss: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit, onManageReminders: () -> Unit) {
    val h = ui.habit
    val color = habitColor(h.colorIndex)
    val t = today()
    val colors = MaterialTheme.colorScheme

    FormSheet(h.name, onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(habitIcon(h.icon), color, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    scheduleText(h.daysMask),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    "Best time · ${TIME_LABELS[h.timeOfDay]}",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant
                )
            }
            StatusPill(if (ui.current > 0) "${ui.current} day streak" else "New habit", color)
        }
        SectionHeader("Your plan")
        Text(
            h.description.ifBlank { "No instructions yet. Tap Edit to describe exactly what to do." },
            style = MaterialTheme.typography.bodyMedium,
            color = if (h.description.isBlank()) colors.onSurfaceVariant else colors.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("Streak", "${ui.current}", Modifier.weight(1f))
            MiniStat("Best", "${ui.best}", Modifier.weight(1f))
            MiniStat("Check-ins", "${ui.days.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Streaks count completed days on the schedule. Days off the schedule do not break them, and today stays open until the day ends.",
            fontSize = 11.sp,
            color = colors.onSurfaceVariant
        )
        PremiumCard(
            onClick = onManageReminders,
            accent = color,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Rounded.NotificationsActive, color, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reminders", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ui.reminders.isEmpty()) "Add times and days for this habit"
                        else "${ui.reminders.size} reminder time${if (ui.reminders.size == 1) "" else "s"} set",
                        fontSize = 12.sp, color = colors.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.outline)
            }
        }
        SectionHeader("Last 14 days")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (t - 13..t).forEach { d ->
                Box(
                    Modifier
                        .size(19.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), CircleShape)
                        .background(
                            when {
                                d in ui.days -> color
                                isHabitDue(h, d, ui.days) -> colors.surfaceVariant
                                else -> Color.Transparent
                            }
                        )
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MomentumOutlinedButton(
                text = "Edit",
                icon = Icons.Rounded.Edit,
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(50.dp)
            )
            MomentumOutlinedButton(
                text = "Archive",
                icon = Icons.Rounded.Archive,
                onClick = onArchive,
                modifier = Modifier.weight(1f).height(50.dp)
            )
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun TodayScreen(
    vm: HabitViewModel,
    onBrowseTemplates: () -> Unit,
    initialHabitId: Int? = null,
    openRequestToken: Long? = null
) {
    val all by vm.all.collectAsStateWithLifecycle()
    val todayDay = today()
    var selected by remember { mutableLongStateOf(todayDay) }
    var detailId by remember(initialHabitId, openRequestToken) { mutableStateOf(initialHabitId) }
    var reminderHabitId by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<HabitUi?>(null) }
    var creating by remember { mutableStateOf(false) }

    val active = remember(all) { all.filter { !it.habit.archived } }
    val weekSchedule = remember(active, todayDay) {
        (todayDay - 6..todayDay).associateWith { day ->
            active.filter { isHabitDue(it.habit, day, it.days) }
        }
    }
    val list = remember(weekSchedule, selected) { weekSchedule[selected].orEmpty() }
    val doneCount = list.count { selected in it.days }
    val progress = if (list.isEmpty()) 0f else doneCount.toFloat() / list.size
    val isToday = selected == todayDay
    val (weekScheduled, weekDone) = remember(weekSchedule) {
        weekSchedule.entries.fold(0 to 0) { totals, (day, habits) ->
            totals.first + habits.size to totals.second + habits.count { day in it.days }
        }
    }

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
            Modifier
                .fillMaxSize()
                .testTag("habits-today-scroll")
                .semantics { contentDescription = "Habits for today" },
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard(contentPadding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isToday) greeting() else "Looking back",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                            Text(
                                LocalDate.ofEpochDay(selected).format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    when {
                                        list.isEmpty() -> "Nothing scheduled"
                                        doneCount == list.size -> "All done — enjoy it"
                                        else -> "${list.size - doneCount} left to do"
                                    },
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                        ProgressRing(progress, "$doneCount/${list.size}")
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (todayDay - 6..todayDay).forEach { d ->
                        val sched = weekSchedule[d].orEmpty()
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
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Insights, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("This week", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (weekScheduled == 0) "Your weekly progress will show here"
                                    else "$weekDone of $weekScheduled check-ins completed",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (weekScheduled == 0) "—" else "${weekDone * 100 / weekScheduled}%",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        ProgressBar(
                            if (weekScheduled == 0) 0f else weekDone.toFloat() / weekScheduled,
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.surfaceVariant,
                            height = 6.dp
                        )
                    }
                }
            }
            if (active.isEmpty()) {
                item {
                    Column {
                        EmptyState(Icons.Rounded.CheckCircle, "No habits yet", "Start from 100 ready-made habits")
                        PrimaryButton("Browse the library", onBrowseTemplates, icon = Icons.Rounded.Lightbulb)
                    }
                }
            } else if (list.isEmpty()) {
                item { EmptyState(Icons.Rounded.WbSunny, "Rest day", "No habits are scheduled for this day") }
            }

            val sortedList = list.sortedWith(
                compareBy<HabitUi> { selected in it.days }.thenBy { it.habit.name.lowercase() }
            )
            items(sortedList, key = { it.habit.id }) { ui ->
                HabitRow(
                    ui = ui,
                    done = selected in ui.days,
                    onToggle = { vm.toggle(ui, selected) },
                    onOpen = { detailId = ui.habit.id },
                    modifier = Modifier.animateItem()
                )
            }
        }
        MomentumFab(
            text = "New habit",
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
        )
    }
}
