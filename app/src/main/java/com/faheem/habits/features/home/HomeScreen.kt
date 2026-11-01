@file:Suppress("FunctionNaming", "LongParameterList")

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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@Immutable
data class HomeState(
    val habitsTotal: Int = 0,
    val habitsDone: Int = 0,
    val monthSpent: Long = 0L,
    val loanNet: Long = 0L,
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
                .sumOf { it.amountMinorUnits }
            val open = loans.filter { !it.settled }
            HomeState(
                habitsTotal = active.size,
                habitsDone = active.count { it.id in doneIds },
                monthSpent = spent,
                loanNet = open.filter { it.lent }.sumOf { it.amountMinorUnits } - open.filter { !it.lent }.sumOf { it.amountMinorUnits },
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

@Immutable
private data class TileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val module: AppModule?   // null = locked / coming soon
)

@Composable
fun HomeScreen(onOpen: (AppModule) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var screenText by remember { mutableStateOf("Tap to set up") }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) {
        val m = withContext(Dispatchers.IO) { todayScreenMinutes(ctx) }
        screenText = if (m == null) "Tap to set up" else "${durationText(m)} today"
    }

    val tiles = remember(s, screenText, ctx) { listOf(
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
            if (s.monthSpent <= 0L) "No spending yet" else "${money(s.monthSpent)} this month",
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
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true } }
    val deviceTools = remember(ctx) { listOf(
        TileSpec("Battery", "Live charging animation", AppModule.Battery.icon, AppModule.Battery.gradient, AppModule.Battery),
        TileSpec("Network", "Wi-Fi and mobile data", AppModule.Network.icon, AppModule.Network.gradient, AppModule.Network)
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true } }
    val settingsTile = TileSpec("Settings", "Manage modules and preferences", AppModule.Settings.icon, AppModule.Settings.gradient, AppModule.Settings)

    ScreenBackground(tint = BrandGradient.first()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HomeHero(s) { onOpen(it) } }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TodaySummaryCard(
                        title = "Habits",
                        value = "${s.habitsDone}/${s.habitsTotal}",
                        detail = if (s.habitsTotal == 0) "Ready to start" else if (s.habitsDone == s.habitsTotal) "All done today" else "Completed today",
                        icon = AppModule.Habits.icon,
                        tint = AppModule.Habits.gradient.last(),
                        progress = if (s.habitsTotal == 0) 0f else s.habitsDone.toFloat() / s.habitsTotal,
                        modifier = Modifier.weight(1f)
                    )
                    TodaySummaryCard(
                        title = "To-dos",
                        value = s.tasksDueToday.toString(),
                        detail = if (s.overdueTasks > 0) "${s.overdueTasks} overdue" else "Due today",
                        icon = AppModule.ToDos.icon,
                        tint = AppModule.ToDos.gradient.last(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item { SectionHeader("Your space", subtitle = "Everything in one place") }
            items(
                items = tiles.chunked(2),
                key = { row -> row.joinToString("|") { it.title } },
                contentType = { "module-row" }
            ) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { spec -> ModuleTile(spec, Modifier.weight(1f)) { spec.module?.let(onOpen) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { SectionHeader("On this device") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    deviceTools.forEach { spec ->
                        DeviceRow(spec) { spec.module?.let(onOpen) }
                    }
                }
            }
            item {
                DeviceRow(settingsTile) { onOpen(AppModule.Settings) }
            }
        }
    }
}

@Composable
private fun HomeHero(s: HomeState, onOpen: (AppModule) -> Unit) {
    HeroCard(gradient = BrandGradient) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), fontSize = 14.sp, color = Color.White.copy(alpha = 0.82f))
                Text("Momentum", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )
            }
            ProgressRing(
                if (s.habitsTotal == 0) 0f else s.habitsDone.toFloat() / s.habitsTotal,
                "${s.habitsDone}/${s.habitsTotal}"
            )
        }
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        s.habitsTotal == 0 -> "Open Habits to begin"
                        s.habitsDone == s.habitsTotal -> "All habits done today. Beautiful work."
                        else -> "${s.habitsTotal - s.habitsDone} habit${if (s.habitsTotal - s.habitsDone == 1) "" else "s"} left today"
                    },
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                if (s.habitsTotal == 0) {
                    TextButton(onClick = { onOpen(AppModule.Habits) }) {
                        Text("Start", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleTile(spec: TileSpec, modifier: Modifier, onClick: () -> Unit) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    Box(
        modifier
            .height(142.dp)
            .shadow(
                if (LocalReduceEffects.current) 0.dp else MomentumTokens.elevationMedium,
                shape,
                clip = false,
                ambientColor = spec.gradient.first().copy(alpha = 0.55f),
                spotColor = spec.gradient.last().copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.cardTop, glass.cardBottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, spec.gradient.first()))
            .clickable(
                enabled = spec.module != null,
                role = Role.Button,
                onClickLabel = "Open ${spec.title}",
                onClick = onClick
            )
            .border(
                1.dp,
                neonEdgeBrush(glass, spec.gradient.first()),
                shape
            )
            .semantics {
                contentDescription = "${spec.title}. ${spec.subtitle}. Open module."
            }
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-48).dp)
                .size(132.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            spec.gradient.last().copy(alpha = 0.32f),
                            spec.gradient.first().copy(alpha = 0.12f),
                            spec.gradient.first().copy(alpha = 0f)
                        )
                    ),
                    CircleShape
                )
        )
        Column(
            Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(40.dp)
                        .shadow(
                            12.dp,
                            RoundedCornerShape(13.dp),
                            clip = false,
                            ambientColor = spec.gradient.first().copy(alpha = 0.75f),
                            spotColor = spec.gradient.last().copy(alpha = 0.50f)
                        )
                        .clip(RoundedCornerShape(13.dp))
                        .background(Brush.linearGradient(spec.gradient))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(spec.icon, null, Modifier.size(21.dp), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    spec.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    spec.subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(spec: TileSpec, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusLarge)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(
                if (LocalReduceEffects.current) 0.dp else MomentumTokens.elevationLow,
                shape,
                clip = false,
                ambientColor = spec.gradient.first().copy(alpha = 0.40f),
                spotColor = spec.gradient.last().copy(alpha = 0.25f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.cardTop, glass.cardBottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, spec.gradient.first()))
            .clickable(
                enabled = spec.module != null,
                role = Role.Button,
                onClickLabel = "Open ${spec.title}",
                onClick = onClick
            )
            .border(
                1.dp,
                neonEdgeBrush(glass, spec.gradient.first()),
                shape
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .shadow(
                    10.dp,
                    RoundedCornerShape(13.dp),
                    clip = false,
                    ambientColor = spec.gradient.first().copy(alpha = 0.65f),
                    spotColor = spec.gradient.last().copy(alpha = 0.45f)
                )
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(spec.gradient))
                .border(1.dp, Color.White.copy(alpha = 0.50f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(spec.icon, null, Modifier.size(20.dp), tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(spec.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TodaySummaryCard(
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    progress: Float = 0f
) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    Box(
        modifier
            .height(118.dp)
            .shadow(
                if (LocalReduceEffects.current) 0.dp else MomentumTokens.elevationLow,
                shape,
                clip = false,
                ambientColor = tint.copy(alpha = 0.50f),
                spotColor = tint.copy(alpha = 0.32f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.cardTop, glass.cardBottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, tint))
            .border(
                1.dp,
                neonEdgeBrush(glass, tint),
                shape
            )
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 26.dp, y = (-38).dp)
                .size(110.dp)
                .background(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0f))),
                    CircleShape
                )
        )
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBubble(icon, tint, 30.dp)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0f) {
                ProgressBar(progress, tint, MaterialTheme.colorScheme.surfaceVariant, height = 4.dp)
            }
        }
    }
}
