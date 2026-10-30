package com.faheem.habits.features.todos
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.Manifest
import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val todoCategories = listOf("Personal", "Health", "Work", "Errands", "Family", "Other")
private val priorityLabels = listOf("Low", "Normal", "High", "Urgent")

class ToDoViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).toDoDao()
    val items = dao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(item: ToDoItem) = viewModelScope.launch {
        val id = if (item.id == 0) dao.insert(item).toInt() else {
            dao.update(item)
            item.id
        }
        dao.getById(id)?.let { ToDoReminderScheduler.schedule(getApplication(), it) }
    }

    fun toggle(item: ToDoItem) = viewModelScope.launch {
        val changed = item.copy(completed = !item.completed)
        dao.update(changed)
        if (changed.completed) ToDoReminderScheduler.cancel(getApplication(), changed.id)
    }

    fun delete(item: ToDoItem) = viewModelScope.launch {
        ToDoReminderScheduler.cancel(getApplication(), item.id)
        dao.delete(item)
    }
}

@Composable
fun ToDoScreen(vm: ToDoViewModel = viewModel()) {
    val tasks by vm.items.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ToDoItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val openTasks = tasks.filterNot { it.completed }
    val today = LocalDate.now()
    val overdueCount = openTasks.count { LocalDate.ofEpochDay(it.dueEpochDay).isBefore(today) }
    val dueTodayCount = openTasks.count { LocalDate.ofEpochDay(it.dueEpochDay) == today }
    val filtered = openTasks.filter { task ->
        val date = LocalDate.ofEpochDay(task.dueEpochDay)
        val matchesFilter = when (filter) {
            "Today" -> date == today
            "Upcoming" -> date.isAfter(today)
            "Overdue" -> date.isBefore(today)
            else -> true
        }
        matchesFilter && (task.title.contains(query, true) || task.notes.contains(query, true) || task.category.contains(query, true))
    }.sortedWith(compareBy<ToDoItem> { it.dueEpochDay }.thenByDescending { it.priority }.thenByDescending { it.id })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Make room for what matters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${openTasks.size} open · ${tasks.count { it.completed }} completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (openTasks.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            overdueCount > 0 -> "$overdueCount overdue · $dueTodayCount due today"
                            dueTodayCount > 0 -> "$dueTodayCount due today · keep your momentum"
                            else -> "You're up to date · keep your momentum"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add task")
            }
        }
        if (tasks.any { !it.completed && it.reminderHour != null } && !MomentumNotificationSettings.masterEnabled(context)) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Task reminders are paused in notification settings", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { MomentumNotificationSettings.setMasterEnabled(context, true) }) { Text("Turn on") }
                }
            }
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Tasks", "Calendar", "Done").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }

        when (tab) {
            0 -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search tasks, notes, categories") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear task search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("All", "Today", "Upcoming", "Overdue").forEach { label ->
                        FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) })
                    }
                }
                TaskList(filtered, onEdit = { editing = it }, onToggle = vm::toggle, onDelete = vm::delete)
            }
            1 -> {
                CalendarAgenda(
                    month = month,
                    selectedDate = selectedDate,
                    tasks = tasks,
                    onPrevious = {
                        month = month.minusMonths(1)
                        selectedDate = month.atDay(selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
                    },
                    onNext = {
                        month = month.plusMonths(1)
                        selectedDate = month.atDay(selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth()))
                    },
                    onSelect = { selectedDate = it },
                    onEdit = { editing = it },
                    onToggle = vm::toggle,
                    onDelete = vm::delete
                )
            }
            else -> TaskList(tasks.filter { it.completed }.sortedByDescending { it.dueEpochDay }, onEdit = { editing = it }, onToggle = vm::toggle, onDelete = vm::delete)
        }
    }

    if (creating) ToDoEditorSheet(null, onDismiss = { creating = false }, onSave = { vm.save(it); creating = false })
    editing?.let { task -> ToDoEditorSheet(task, onDismiss = { editing = null }, onSave = { vm.save(it); editing = null }) }
}

@Composable
private fun TaskList(tasks: List<ToDoItem>, onEdit: (ToDoItem) -> Unit, onToggle: (ToDoItem) -> Unit, onDelete: (ToDoItem) -> Unit) {
    if (tasks.isEmpty()) {
        EmptyState(Icons.Rounded.CalendarMonth, "Nothing here yet", "Add a task with a date to keep your plans in one place.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tasks, key = { it.id }) { task -> ToDoCard(task, onClick = { onEdit(task) }, onToggle = { onToggle(task) }, onDelete = { onDelete(task) }) }
    }
}

@Composable
private fun CalendarAgenda(
    month: YearMonth, selectedDate: LocalDate, tasks: List<ToDoItem>, onPrevious: () -> Unit, onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit, onEdit: (ToDoItem) -> Unit, onToggle: (ToDoItem) -> Unit, onDelete: (ToDoItem) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevious) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext) { Text("›") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val totalCells = ((firstOffset + month.lengthOfMonth() + 6) / 7) * 7
        (0 until totalCells step 7).forEach { rowStart ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (rowStart until rowStart + 7).forEach { cell ->
                    val dayNum = cell - firstOffset + 1
                    if (dayNum in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayNum)
                        val hasTasks = tasks.any { it.dueEpochDay == date.toEpochDay() }
                        Box(
                            Modifier.weight(1f).padding(vertical = 2.dp).height(42.dp).clip(CircleShape)
                                .background(if (date == selectedDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("$dayNum", color = if (date == selectedDate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                if (hasTasks) Box(Modifier.size(4.dp).clip(CircleShape).background(if (date == selectedDate) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                            }
                        }
                    } else Spacer(Modifier.weight(1f).height(42.dp))
                }
            }
        }
        Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        TaskList(tasks.filter { it.dueEpochDay == selectedDate.toEpochDay() }.sortedByDescending { it.priority }, onEdit, onToggle, onDelete)
    }
}

@Composable
private fun ToDoCard(task: ToDoItem, onClick: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val date = LocalDate.ofEpochDay(task.dueEpochDay)
    val today = LocalDate.now()
    val dateLabel = when {
        task.completed -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
        date.isBefore(today) -> "Overdue · ${date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))}"
        date == today -> "Today · ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
    }
    val priorityColor = when (task.priority) {
        3 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = task.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp)) {
                Text(task.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.Event, null, Modifier.size(15.dp), tint = if (!task.completed && date.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = if (!task.completed && date.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text("· ${task.category} · ${priorityLabels[task.priority.coerceIn(0, 3)]}", style = MaterialTheme.typography.labelSmall, color = priorityColor)
                    if (task.reminderHour != null && task.reminderMinute != null) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete task", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ToDoEditorSheet(initial: ToDoItem?, onDismiss: () -> Unit, onSave: (ToDoItem) -> Unit) {
    val context = LocalContext.current
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var date by remember(initial?.id) { mutableStateOf(LocalDate.ofEpochDay(initial?.dueEpochDay ?: today())) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category ?: "Personal") }
    var priority by remember(initial?.id) { mutableIntStateOf(initial?.priority ?: 1) }
    var reminderHour by remember(initial?.id) { mutableStateOf(initial?.reminderHour) }
    var reminderMinute by remember(initial?.id) { mutableStateOf(initial?.reminderMinute) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) ToDoReminderScheduler.rescheduleAll(context)
    }

    FormSheet(if (initial == null) "New task" else "Edit task", onDismiss) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task or appointment") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes, location, or details") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Text("Date", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CalendarMonth, null)
            Spacer(Modifier.width(8.dp))
            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")))
        }
        Text("Category", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            todoCategories.take(3).forEach { value -> FilterChip(category == value, onClick = { category = value }, label = { Text(value) }) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            todoCategories.drop(3).forEach { value -> FilterChip(category == value, onClick = { category = value }, label = { Text(value) }) }
        }
        Text("Priority", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            priorityLabels.forEachIndexed { index, value -> FilterChip(priority == index, onClick = { priority = index }, label = { Text(value) }) }
        }
        Text("Reminder", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    reminderHour = hour; reminderMinute = minute
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }, reminderHour ?: 9, reminderMinute ?: 0, DateFormat.is24HourFormat(context)).show()
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.NotificationsActive, null)
                Spacer(Modifier.width(6.dp))
                Text(if (reminderHour != null && reminderMinute != null) String.format("%02d:%02d", reminderHour, reminderMinute) else "Add time")
            }
            if (reminderHour != null) TextButton(onClick = { reminderHour = null; reminderMinute = null }) { Text("Remove") }
        }
        if (reminderHour != null && !MomentumNotificationSettings.masterEnabled(context)) {
            Text("Turn on the app’s reminder notifications to receive this alert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Past dates are allowed for your records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Save task") {
            if (title.isNotBlank()) {
                onSave((initial ?: ToDoItem(title = "", dueEpochDay = date.toEpochDay())).copy(
                    title = title.trim(), notes = notes.trim(), dueEpochDay = date.toEpochDay(),
                    category = category, priority = priority, reminderHour = reminderHour, reminderMinute = reminderMinute
                ))
            }
        }
    }
}
