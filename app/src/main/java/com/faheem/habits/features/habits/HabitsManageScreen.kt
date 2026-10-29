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
        AlertDialog(
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
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Text("Manage habits", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                    Text("${active.size} active", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${archived.size} archived · edit, reschedule or archive any habit",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find a habit") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { SectionLabel("Active") }
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
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text("Archived · ${archived.size}")
                        Icon(if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
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

@Composable
private fun ManageCard(ui: HabitUi, modifier: Modifier, actions: @Composable RowScope.() -> Unit) {
    val h = ui.habit
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(habitIcon(h.icon), habitColor(h.colorIndex), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${scheduleText(h.daysMask)} · ${TIME_LABELS[h.timeOfDay]}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            actions()
        }
    }
}
