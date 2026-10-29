package com.faheem.habits

import android.app.Application
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class HabitUi(val habit: Habit, val doneToday: Boolean, val streak: Int)

private fun streakOf(days: Set<Long>, today: Long): Int {
    var d = if (today in days) today else today - 1
    var n = 0
    while (d in days) { n++; d-- }
    return n
}

class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).habitDao()

    val habits: StateFlow<List<HabitUi>> =
        combine(dao.getHabits(), dao.getCompletions()) { habits, completions ->
            val today = today()
            val byHabit = completions.groupBy({ it.habitId }, { it.epochDay }).mapValues { it.value.toSet() }
            habits.map { h ->
                val days = byHabit[h.id] ?: emptySet()
                HabitUi(h, today in days, streakOf(days, today))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { dao.insertHabit(Habit(name = name.trim())) }
    }

    fun toggle(item: HabitUi) {
        val day = today()
        viewModelScope.launch {
            if (item.doneToday) dao.deleteCompletion(item.habit.id, day)
            else dao.insertCompletion(Completion(item.habit.id, day))
        }
    }

    fun remove(item: HabitUi) {
        viewModelScope.launch { dao.deleteHabit(item.habit) }
    }
}

fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun HabitScreen(vm: HabitViewModel = viewModel()) {
    val habits by vm.habits.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    val total = habits.size
    val done = habits.count { it.doneToday }
    val progress = if (total == 0) 0f else done.toFloat() / total

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        FormSheet("New habit", onDismiss = { showAdd = false }) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Habit name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            BigButton("Add habit") {
                vm.add(name)
                showAdd = false
            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(greeting(), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    total == 0 -> "Add your first habit"
                                    done == total -> "All done today 🎉"
                                    else -> "${total - done} left today"
                                },
                                fontSize = 15.sp
                            )
                        }
                        ProgressRing(progress, "$done/$total")
                    }
                }
            }
            item { SectionLabel("Your habits") }
            if (habits.isEmpty()) {
                item { EmptyState(Icons.Rounded.CheckCircle, "No habits yet", "Tap “New habit” to start") }
            }
            items(habits, key = { it.habit.id }) { item ->
                HabitCard(item, onToggle = { vm.toggle(item) }, onDelete = { vm.remove(item) }, modifier = Modifier.animateItem())
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New habit") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun HabitCard(item: HabitUi, onToggle: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val bg by animateColorAsState(if (item.doneToday) primary else Color.Transparent, label = "check")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(2.dp, if (item.doneToday) primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (item.doneToday) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                item.habit.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.doneToday) TextDecoration.LineThrough else null,
                color = if (item.doneToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (item.streak > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Warning.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, Modifier.size(16.dp), tint = Warning)
                    Spacer(Modifier.width(4.dp))
                    Text("${item.streak}", fontWeight = FontWeight.Bold, color = Warning, fontSize = 13.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
