@file:Suppress("FunctionNaming")

package com.faheem.habits.features.habits
import com.faheem.habits.core.data.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.features.habits.data.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.time.LocalTime

data class HabitUi(
    val habit: Habit,
    val days: Set<Long>,   // all days this habit was completed
    val current: Int,
    val best: Int,
    val reminders: List<HabitReminder> = emptyList()
)

@Suppress("TooManyFunctions")
@HiltViewModel
class HabitViewModel @Inject constructor(
    private val repository: HabitRepository
) : ViewModel() {

    val all: StateFlow<List<HabitUi>> =
        combine(repository.habits, repository.completions, repository.reminders) { habits, completions, reminders ->
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
        viewModelScope.launch { repository.add(habit) }
    }

    fun saveWithReminders(habit: Habit, drafts: List<HabitReminderDraft>) {
        viewModelScope.launch { repository.saveWithReminders(habit, drafts) }
    }

    fun update(habit: Habit) {
        viewModelScope.launch { repository.update(habit) }
    }

    fun toggle(item: HabitUi, day: Long) {
        viewModelScope.launch { repository.toggle(item.habit, day in item.days, day) }
    }

    fun skipDay(habit: Habit, day: Long) {
        viewModelScope.launch { repository.skipDay(habit, day) }
    }

    fun unskipDay(habit: Habit, day: Long) {
        viewModelScope.launch { repository.unskipDay(habit, day) }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(habit, archived) }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { repository.delete(habit) }
    }

    fun addReminder(habit: Habit, hour: Int, minute: Int, daysMask: Int) {
        viewModelScope.launch { repository.addReminder(habit, hour, minute, daysMask) }
    }

    fun updateReminder(reminder: HabitReminder) {
        viewModelScope.launch { repository.updateReminder(reminder) }
    }

    fun deleteReminder(reminder: HabitReminder) {
        viewModelScope.launch { repository.deleteReminder(reminder) }
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
fun HabitScreen(
    vm: HabitViewModel = hiltViewModel(),
    initialHabitId: Int? = null,
    openRequestToken: Long? = null
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = remember {
        habitTabs.map { MomentumTab(it.label, it.icon) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = tab, label = "habitTab") { t ->
                when (t) {
                    0 -> TodayScreen(
                        vm,
                        onBrowseTemplates = { tab = 1 },
                        initialHabitId = initialHabitId,
                        openRequestToken = openRequestToken
                    )
                    1 -> TemplatesScreen(vm)
                    2 -> InsightsScreen(vm)
                    else -> ManageScreen(vm)
                }
            }
        }
        MomentumTabs(tabs = tabs, selected = tab, onSelect = { tab = it })
    }
}
