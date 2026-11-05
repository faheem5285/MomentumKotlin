@file:Suppress("FunctionNaming", "LongParameterList", "CyclomaticComplexMethod")

package com.faheem.habits.features.screen_time
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppUsage(val pkg: String, val label: String, val minutes: Long, val icon: ImageBitmap?)
private enum class TimeTab(val title: String) { Overview("Overview"), Apps("Apps"), Reels("Reels") }
private enum class UsageRange(val title: String, val days: Long) { Today("Today", 1), Week("7 days", 7) }

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun loadUsage(ctx: Context, days: Long = 1): List<AppUsage> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val startDay = LocalDate.now().minusDays(days - 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val stats = usm.queryAndAggregateUsageStats(startDay, System.currentTimeMillis())
    return stats.values
        .filter { it.packageName != ctx.packageName && it.totalTimeInForeground >= 60_000 }
        .sortedByDescending { it.totalTimeInForeground }
        .take(40)
        .map {
            val identity = installedAppIdentity(ctx, it.packageName)
            AppUsage(it.packageName, identity.label, it.totalTimeInForeground / 60_000, identity.icon)
        }
}

fun durationText(min: Long) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

/** Per-day totals for the last [days] days, in minutes. */
private fun loadDailyMinutes(ctx: Context, days: Int): List<Long> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val today = LocalDate.now()
    return (days - 1 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            .coerceAtMost(System.currentTimeMillis())
        val stats = runCatching { usm.queryAndAggregateUsageStats(start, end) }.getOrNull()
        stats?.values
            ?.filter { it.packageName != ctx.packageName }
            ?.sumOf { it.totalTimeInForeground }
            ?.div(60_000L)
            ?: 0L
    }
}

@Composable
fun ScreenTimeScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TimeTab.Overview) }
    var range by remember { mutableStateOf(UsageRange.Today) }
    var granted by remember { mutableStateOf(hasUsageAccess(context)) }
    var accessibilityOn by remember { mutableStateOf(InstagramScrollCounterService.isEnabled(context)) }
    var usage by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var weekMinutes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var reelCount by remember { mutableIntStateOf(0) }
    var reelLimit by remember { mutableIntStateOf(0) }
    var screenLimit by remember { mutableIntStateOf(0) }
    var screenLimitPeriod by remember { mutableStateOf(ScreenTimePeriod.DAILY) }
    var screenLimitUsed by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var showReelLimit by remember { mutableStateOf(false) }
    var showScreenLimit by remember { mutableStateOf(false) }
    var showScreenAlarm by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick, range) {
        granted = hasUsageAccess(context)
        accessibilityOn = InstagramScrollCounterService.isEnabled(context)
        val prefs = context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE)
        reelCount = prefs.getInt(InstagramScrollCounterService.countKey(LocalDate.now()), 0)
        reelLimit = prefs.getInt(InstagramScrollCounterService.PREF_LIMIT, 0)
        screenLimit = prefs.getInt(PREF_SCREEN_LIMIT, 0)
        screenLimitPeriod = ScreenTimePeriod.fromKey(prefs.getString(PREF_SCREEN_LIMIT_PERIOD, null))
        usage = if (granted) withContext(Dispatchers.IO) { loadUsage(context, range.days) } else emptyList()
        weekMinutes = if (granted) withContext(Dispatchers.IO) { loadDailyMinutes(context, 7) } else emptyList()
        screenLimitUsed = if (granted) withContext(Dispatchers.IO) {
            screenTimeMinutes(context, screenLimitPeriod)?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
        } else 0
        withContext(Dispatchers.IO) { checkScreenTimeAlarm(context) }
    }

    if (showScreenAlarm) DurationLimitSheet(
        title = "Screen time alarm",
        currentMinutes = context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE).getInt(PREF_SCREEN_ALARM, 0),
        currentPeriod = ScreenTimePeriod.fromKey(context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE).getString(PREF_SCREEN_ALARM_PERIOD, null)),
        helper = "Alert when screen time reaches this amount",
        onSave = { minutes, period ->
            context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PREF_SCREEN_ALARM, minutes).putString(PREF_SCREEN_ALARM_PERIOD, period.key)
                .remove("screen_time_alarm_last_period").apply()
            showScreenAlarm = false
            tick++
        },
        onDismiss = { showScreenAlarm = false }
    )
    if (showReelLimit) NumberLimitSheet(
        title = "Daily Reels scroll limit", current = reelLimit, helper = "Maximum daily scroll events",
        onSave = {
            context.getSharedPreferences(InstagramScrollCounterService.PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(InstagramScrollCounterService.PREF_LIMIT, it).apply()
            reelLimit = it; showReelLimit = false
        },
        onDismiss = { showReelLimit = false }
    )
    if (showScreenLimit) DurationLimitSheet(
        title = "Screen time limit",
        currentMinutes = screenLimit,
        currentPeriod = screenLimitPeriod,
        helper = "Usage time allowed for this period",
        onSave = { minutes, period ->
            context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(PREF_SCREEN_LIMIT, minutes).putString(PREF_SCREEN_LIMIT_PERIOD, period.key).apply()
            screenLimit = minutes
            screenLimitPeriod = period
            showScreenLimit = false
            tick++
        },
        onDismiss = { showScreenLimit = false }
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                TimeTab.Overview -> ScreenOverview(usage, weekMinutes, granted, screenLimit, screenLimitPeriod, screenLimitUsed, reelCount, accessibilityOn, onUsageSettings = { openUsageSettings(context) }, onSetLimit = { showScreenLimit = true }, onSetAlarm = { showScreenAlarm = true }, onOpenReels = { tab = TimeTab.Reels }, onRefresh = { tick++ })
                TimeTab.Apps -> UsageAppsScreen(usage, granted, range, onRange = { range = it }, onUsageSettings = { openUsageSettings(context) }, onRefresh = { tick++ })
                TimeTab.Reels -> ReelsCounterScreen(reelCount, reelLimit, accessibilityOn, onSettings = { openAccessibilitySettings(context) }, onSetLimit = { showReelLimit = true }, onRefresh = { tick++ })
            }
        }
        MomentumTabs(
            tabs = TimeTab.entries.map {
                MomentumTab(
                    it.title,
                    when (it) {
                        TimeTab.Overview -> Icons.Rounded.Dashboard
                        TimeTab.Apps -> Icons.Rounded.Apps
                        TimeTab.Reels -> Icons.Rounded.Swipe
                    }
                )
            },
            selected = TimeTab.entries.indexOf(tab),
            onSelect = { tab = TimeTab.entries[it] }
        )
    }
}

private fun openUsageSettings(context: Context) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
private fun openAccessibilitySettings(context: Context) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

@Composable
private fun ScreenOverview(
    usage: List<AppUsage>, weekMinutes: List<Long>, granted: Boolean, screenLimit: Int,
    screenLimitPeriod: ScreenTimePeriod, screenLimitUsed: Int, reelCount: Int, accessibilityOn: Boolean,
    onUsageSettings: () -> Unit, onSetLimit: () -> Unit, onSetAlarm: () -> Unit, onOpenReels: () -> Unit, onRefresh: () -> Unit
) {
    val total = usage.sumOf { it.minutes }
    val maxMin = (usage.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1)
    val fraction = if (screenLimit > 0) screenLimitUsed.toFloat() / screenLimit else 0f
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your digital balance", Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                    HeroRefreshButton(onRefresh, "Refresh screen-time data")
                }
                Text(if (granted) durationText(total) else "Usage access needed", fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                Text(if (granted) "Screen time today across ${usage.size} apps" else "Turn on Usage access to see app time", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                if (granted && screenLimit > 0) {
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(fraction, Color.White, Color.White.copy(alpha = .25f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (screenLimitUsed >= screenLimit) "${screenLimitPeriod.label} time limit reached"
                        else "${durationText((screenLimit - screenLimitUsed).coerceAtLeast(0).toLong())} left of ${durationText(screenLimit.toLong())} ${screenLimitPeriod.label.lowercase()}",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = .9f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = if (granted) onSetLimit else onUsageSettings) {
                    Text(if (granted) "${if (screenLimit > 0) "Adjust" else "Set"} ${screenLimitPeriod.label.lowercase()} limit" else "Open Usage access settings", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onSetAlarm) {
                    Text("Set alarm", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!granted) item {
            PermissionInfoCard("Usage access", "Shows time spent in each app. Android keeps this permission under your control.", onUsageSettings)
        }
        if (granted && weekMinutes.any { it > 0L }) {
            item { SectionHeader("Last 7 days", subtitle = "Daily screen time") }
            item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.BarChart, MaterialTheme.colorScheme.primary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Weekly rhythm", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Average ${durationText(weekMinutes.average().toLong())} per day",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    BarChart(
                        values = weekMinutes.map { it.toFloat() },
                        labels = weekMinutes.indices.map { index ->
                            DateTimeFormatter.ofPattern("EEE", Locale.US)
                                .format(LocalDate.now().minusDays((weekMinutes.size - 1 - index).toLong()))
                        },
                        colors = listOf(MaterialTheme.colorScheme.primary),
                        highlightIndex = weekMinutes.lastIndex,
                        height = 150.dp,
                        valueFormatter = { value -> durationText(value.toLong()) }
                    )
                }
            }
        }
        item {
            PremiumCard(onClick = onOpenReels, contentPadding = PaddingValues(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Swipe, MaterialTheme.colorScheme.primary, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Instagram scroll estimate", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (accessibilityOn) "$reelCount events today" else "Set up your optional counter",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item { SectionHeader("Top apps today", subtitle = if (granted && usage.isNotEmpty()) "${usage.size} apps tracked" else null) }
        if (granted && usage.isEmpty()) item {
            EmptyState(Icons.Rounded.Smartphone, "No usage yet", "Apps appear after at least one minute of use")
        }
        if (granted && usage.isNotEmpty()) {
            item {
                val slices = usage.take(5).mapIndexed { index, app ->
                    ChartSlice(app.label, app.minutes.toFloat(), chartColor(index))
                }
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            slices = slices,
                            modifier = Modifier.width(150.dp),
                            centerValue = durationText(total),
                            centerLabel = "total",
                            thickness = 18.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            ChartLegend(
                                slices = slices,
                                maxRows = 5,
                                valueFormatter = { durationText(it.toLong()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${usage.first().label} leads with ${durationText(usage.first().minutes)}.",
                        fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (granted) items(usage.take(5), key = { "overview-${it.pkg}" }) { UsageRow(it, maxMin) }
    }
}

@Composable
private fun UsageAppsScreen(usage: List<AppUsage>, granted: Boolean, range: UsageRange, onRange: (UsageRange) -> Unit, onUsageSettings: () -> Unit, onRefresh: () -> Unit) {
    val total = usage.sumOf { it.minutes }
    val maxMin = (usage.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1)
    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PermissionInfoCard("Unlock app usage", "Allow Usage access in Android settings. Your device decides when you can revoke it.", onUsageSettings)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("App usage", Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                    HeroRefreshButton(onRefresh, "Refresh app usage")
                }
                Text(durationText(total), fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                Text(
                    if (range == UsageRange.Today) "Total so far today" else "${durationText(total / 7)} average per day over 7 days",
                    fontSize = 13.sp, color = Color.White.copy(alpha = .9f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageRange.entries.forEach { rangeOption ->
                    MomentumChip(selected = range == rangeOption, onClick = { onRange(rangeOption) }, label = rangeOption.title)
                }
            }
        }
        item { SectionHeader("Most used", subtitle = "${usage.size} apps") }
        if (usage.isEmpty()) item { EmptyState(Icons.Rounded.Smartphone, "No usage to show", "Usage may take a short while to appear") }
        items(usage, key = { it.pkg }) { UsageRow(it, maxMin) }
    }
}

@Composable
private fun UsageRow(app: AppUsage, maxMin: Long) {
    PremiumCard(contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.icon, app.label, 44.dp, cornerRadius = 13.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(durationText(app.minutes), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                ProgressBar(app.minutes.toFloat() / maxMin, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant, height = 6.dp)
            }
        }
    }
}

@Composable
private fun ReelsCounterScreen(count: Int, limit: Int, enabled: Boolean, onSettings: () -> Unit, onSetLimit: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(MomentumTokens.screenPadding, 6.dp, MomentumTokens.screenPadding, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Instagram scroll estimate", Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                    HeroRefreshButton(onRefresh, "Refresh Reels count")
                }
                Text("$count", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                Text("scroll events counted today", fontSize = 14.sp, color = Color.White.copy(alpha = .9f))
                if (limit > 0) {
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(count.toFloat() / limit, Color.White, Color.White.copy(alpha = .25f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (count >= limit) "Daily target reached" else "${limit - count} events left of your $limit target",
                        fontSize = 12.5.sp, color = Color.White.copy(alpha = .9f)
                    )
                }
            }
        }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(
                        if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        if (enabled) Positive else Warning,
                        40.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (enabled) "Counter is on" else "Counter is off", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("You can change access any time in Android Settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (!enabled) {
                    PrimaryButton("Set up counter", onClick = onSettings, icon = Icons.Rounded.AccessibilityNew)
                } else {
                    SecondaryButton("Manage Accessibility access", onClick = onSettings, icon = Icons.Rounded.Settings)
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    if (limit > 0) "Change daily target" else "Set daily target",
                    onClick = onSetLimit,
                    icon = Icons.Rounded.Flag
                )
            }
        }
        item {
            InfoBanner(
                icon = Icons.Rounded.Info,
                text = "Android reports accessibility scroll events from Instagram. Zylo counts debounced events and resets each day. The count is an estimate, not a verified Reels count.",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Text("Privacy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "The service is filtered to Instagram and does not retrieve screen text or content. You enable or disable it in Android Accessibility settings.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionInfoCard(title: String, description: String, onClick: () -> Unit) {
    PremiumCard(contentPadding = PaddingValues(MomentumTokens.cardPadding)) {
        IconBubble(Icons.Rounded.Security, MaterialTheme.colorScheme.primary, 44.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Open Android settings", onClick = onClick, icon = Icons.Rounded.OpenInNew)
    }
}

@Composable
private fun NumberLimitSheet(title: String, current: Int, helper: String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var input by remember(title, current) { mutableStateOf(if (current > 0) current.toString() else "") }
    FormSheet(title, onDismiss) {
        MomentumTextField(
            value = input,
            onValueChange = { input = it.filter { character -> character.isDigit() }.take(5) },
            label = { Text(helper) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text("Leave the field empty or enter 0 to remove the limit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("Save limit", onClick = { onSave(input.toIntOrNull() ?: 0) })
    }
}

@Composable
private fun DurationLimitSheet(
    title: String,
    currentMinutes: Int,
    currentPeriod: ScreenTimePeriod,
    helper: String,
    onSave: (Int, ScreenTimePeriod) -> Unit,
    onDismiss: () -> Unit
) {
    var hours by remember(title, currentMinutes) {
        mutableStateOf(if (currentMinutes > 0) (currentMinutes / 60).toString() else "")
    }
    var minutes by remember(title, currentMinutes) {
        mutableStateOf(if (currentMinutes > 0) (currentMinutes % 60).toString() else "")
    }
    var period by remember(title, currentPeriod) { mutableStateOf(currentPeriod) }
    FormSheet(title, onDismiss) {
        Text(helper, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MomentumTextField(
                value = hours,
                onValueChange = { hours = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.weight(1f),
                label = { Text("Hours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            MomentumTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f),
                label = { Text("Minutes (0–59)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Text("Limit period", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ScreenTimePeriod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = period == option,
                    onClick = { period = option },
                    shape = SegmentedButtonDefaults.itemShape(index, ScreenTimePeriod.entries.size)
                ) { Text(option.label, fontSize = 11.sp, maxLines = 1) }
            }
        }
        Text("Enter 0 hours and 0 minutes to remove the limit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(
            "Save limit",
            onClick = {
                val hourValue = hours.toIntOrNull() ?: 0
                val minuteValue = (minutes.toIntOrNull() ?: 0).coerceIn(0, 59)
                onSave((hourValue * 60 + minuteValue).coerceAtMost(Int.MAX_VALUE), period)
            }
        )
    }
}

/** Total minutes of today's screen time, or null if Usage access is not granted. */
fun todayScreenMinutes(ctx: Context): Long? {
    if (!hasUsageAccess(ctx)) return null
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
        .filter { it.packageName != ctx.packageName && it.totalTimeInForeground >= 60_000 }
        .sumOf { it.totalTimeInForeground / 60_000 }
}

/** Minutes of screen time for each of the last [days] days, oldest first. */
fun screenWeekMinutes(ctx: Context, days: Int = 7): List<Float> {
    if (!hasUsageAccess(ctx)) return emptyList()
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    return (days - 1 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            usm.queryAndAggregateUsageStats(start, end.coerceAtMost(now)).values
                .filter { it.packageName != ctx.packageName && it.totalTimeInForeground >= 60_000 }
                .sumOf { it.totalTimeInForeground / 60_000 }
                .toFloat()
        }.getOrDefault(0f)
    }
}
