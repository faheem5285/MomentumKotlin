package com.faheem.habits.features.home
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R
import com.faheem.habits.features.settings.isModuleVisible
import com.faheem.habits.features.habits.isScheduled
import com.faheem.habits.features.habits.greeting
import com.faheem.habits.features.screen_time.durationText
import com.faheem.habits.features.screen_time.todayScreenMinutes


import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class HomeState(
    val habitsTotal: Int = 0,
    val habitsDone: Int = 0,
    val monthSpent: Double = 0.0,
    val loanNet: Double = 0.0,
    val openLoans: Int = 0,
    val fileCount: Int = 0,
    val tasksDueToday: Int = 0,
    val overdueTasks: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    private val overview = combine(
        db.habitDao().getHabits(),
        db.habitDao().getCompletions(),
        db.expenseDao().getAll(),
        db.loanDao().getAll(),
        db.fileDao().getAll()
    ) { habits, comps, exps, loans, files ->
        withContext(Dispatchers.Default) {
            val day = today()
            val active = habits.filter { !it.archived && isScheduled(it.daysMask, day) }
            val doneIds = comps.asSequence().filter { it.epochDay == day }.map { it.habitId }.toSet()
            val month = YearMonth.now()
            val spent = exps.asSequence()
                .filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
                .sumOf { it.amount }
            val open = loans.filter { !it.settled }
            HomeState(
                habitsTotal = active.size,
                habitsDone = active.count { it.id in doneIds },
                monthSpent = spent,
                loanNet = open.filter { it.lent }.sumOf { it.amount } - open.filter { !it.lent }.sumOf { it.amount },
                openLoans = open.size,
                fileCount = files.size
            )
        }
    }

    val state: StateFlow<HomeState> = combine(overview, db.toDoDao().getAll()) { base, tasks ->
        val day = today()
        base.copy(
            tasksDueToday = tasks.count { !it.completed && it.dueEpochDay == day },
            overdueTasks = tasks.count { !it.completed && it.dueEpochDay < day }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private data class TileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val module: AppModule?   // null = locked / coming soon
)

@Composable
fun HomeScreen(onOpen: (AppModule) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    var screenText by remember { mutableStateOf("Tap to set up") }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) {
        val m = withContext(Dispatchers.IO) { todayScreenMinutes(ctx) }
        screenText = if (m == null) "Tap to set up" else "${durationText(m)} today"
    }

    val tiles = listOf(
        TileSpec(
            "Habits",
            if (s.habitsTotal == 0) "Start your first habit" else "${s.habitsDone} of ${s.habitsTotal} done today",
            AppModule.Habits.icon, AppModule.Habits.gradient, AppModule.Habits
        ),
        TileSpec(
            "To-dos",
            when {
                s.overdueTasks > 0 -> "${s.overdueTasks} overdue · ${s.tasksDueToday} today"
                s.tasksDueToday > 0 -> "${s.tasksDueToday} task${if (s.tasksDueToday == 1) "" else "s"} due today"
                else -> "Appointments and tasks by date"
            },
            AppModule.ToDos.icon, AppModule.ToDos.gradient, AppModule.ToDos
        ),
        TileSpec(
            "Money",
            if (s.monthSpent <= 0.0) "No spending yet" else "${money(s.monthSpent)} this month",
            AppModule.Money.icon, AppModule.Money.gradient, AppModule.Money
        ),
        TileSpec(
            "Loans",
            when {
                s.openLoans == 0 -> "All clear"
                s.loanNet >= 0 -> "${money(s.loanNet)} to receive"
                else -> "${money(-s.loanNet)} to pay"
            },
            AppModule.Loans.icon, AppModule.Loans.gradient, AppModule.Loans
        ),
        TileSpec(
            "Files",
            if (s.fileCount == 0) "Your file library" else "${s.fileCount} file${if (s.fileCount == 1) "" else "s"} saved",
            AppModule.Files.icon, AppModule.Files.gradient, AppModule.Files
        ),
        TileSpec("Screen time", screenText, AppModule.Screen.icon, AppModule.Screen.gradient, AppModule.Screen),
        TileSpec("Vault", "Private password manager", AppModule.Vault.icon, AppModule.Vault.gradient, AppModule.Vault)
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true }
    val deviceTools = listOf(
        TileSpec("Battery", "Live charging animation", AppModule.Battery.icon, AppModule.Battery.gradient, AppModule.Battery),
        TileSpec("Network", "Wi-Fi and mobile data", AppModule.Network.icon, AppModule.Network.gradient, AppModule.Network)
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true }
    val settingsTile = TileSpec("Settings", "Manage modules and preferences", AppModule.Settings.icon, AppModule.Settings.gradient, AppModule.Settings)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("Momentum", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (s.habitsTotal == 0) "Open Habits to begin"
                            else if (s.habitsDone == s.habitsTotal) "All habits done today 🎉"
                            else "${s.habitsTotal - s.habitsDone} habits left today",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    ProgressRing(
                        if (s.habitsTotal == 0) 0f else s.habitsDone.toFloat() / s.habitsTotal,
                        "${s.habitsDone}/${s.habitsTotal}"
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TodaySummaryCard(
                    title = "Habits",
                    value = "${s.habitsDone}/${s.habitsTotal}",
                    detail = if (s.habitsTotal == 0) "Ready to start" else if (s.habitsDone == s.habitsTotal) "All done today" else "Completed today",
                    icon = AppModule.Habits.icon,
                    tint = AppModule.Habits.gradient.first(),
                    modifier = Modifier.weight(1f)
                )
                TodaySummaryCard(
                    title = "To-dos",
                    value = s.tasksDueToday.toString(),
                    detail = if (s.overdueTasks > 0) "${s.overdueTasks} overdue" else "Due today",
                    icon = AppModule.ToDos.icon,
                    tint = AppModule.ToDos.gradient.first(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { SectionLabel("Your modules") }
        items(tiles.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { spec -> ModuleTile(spec, Modifier.weight(1f)) { spec.module?.let(onOpen) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { SectionLabel("Device tools") }
        items(deviceTools.chunked(2)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { spec -> ModuleTile(spec, Modifier.weight(1f)) { spec.module?.let(onOpen) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { SectionLabel("Manage app") }
        item { ModuleTile(settingsTile, Modifier.fillMaxWidth()) { onOpen(AppModule.Settings) } }
    }
}

@Composable
private fun ModuleTile(spec: TileSpec, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(156.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(spec.gradient))
            .clickable(
                enabled = spec.module != null,
                role = Role.Button,
                onClickLabel = "Open ${spec.title}",
                onClick = onClick
            )
            .semantics {
                contentDescription = "${spec.title}. ${spec.subtitle}. Open module."
            }
    ) {
        Icon(
            spec.icon, null,
            Modifier.size(112.dp).align(Alignment.BottomEnd).offset(x = 24.dp, y = 24.dp),
            tint = Color.White.copy(alpha = 0.14f)
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(spec.icon, null, Modifier.size(24.dp), tint = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(spec.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(spec.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(title: String, value: String, detail: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(124.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBubble(icon, tint, 32.dp)
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
