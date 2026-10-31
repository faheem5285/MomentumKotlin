package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Application
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

data class HabitUi(
    val habit: Habit,
    val days: Set<Long>,   // all days this habit was completed
    val current: Int,
    val best: Int,
    val reminders: List<HabitReminder> = emptyList()
)

@Suppress("TooManyFunctions")
class HabitViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).habitDao()

    val all: StateFlow<List<HabitUi>> =
        combine(dao.getHabits(), dao.getCompletions(), dao.getReminders()) { habits, completions, reminders ->
            withContext(Dispatchers.Default) {
                val t = today()
                val byHabit = completions.groupBy({ it.habitId }, { it.epochDay }).mapValues { it.value.toSet() }
                val remindersByHabit = reminders.groupBy { it.habitId }
                habits.map { h ->
                    val d = byHabit[h.id] ?: emptySet()
                    val s = computeStreaks(d, h.daysMask, t)
                    HabitUi(h, d, s.current, s.best, remindersByHabit[h.id].orEmpty())
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(habit: Habit) {
        if (habit.name.isBlank()) return
        viewModelScope.launch { dao.insertHabit(habit.copy(id = 0, createdEpochDay = today())) }
    }

    fun saveWithReminders(habit: Habit, drafts: List<HabitReminderDraft>) {
        if (habit.name.isBlank()) return
        viewModelScope.launch {
            val id = if (habit.id == 0) {
                dao.insertHabit(habit.copy(createdEpochDay = today())).toInt()
            } else {
                dao.updateHabit(habit)
                dao.getRemindersForHabit(habit.id).forEach { old ->
                    HabitReminderScheduler.cancel(getApplication(), old.id)
                    dao.deleteReminder(old)
                }
                habit.id
            }
            if (drafts.isNotEmpty()) {
                if (!MomentumNotificationSettings.masterEnabled(getApplication())) MomentumNotificationSettings.setMasterEnabled(getApplication(), true)
                if (!MomentumNotificationSettings.isEnabled(getApplication(), "habits")) MomentumNotificationSettings.setEnabled(getApplication(), "habits", true)
            }
            drafts.forEach { draft ->
                val reminderId = dao.insertReminder(
                    HabitReminder(
                        habitId = id,
                        hour = draft.hour,
                        minute = draft.minute,
                        daysMask = draft.daysMask,
                        dateEpochDay = draft.dateEpochDay
                    )
                )
                dao.getReminder(reminderId.toInt())?.let { HabitReminderScheduler.schedule(getApplication(), it) }
            }
        }
    }

    fun update(habit: Habit) {
        viewModelScope.launch { dao.updateHabit(habit) }
    }

    fun toggle(item: HabitUi, day: Long) {
        viewModelScope.launch {
            if (day in item.days) dao.deleteCompletion(item.habit.id, day)
            else dao.insertCompletion(
                Completion(
                    habitId = item.habit.id,
                    epochDay = day,
                    quantity = item.habit.targetQuantity,
                    durationSeconds = item.habit.timerMinutes * 60L,
                    completedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun skipDay(habit: Habit, day: Long) {
        viewModelScope.launch { dao.upsertSkip(HabitSkip(habit.id, day)) }
    }

    fun unskipDay(habit: Habit, day: Long) {
        viewModelScope.launch { dao.deleteSkip(habit.id, day) }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch {
            dao.updateHabit(habit.copy(archived = archived))
            dao.getRemindersForHabit(habit.id).forEach { reminder ->
                if (archived) HabitReminderScheduler.cancel(getApplication(), reminder.id)
                else HabitReminderScheduler.schedule(getApplication(), reminder)
            }
        }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch {
            dao.getRemindersForHabit(habit.id).forEach { HabitReminderScheduler.cancel(getApplication(), it.id) }
            dao.deleteHabit(habit)
        }
    }

    fun addReminder(habit: Habit, hour: Int, minute: Int, daysMask: Int) {
        viewModelScope.launch {
            val id = dao.insertReminder(HabitReminder(habitId = habit.id, hour = hour, minute = minute, daysMask = daysMask))
            dao.getReminder(id.toInt())?.let {
                if (!habit.archived) HabitReminderScheduler.schedule(getApplication(), it)
            }
        }
    }

    fun updateReminder(reminder: HabitReminder) {
        viewModelScope.launch {
            dao.updateReminder(reminder)
            if (dao.getHabit(reminder.habitId)?.archived == true) HabitReminderScheduler.cancel(getApplication(), reminder.id)
            else HabitReminderScheduler.schedule(getApplication(), reminder)
        }
    }

    fun deleteReminder(reminder: HabitReminder) {
        viewModelScope.launch {
            HabitReminderScheduler.cancel(getApplication(), reminder.id)
            dao.deleteReminder(reminder)
        }
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

private data class HabitTab(val label: String, val icon: ImageVector)

private val habitTabs = listOf(
    HabitTab("Today", Icons.Rounded.CheckCircle),
    HabitTab("Library", Icons.Rounded.Lightbulb),
    HabitTab("Insights", Icons.Rounded.EmojiEvents),
    HabitTab("Manage", Icons.Rounded.Tune)
)

@Composable
fun HabitScreen(vm: HabitViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "habitTab") { t ->
                when (t) {
                    0 -> TodayScreen(vm, onBrowseTemplates = { tab = 1 })
                    1 -> TemplatesScreen(vm)
                    2 -> InsightsScreen(vm)
                    else -> ManageScreen(vm)
                }
            }
        }
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        ) {
            habitTabs.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(t.icon, t.label) },
                    label = { Text(t.label, maxLines = 1, fontSize = 11.sp) }
                )
            }
        }
    }
}
