@file:Suppress("FunctionNaming", "LongParameterList")

package com.faheem.habits.features.home
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R
import com.faheem.habits.features.settings.isModuleVisible
import com.faheem.habits.features.habits.isScheduled
import com.faheem.habits.features.habits.greeting
import com.faheem.habits.features.battery.BatteryLevelHistory
import com.faheem.habits.features.network.loadAppNetworkReport
import com.faheem.habits.features.network.trafficText
import com.faheem.habits.features.screen_time.durationText
import com.faheem.habits.features.screen_time.hasUsageAccess
import com.faheem.habits.features.screen_time.screenWeekMinutes


import android.app.Application
import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
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
import java.time.ZoneId
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
    val overdueTasks: Int = 0,
    val openTasks: Int = 0,
    val habitWeek: List<Float> = emptyList(),
    val spendWeek: List<Float> = emptyList(),
    val todoWeek: List<Float> = emptyList(),
    val vaultWeek: List<Float> = emptyList(),
    val vaultEntries: Int = 0,
    val fileMix: List<Pair<String, Float>> = emptyList(),
    val loanLent: Long = 0L,
    val loanBorrowed: Long = 0L,
    val syncPending: Int = 0,
    val syncHealthy: Int = 0,
    val syncTracked: Int = 0,
    val lifeRules: Int = 0,
    val pinnedRules: Int = 0
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
            val spentByDay = exps.asSequence()
                .filter { it.epochDay in (day - 6)..day }
                .groupBy { it.epochDay }
                .mapValues { entry -> entry.value.sumOf { it.amountMinorUnits }.toFloat() }
            val open = loans.filter { !it.settled }
            val habitWeek = (6L downTo 0L).map { offset ->
                val date = LocalDate.ofEpochDay(day).minusDays(offset).toEpochDay()
                val scheduledToday = habits
                    .filter { !it.archived && isScheduled(it.daysMask, date) }
                    .map { it.id }
                    .toSet()
                comps.asSequence()
                    .filter { it.epochDay == date && it.habitId in scheduledToday }
                    .count()
                    .toFloat()
            }
            HomeState(
                habitsTotal = active.size,
                habitsDone = active.count { it.id in doneIds },
                monthSpent = spent,
                loanNet = open.filter { it.lent }.sumOf { it.amountMinorUnits } -
                    open.filter { !it.lent }.sumOf { it.amountMinorUnits },
                openLoans = open.size,
                fileCount = files.size,
                habitWeek = habitWeek,
                spendWeek = (6L downTo 0L).map { spentByDay[day - it] ?: 0f },
                fileMix = files.groupBy { fileKind(it.mime) }
                    .map { (kind, items) -> kind to items.size.toFloat() }
                    .sortedByDescending { it.second },
                loanLent = open.filter { it.lent }.sumOf { it.amountMinorUnits },
                loanBorrowed = open.filter { !it.lent }.sumOf { it.amountMinorUnits }
            )
        }
    }

    val state: StateFlow<HomeState> = combine(
        overview,
        db.toDoDao().getAll(),
        db.vaultDao().observeUpdatedAt(),
        db.syncStateDao().observeAll(),
        db.lifeRuleDao().getAll()
    ) { base, tasks, vaultUpdates, syncStates, lifeRules ->
        val liveRules = lifeRules.filter { !it.deleted }
        val day = today()
        fun weekCounts(timestamps: List<Long>): List<Float> =
            (6L downTo 0L).map { offset ->
                val epoch = day - offset
                val begin = LocalDate.ofEpochDay(epoch)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = LocalDate.ofEpochDay(epoch + 1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                timestamps.count { it in begin until end }.toFloat()
            }
        base.copy(
            tasksDueToday = tasks.count { !it.completed && it.dueEpochDay == day },
            overdueTasks = tasks.count { !it.completed && it.dueEpochDay < day },
            openTasks = tasks.count { !it.completed },
            todoWeek = weekCounts(tasks.filter { it.completed }.map { it.updatedAt }),
            vaultWeek = weekCounts(vaultUpdates),
            vaultEntries = vaultUpdates.size,
            syncPending = syncStates.sumOf { it.pendingCount },
            syncHealthy = syncStates.count { it.lastSyncEpochMillis > 0 && it.lastError.isNullOrBlank() },
            syncTracked = syncStates.size,
            lifeRules = liveRules.size,
            pinnedRules = liveRules.count { it.pinned }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

private fun fileKind(mime: String): String = when {
    mime.startsWith("image/") -> "Images"
    mime.startsWith("video/") -> "Video"
    mime.startsWith("audio/") -> "Audio"
    mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") ||
        mime.contains("sheet") || mime.contains("presentation") -> "Docs"
    else -> "Other"
}

@Immutable
private sealed interface TileChart {
    data class Trend(val values: List<Float>) : TileChart
    data class Composition(val slices: List<ChartSlice>) : TileChart
    data class Gauge(val fraction: Float) : TileChart
}

@Immutable
private data class TileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val module: AppModule?,   // null = locked / coming soon
    val chart: TileChart? = null
) {
    val tint: Color get() = gradient.getOrElse(1) { gradient.first() }
}

@Immutable
private data class DeviceInsights(
    val screenWeek: List<Float> = emptyList(),
    val networkWeek: List<Float> = emptyList(),
    val networkLabel: String? = null,
    val batteryTrend: List<Float> = emptyList(),
    val batteryLevel: Int? = null
)

private fun loadDeviceInsights(context: Context): DeviceInsights {
    val screenWeek = if (hasUsageAccess(context)) {
        runCatching { screenWeekMinutes(context) }.getOrDefault(emptyList())
    } else {
        emptyList()
    }
    val network = runCatching { loadAppNetworkReport(context, 7) }.getOrNull()
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 1..100 }
    val trend = runCatching {
        BatteryLevelHistory.samples(context).map { it.percent.toFloat() }
    }.getOrDefault(emptyList())
    return DeviceInsights(
        screenWeek = screenWeek,
        networkWeek = network?.daily?.map { it.totalBytes.toFloat() } ?: emptyList(),
        networkLabel = network?.let { report ->
            trafficText(report.daily.sumOf { it.totalBytes })
        },
        batteryTrend = trend.takeLast(40),
        batteryLevel = level
    )
}

@Composable
fun HomeScreen(onOpen: (AppModule) -> Unit, vm: HomeViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val displayName = remember { ctx.getSharedPreferences("account_profile", Context.MODE_PRIVATE).getString("display_name", "").orEmpty() }
    var screenText by remember { mutableStateOf("Tap to set up") }
    var insights by remember { mutableStateOf(DeviceInsights()) }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick) {
        val loaded = withContext(Dispatchers.IO) { loadDeviceInsights(ctx) }
        insights = loaded
        screenText = if (!hasUsageAccess(ctx)) {
            "Tap to set up"
        } else {
            loaded.screenWeek.lastOrNull()
                ?.let { "${durationText(it.toLong())} today" }
                ?: "Tap to set up"
        }
    }

    val tiles = remember(s, insights, screenText, ctx) { listOf(
        TileSpec(
            "Habits",
            if (s.habitsTotal == 0) "Start your first habit" else "${s.habitsDone} of ${s.habitsTotal} done today",
            AppModule.Habits.icon, AppModule.Habits.gradient, AppModule.Habits,
            chart = when {
                s.habitWeek.any { it > 0f } -> TileChart.Trend(s.habitWeek)
                s.habitsTotal > 0 -> TileChart.Gauge(s.habitsDone.toFloat() / s.habitsTotal)
                else -> null
            }
        ),
        TileSpec(
            "To-dos",
            when {
                s.overdueTasks > 0 -> "${s.overdueTasks} overdue · ${s.tasksDueToday} today"
                s.tasksDueToday > 0 -> "${s.tasksDueToday} task${if (s.tasksDueToday == 1) "" else "s"} due today"
                else -> "Appointments and tasks by date"
            },
            AppModule.ToDos.icon, AppModule.ToDos.gradient, AppModule.ToDos,
            chart = TileChart.Trend(s.todoWeek)
        ),
        TileSpec(
            "Money",
            if (s.monthSpent <= 0L) "No spending yet" else "${money(s.monthSpent)} this month",
            AppModule.Money.icon, AppModule.Money.gradient, AppModule.Money,
            chart = if (s.spendWeek.any { it > 0f }) TileChart.Trend(s.spendWeek) else null
        ),
        TileSpec(
            "Loans",
            when {
                s.openLoans == 0 -> "All clear"
                s.loanNet >= 0 -> "${money(s.loanNet)} to receive"
                else -> "${money(-s.loanNet)} to pay"
            },
            AppModule.Loans.icon, AppModule.Loans.gradient, AppModule.Loans,
            chart = if (s.loanLent + s.loanBorrowed > 0) {
                TileChart.Composition(
                    listOf(
                        ChartSlice("Lent", s.loanLent.toFloat(), AppModule.Loans.accent),
                        ChartSlice("Borrowed", s.loanBorrowed.toFloat(), ChartPalette[2])
                    )
                )
            } else {
                null
            }
        ),
        TileSpec(
            "Contacts",
            "People, phone numbers and notes",
            AppModule.Contacts.icon, AppModule.Contacts.gradient, AppModule.Contacts
        ),
        TileSpec(
            "Files",
            if (s.fileCount == 0) "Your file library" else "${s.fileCount} file${if (s.fileCount == 1) "" else "s"} saved",
            AppModule.Files.icon, AppModule.Files.gradient, AppModule.Files,
            chart = if (s.fileMix.isEmpty()) {
                null
            } else {
                TileChart.Composition(
                    s.fileMix.take(3).mapIndexed { index, (label, value) ->
                        ChartSlice(label, value, chartColor(index))
                    }
                )
            }
        ),
        TileSpec(
            "Life rules",
            when {
                s.lifeRules == 0 -> "Rules, habits and quotes"
                s.pinnedRules > 0 -> "${s.lifeRules} saved · ${s.pinnedRules} pinned"
                else -> "${s.lifeRules} saved"
            },
            AppModule.LifeRules.icon, AppModule.LifeRules.gradient, AppModule.LifeRules
        ),
        TileSpec(
            "Screen time", screenText,
            AppModule.Screen.icon, AppModule.Screen.gradient, AppModule.Screen,
            chart = if (insights.screenWeek.any { it > 0f }) TileChart.Trend(insights.screenWeek) else null
        ),
        TileSpec(
            "Vault",
            if (s.vaultEntries == 0) "Private password manager"
            else "${s.vaultEntries} entr${if (s.vaultEntries == 1) "y" else "ies"} encrypted",
            AppModule.Vault.icon, AppModule.Vault.gradient, AppModule.Vault,
            chart = TileChart.Trend(s.vaultWeek)
        ),
        TileSpec(
            "Supabase",
            if (s.syncTracked == 0) "Cloud records and storage" else "${s.syncHealthy} of ${s.syncTracked} tables healthy",
            AppModule.Supabase.icon, AppModule.Supabase.gradient, AppModule.Supabase,
            chart = if (s.syncTracked == 0) {
                null
            } else {
                TileChart.Composition(
                    listOfNotNull(
                        ChartSlice("Synced", s.syncHealthy.toFloat(), AppModule.Supabase.accent)
                            .takeIf { s.syncHealthy > 0 },
                        ChartSlice("Needs sync", (s.syncTracked - s.syncHealthy).toFloat(), Warning)
                            .takeIf { s.syncTracked > s.syncHealthy }
                    )
                )
            }
        )
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true } }
    val batteryLevel = insights.batteryLevel
    val deviceTools = remember(insights, ctx) { listOf(
        TileSpec(
            "Battery",
            insights.batteryLevel?.let { "$it% right now" } ?: "Live charging animation",
            AppModule.Battery.icon, AppModule.Battery.gradient, AppModule.Battery,
            chart = when {
                insights.batteryTrend.size >= 2 -> TileChart.Trend(insights.batteryTrend)
                batteryLevel != null -> TileChart.Gauge(batteryLevel / 100f)
                else -> null
            }
        ),
        TileSpec(
            "Network",
            insights.networkLabel?.let { "$it this week" } ?: "Wi-Fi and mobile data",
            AppModule.Network.icon, AppModule.Network.gradient, AppModule.Network,
            chart = if (insights.networkWeek.any { it > 0f }) TileChart.Trend(insights.networkWeek) else null
        )
    ).filter { spec -> spec.module?.let { isModuleVisible(ctx, it) } ?: true } }
    val settingsTile = TileSpec("Settings", "Manage modules and preferences", AppModule.Settings.icon, AppModule.Settings.gradient, AppModule.Settings)

    ScreenBackground(tint = BrandGradient.first()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = MomentumTokens.screenPadding,
                vertical = MomentumTokens.space3
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HomeHeader(s, displayName, onSettings = { onOpen(AppModule.Settings) }) }
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
private fun HomeHeader(s: HomeState, displayName: String, onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = MomentumTokens.space2,
                bottom = MomentumTokens.space2
            )
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.zylo_logo),
                contentDescription = "Zylo logo",
                modifier = Modifier.size(34.dp).padding(end = 4.dp)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, "Open settings", Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.height(MomentumTokens.space2))
        Text(
            if (displayName.isBlank()) "Your day," else "Your day, $displayName",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "in one place",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(MomentumTokens.space2))
        Text(
            "Stay organized. Live smarter",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModuleTile(spec: TileSpec, modifier: Modifier, onClick: () -> Unit) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    val accent = spec.tint
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
            .background(neonWashBrush(glass, accent))
            .clickable(
                enabled = spec.module != null,
                role = Role.Button,
                onClickLabel = "Open ${spec.title}",
                onClick = onClick
            )
            .border(1.dp, neonEdgeBrush(glass, accent), shape)
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
                            spec.gradient.last().copy(alpha = 0.34f),
                            spec.gradient.first().copy(alpha = 0.12f),
                            spec.gradient.first().copy(alpha = 0f)
                        )
                    ),
                    CircleShape
                )
        )
        Column(
            Modifier.fillMaxSize().padding(14.dp),
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
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (glass.dark) 0.08f else 0.34f))
                        .border(1.dp, Color.White.copy(alpha = if (glass.dark) 0.18f else 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null,
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (spec.chart != null) {
                    Spacer(Modifier.height(3.dp))
                    TileChartView(
                        spec.chart,
                        accent,
                        Modifier.fillMaxWidth().height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TileChartView(
    chart: TileChart?,
    tint: Color,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    when (chart) {
        null -> Unit
        is TileChart.Trend -> Box(modifier, contentAlignment = Alignment.BottomStart) {
            Sparkline(chart.values, Modifier.fillMaxSize(), tint)
        }
        is TileChart.Gauge -> Box(modifier, contentAlignment = Alignment.CenterStart) {
            ProgressBar(
                fraction = chart.fraction.coerceIn(0f, 1f),
                color = tint,
                track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
                height = if (dense) 5.dp else 6.dp
            )
        }
        is TileChart.Composition -> {
            val slices = chart.slices.filter { it.value > 0f }
            if (slices.isEmpty()) return
            Column(
                modifier,
                verticalArrangement = Arrangement.spacedBy(if (dense) 0.dp else 4.dp)
            ) {
                StackedBar(slices, Modifier.fillMaxWidth(), height = if (dense) 8.dp else 7.dp)
                if (!dense) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slices.take(3).forEach { slice ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    slice.label,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
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
            .background(neonWashBrush(glass, spec.tint))
            .clickable(
                enabled = spec.module != null,
                role = Role.Button,
                onClickLabel = "Open ${spec.title}",
                onClick = onClick
            )
            .border(
                1.dp,
                neonEdgeBrush(glass, spec.tint),
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.26f), Color.Transparent)
                    )
                )
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
        if (spec.chart != null) {
            Spacer(Modifier.width(10.dp))
            TileChartView(
                spec.chart,
                spec.tint,
                Modifier.width(66.dp).height(20.dp),
                dense = true
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}
