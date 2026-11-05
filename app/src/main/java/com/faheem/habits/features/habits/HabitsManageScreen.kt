package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun ManageScreen(vm: HabitViewModel) {
    val all by vm.all.collectAsState()
    var editing by remember { mutableStateOf<HabitUi?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Habit?>(null) }
    var reminderHabitId by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val totalActive = all.count { !it.habit.archived }
    val totalArchived = all.count { it.habit.archived }
    val matching = all.filter { query.isBlank() || it.habit.name.contains(query, ignoreCase = true) || it.habit.description.contains(query, ignoreCase = true) }
    val active = matching.filter { !it.habit.archived }.sortedBy { it.habit.name.lowercase() }
    val archived = matching.filter { it.habit.archived }.sortedBy { it.habit.name.lowercase() }

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
    confirmDelete?.let { h ->
        MomentumDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${h.name}”?") },
            text = { Text("Its whole history is deleted too. This cannot be undone. Archive it instead to keep the history.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(h); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard(contentPadding = 18.dp) {
                    Text("Manage habits", fontSize = 13.sp, color = Color.White.copy(alpha = 0.84f))
                    Text("$totalActive active", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                    Text(
                        "$totalArchived archived · edit, reschedule or archive any habit",
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            item {
                MomentumTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.height(52.dp),
                    placeholder = { Text("Find a habit") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            }
            item {
                SectionHeader(
                    if (query.isBlank()) "Active habits" else "Matching habits",
                    subtitle = "${active.size} habit${if (active.size == 1) "" else "s"}"
                )
            }
            if (active.isEmpty()) {
                item { EmptyState(Icons.Rounded.Tune, "Nothing to manage", "Add a habit or pick one from the Library") }
            }
            items(active, key = { it.habit.id }) { ui ->
                ManageCard(ui, Modifier.animateItem()) {
                    IconButton(onClick = { reminderHabitId = ui.habit.id }) { Icon(Icons.Rounded.Notifications, "Manage reminders") }
                    IconButton(onClick = { editing = ui }) { Icon(Icons.Rounded.Edit, "Edit") }
                    IconButton(onClick = { vm.setArchived(ui.habit, true) }) { Icon(Icons.Rounded.Archive, "Archive") }
                }
            }
            if (archived.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = { showArchived = !showArchived },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (query.isBlank()) "Archived · ${archived.size}" else "Matching archived · ${archived.size}")
                        Icon(if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    }
                }
            }
            if (showArchived) {
                items(archived, key = { it.habit.id }) { ui ->
                    ManageCard(ui, Modifier.animateItem().alpha(0.75f)) {
                        IconButton(onClick = { vm.setArchived(ui.habit, false) }) { Icon(Icons.Rounded.Unarchive, "Restore") }
                        IconButton(onClick = { confirmDelete = ui.habit }) {
                            Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        MomentumFab(
            text = "New habit",
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(MomentumTokens.screenPadding)
        )
    }
}

@Composable
private fun ManageCard(ui: HabitUi, modifier: Modifier, actions: @Composable RowScope.() -> Unit) {
    val h = ui.habit
    PremiumCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(habitIcon(h.icon), habitColor(h.colorIndex), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "${scheduleText(h.daysMask)} · ${TIME_LABELS[h.timeOfDay]}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            actions()
        }
    }
}
