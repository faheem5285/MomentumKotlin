@file:Suppress("FunctionNaming", "LongParameterList", "TooManyFunctions")

package com.faheem.habits.features.home
import com.faheem.habits.core.data.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.features.settings.SettingsViewModel
import com.faheem.habits.features.battery.BatteryLevelHistory
import com.faheem.habits.features.network.loadAppNetworkReport
import com.faheem.habits.features.network.trafficText
import com.faheem.habits.features.screen_time.durationText
import com.faheem.habits.features.screen_time.hasUsageAccess
import com.faheem.habits.features.screen_time.screenWeekMinutes


import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.LocalDate
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
    val vaultCountKnown: Boolean = false,
    val fileMix: List<Pair<String, Float>> = emptyList(),
    val loanLent: Long = 0L,
    val loanBorrowed: Long = 0L,
    val syncPending: Int = 0,
    val syncHealthy: Int = 0,
    val syncTracked: Int = 0,
    val lifeRules: Int = 0,
    val pinnedRules: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: HomeRepository
) : ViewModel() {
    val state: StateFlow<HomeState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
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
fun HomeScreen(
    onOpen: (AppModule) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val moduleVisibility by settingsViewModel.moduleVisibility.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
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

    val tiles = remember(s, insights, screenText) { listOf(
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
            if (!s.vaultCountKnown) "Open Vault to load account records"
            else if (s.vaultEntries == 0) "Private password manager"
            else "${s.vaultEntries} secure item${if (s.vaultEntries == 1) "" else "s"}",
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
    ) }
    val visibleTiles = remember(tiles, moduleVisibility) {
        tiles.filter { spec -> spec.module?.let { moduleVisibility[it.name] ?: true } ?: true }
    }
    val batteryLevel = insights.batteryLevel
    val deviceTools = remember(insights) { listOf(
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
    ) }
    val visibleDeviceTools = remember(deviceTools, moduleVisibility) {
        deviceTools.filter { spec -> spec.module?.let { moduleVisibility[it.name] ?: true } ?: true }
    }
    val settingsTile = TileSpec("Settings", "Manage modules and preferences", AppModule.Settings.icon, AppModule.Settings.gradient, AppModule.Settings)

    val rows = visibleTiles.chunked(2)
    val reduce = LocalReduceEffects.current
    var entered by remember { mutableStateOf(reduce) }
    val enter = animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "home-enter"
    )
    LaunchedEffect(Unit) { entered = true }

    ScreenBackground(tint = BrandGradient.first()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = MomentumTokens.screenPadding,
                vertical = MomentumTokens.space3
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                HomeHeader(
                    onSettings = { onOpen(AppModule.Settings) },
                    modifier = Modifier.homeEntrance(enter, 0)
                )
            }
            itemsIndexed(
                items = rows,
                key = { _, row -> row.joinToString("|") { it.title } },
                contentType = { _, _ -> "module-row" }
            ) { index, row ->
                Row(
                    Modifier.fillMaxWidth().homeEntrance(enter, index + 1),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { spec -> ModuleTile(spec, Modifier.weight(1f)) { spec.module?.let(onOpen) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item { SectionHeader("On this device", Modifier.homeEntrance(enter, rows.size + 1)) }
            item {
                Column(
                    Modifier.homeEntrance(enter, rows.size + 2),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleDeviceTools.forEach { spec ->
                        DeviceRow(spec) { spec.module?.let(onOpen) }
                    }
                }
            }
            item {
                DeviceRow(settingsTile, Modifier.homeEntrance(enter, rows.size + 3)) { onOpen(AppModule.Settings) }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassPalette.current
    val accent = Color(0xFF1769F5)
    Box(
        modifier.fillMaxWidth().padding(top = MomentumTokens.space2)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape)
                    .background(Color.White.copy(alpha = if (glass.dark) 0.10f else 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.62f), CircleShape)
                    .padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp), tint = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d")), fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (glass.dark) 0.12f else 0.68f))
                    .border(1.dp, Color.White.copy(alpha = 0.64f), CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Open settings", onClick = onSettings),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Settings, "Open settings", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1.45f)) {
                    Text("Your day,",
                        fontSize = 29.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = (-1.1).sp,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("in one place", fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.2).sp, color = accent, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(5.dp))
                    Text("Stay organized. Live smarter.", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.width(70.dp).height(3.dp).rotate(-8f).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF6624E8), accent, Color.Transparent))))
                }
                Spacer(Modifier.width(2.dp))
                CalendarHeroIllustration(Modifier.weight(0.8f).height(122.dp))
            }
        }
    }
}

@Composable
private fun CalendarHeroIllustration(modifier: Modifier = Modifier) {
    val deepBlue = Color(0xFF064DCB)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.98f).height(32.dp).rotate(-18f)
            .border(5.dp, Color(0xFF63C8FF).copy(alpha = 0.82f), CircleShape))
        Box(Modifier.size(width = 98.dp, height = 114.dp).rotate(10f)
            .shadow(12.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE6F0FF))))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(20.dp))) {
            Box(Modifier.fillMaxWidth().height(38.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF44A8FF), deepBlue))))
            Row(Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                repeat(2) {
                    Box(Modifier.width(11.dp).height(20.dp).clip(RoundedCornerShape(7.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFB9E9FF), Color(0xFF1670EF))))
                        .border(2.dp, Color(0xFFDBF6FF), RoundedCornerShape(7.dp)))
                }
            }
            Box(Modifier.align(Alignment.Center).offset(y = 8.dp).size(62.dp).shadow(7.dp, CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF39B8FF), deepBlue)), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "A completed day", Modifier.size(38.dp), tint = Color.White)
            }
        }
        Box(Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 17.dp).size(9.dp)
            .background(Color.White.copy(alpha = 0.86f), CircleShape))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-3).dp, y = (-14).dp).size(13.dp)
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFF8BCBFF))), CircleShape))
    }
}
/**
 * One-shot entrance: a staggered fade-and-rise driven by a single shared
 * progress value so recycling list items never replay the animation.
 */
private fun Modifier.homeEntrance(progress: State<Float>, index: Int): Modifier =
    graphicsLayer {
        val p = progress.value
        val phase = (index * 0.07f).coerceAtMost(0.55f)
        val t = ((p - phase) / (1f - phase)).coerceIn(0f, 1f)
        alpha = t
        translationY = (1f - t) * 26.dp.toPx()
    }

/**
 * High-contrast icon badge used by module tiles and device rows on Home.
 */
@Composable
private fun ModuleIconBadge(
    icon: ImageVector,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val accent = gradient.getOrElse(1) { gradient.first() }
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        modifier
            .size(size)
            .shadow(
                if (LocalReduceEffects.current) 0.dp else 8.dp,
                shape,
                clip = false,
                ambientColor = accent.copy(alpha = 0.30f),
                spotColor = gradient.first().copy(alpha = 0.24f)
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(gradient.first(), accent, accent.copy(alpha = 0.88f))))
            .border(1.dp, Color.White.copy(alpha = 0.58f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.55f), tint = Color.White)
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
                .offset(x = 18.dp, y = (-24).dp)
                .size(94.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            spec.gradient.last().copy(alpha = 0.24f),
                            spec.gradient.first().copy(alpha = 0.08f),
                            spec.gradient.first().copy(alpha = 0f)
                        )
                    ),
                    CircleShape
                )
        )
        Icon(
            imageVector = moduleWatermarkIcon(spec.module),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = 9.dp).size(70.dp),
            tint = spec.tint.copy(alpha = if (glass.dark) 0.35f else 0.27f)
        )
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
        ) {
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

private fun moduleWatermarkIcon(module: AppModule?) = when (module) {
    AppModule.Habits -> Icons.Rounded.TaskAlt
    AppModule.ToDos -> Icons.Rounded.EventNote
    AppModule.Money -> Icons.Rounded.Payments
    AppModule.Loans -> Icons.Rounded.CurrencyExchange
    AppModule.Contacts -> Icons.Rounded.Contacts
    AppModule.Files -> Icons.Rounded.FolderOpen
    AppModule.LifeRules -> Icons.Rounded.AutoStories
    AppModule.Screen -> Icons.Rounded.HourglassTop
    AppModule.Vault -> Icons.Rounded.Security
    AppModule.Battery -> Icons.Rounded.BatteryChargingFull
    AppModule.Network -> Icons.Rounded.Wifi
    AppModule.Supabase -> Icons.Rounded.CloudSync
    AppModule.Settings -> Icons.Rounded.Tune
    null -> Icons.Rounded.Apps
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
            Box(modifier, contentAlignment = Alignment.Center) {
                StackedBar(
                    slices,
                    Modifier.fillMaxWidth(),
                    height = if (dense) 8.dp else 7.dp
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
        ModuleIconBadge(spec.icon, spec.gradient)
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
