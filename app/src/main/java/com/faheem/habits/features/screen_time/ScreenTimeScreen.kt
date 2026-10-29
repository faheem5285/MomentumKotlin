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
import androidx.compose.foundation.Image
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
    val pm = ctx.packageManager
    return stats.values
        .filter { it.packageName != ctx.packageName && it.totalTimeInForeground >= 60_000 }
        .sortedByDescending { it.totalTimeInForeground }
        .take(40)
        .map {
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString() } catch (_: Exception) { it.packageName }
            val icon = try { pm.getApplicationIcon(it.packageName).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null }
            AppUsage(it.packageName, label, it.totalTimeInForeground / 60_000, icon)
        }
}

fun durationText(min: Long) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

@Composable
fun ScreenTimeScreen() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TimeTab.Overview) }
    var range by remember { mutableStateOf(UsageRange.Today) }
    var granted by remember { mutableStateOf(hasUsageAccess(context)) }
    var accessibilityOn by remember { mutableStateOf(InstagramScrollCounterService.isEnabled(context)) }
    var usage by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var reelCount by remember { mutableIntStateOf(0) }
    var reelLimit by remember { mutableIntStateOf(0) }
    var screenLimit by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var showReelLimit by remember { mutableStateOf(false) }
    var showScreenLimit by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    LaunchedEffect(tick, range) {
        granted = hasUsageAccess(context)
        accessibilityOn = InstagramScrollCounterService.isEnabled(context)
        val prefs = context.getSharedPreferences(InstagramScrollCounterService.PREFS_NAME, Context.MODE_PRIVATE)
        reelCount = prefs.getInt(InstagramScrollCounterService.countKey(LocalDate.now()), 0)
        reelLimit = prefs.getInt(InstagramScrollCounterService.PREF_LIMIT, 0)
        screenLimit = prefs.getInt(PREF_SCREEN_LIMIT, 0)
        usage = if (granted) withContext(Dispatchers.IO) { loadUsage(context, range.days) } else emptyList()
    }

    if (showReelLimit) NumberLimitSheet(
        title = "Daily Reels scroll limit", current = reelLimit, helper = "Maximum daily scroll events",
        onSave = {
            context.getSharedPreferences(InstagramScrollCounterService.PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(InstagramScrollCounterService.PREF_LIMIT, it).apply()
            reelLimit = it; showReelLimit = false
        },
        onDismiss = { showReelLimit = false }
    )
    if (showScreenLimit) NumberLimitSheet(
        title = "Daily screen time goal", current = screenLimit, helper = "Minutes per day",
        onSave = {
            context.getSharedPreferences(InstagramScrollCounterService.PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_SCREEN_LIMIT, it).apply()
            screenLimit = it; showScreenLimit = false
        },
        onDismiss = { showScreenLimit = false }
    )

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                TimeTab.Overview -> ScreenOverview(usage, granted, screenLimit, reelCount, accessibilityOn, onUsageSettings = { openUsageSettings(context) }, onSetLimit = { showScreenLimit = true }, onOpenReels = { tab = TimeTab.Reels }, onRefresh = { tick++ })
                TimeTab.Apps -> UsageAppsScreen(usage, granted, range, onRange = { range = it }, onUsageSettings = { openUsageSettings(context) }, onRefresh = { tick++ })
                TimeTab.Reels -> ReelsCounterScreen(reelCount, reelLimit, accessibilityOn, onSettings = { openAccessibilitySettings(context) }, onSetLimit = { showReelLimit = true }, onRefresh = { tick++ })
            }
        }
        NavigationBar(windowInsets = WindowInsets(0.dp)) {
            TimeTab.entries.forEach { item ->
                val icon = when (item) { TimeTab.Overview -> Icons.Rounded.Dashboard; TimeTab.Apps -> Icons.Rounded.Apps; TimeTab.Reels -> Icons.Rounded.Swipe }
                NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(icon, item.title) }, label = { Text(item.title) })
            }
        }
    }
}

private const val PREF_SCREEN_LIMIT = "daily_screen_limit_minutes"

private fun openUsageSettings(context: Context) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
private fun openAccessibilitySettings(context: Context) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

@Composable
private fun ScreenOverview(
    usage: List<AppUsage>, granted: Boolean, screenLimit: Int, reelCount: Int, accessibilityOn: Boolean,
    onUsageSettings: () -> Unit, onSetLimit: () -> Unit, onOpenReels: () -> Unit, onRefresh: () -> Unit
) {
    val total = usage.sumOf { it.minutes }
    val maxMin = (usage.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your digital balance", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                    HeroRefreshButton(onRefresh, "Refresh screen-time data")
                }
                Text(if (granted) durationText(total) else "Usage access needed", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(if (granted) "Screen time today across ${usage.size} apps" else "Turn on Usage access to see app time", fontSize = 13.sp)
                if (granted && screenLimit > 0) {
                    Spacer(Modifier.height(10.dp))
                    ProgressBar(total.toFloat() / screenLimit, Color.White, Color.White.copy(alpha = .25f))
                    Spacer(Modifier.height(5.dp))
                    Text(if (total >= screenLimit) "Daily time limit reached" else "${durationText((screenLimit - total).coerceAtLeast(0))} left of ${durationText(screenLimit.toLong())}", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = if (granted) onSetLimit else onUsageSettings) { Text(if (granted) "${if (screenLimit > 0) "Adjust" else "Set"} daily limit" else "Open Usage access settings", color = Color.White) }
            }
        }
        if (!granted) item { PermissionInfoCard("Usage access", "Shows time spent in each app. Android keeps this permission under your control.", onUsageSettings) }
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenReels), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Swipe, MaterialTheme.colorScheme.primary, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("Instagram scroll estimate", fontWeight = FontWeight.SemiBold); Text(if (accessibilityOn) "$reelCount events today" else "Set up your optional counter", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
        item { SectionLabel("Top apps today") }
        if (granted && usage.isEmpty()) item { EmptyState(Icons.Rounded.Smartphone, "No usage yet", "Apps appear after at least one minute of use") }
        if (granted) items(usage.take(5), key = { "overview-${it.pkg}" }) { UsageRow(it, maxMin) }
    }
}

@Composable
private fun UsageAppsScreen(usage: List<AppUsage>, granted: Boolean, range: UsageRange, onRange: (UsageRange) -> Unit, onUsageSettings: () -> Unit, onRefresh: () -> Unit) {
    val total = usage.sumOf { it.minutes }
    val maxMin = (usage.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1)
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PermissionInfoCard("Unlock app usage", "Allow Usage access in Android settings. Your device decides when you can revoke it.", onUsageSettings)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("App usage", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                    HeroRefreshButton(onRefresh, "Refresh app usage")
                }
                Text(durationText(total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(if (range == UsageRange.Today) "Total so far today" else "${durationText(total / 7)} average per day over 7 days", fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UsageRange.entries.forEach { FilterChip(selected = range == it, onClick = { onRange(it) }, label = { Text(it.title) }) }
            }
        }
        item { SectionLabel("Most used · ${usage.size} apps") }
        if (usage.isEmpty()) item { EmptyState(Icons.Rounded.Smartphone, "No usage to show", "Usage may take a short while to appear") }
        items(usage, key = { it.pkg }) { UsageRow(it, maxMin) }
    }
}

@Composable
private fun UsageRow(app: AppUsage, maxMin: Long) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) Image(app.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
            else IconBubble(Icons.Rounded.PhoneAndroid, MaterialTheme.colorScheme.primary, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Instagram scroll estimate", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                    HeroRefreshButton(onRefresh, "Refresh Reels count")
                }
                Text("$count", fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Text("scroll events counted today", fontSize = 14.sp)
                if (limit > 0) {
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(count.toFloat() / limit, Color.White, Color.White.copy(alpha = .25f))
                    Spacer(Modifier.height(6.dp))
                    Text(if (count >= limit) "Daily target reached" else "${limit - count} events left of your $limit target", fontSize = 13.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Security, if (enabled) Positive else Warning, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(if (enabled) "Counter is on" else "Counter is off", fontWeight = FontWeight.SemiBold); Text("You can change access any time in Android Settings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (!enabled) BigButton("Set up counter") { onSettings() }
                    else OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Manage Accessibility access") }
                    OutlinedButton(onClick = onSetLimit, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Flag, null); Spacer(Modifier.width(8.dp)); Text(if (limit > 0) "Change daily target" else "Set daily target") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How the estimate works", fontWeight = FontWeight.SemiBold)
                    Text("Android reports accessibility scroll events from Instagram. Momentum counts debounced events and resets the count each day. Instagram does not identify every Reels swipe, so this can include other scrolling and may miss some Reels. It is an estimate, not a verified video count.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("The service is filtered to Instagram and does not retrieve screen text or content. You enable or disable it in Android Accessibility settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PermissionInfoCard(title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBubble(Icons.Rounded.Security, MaterialTheme.colorScheme.primary, 44.dp)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open Android settings") }
        }
    }
}

@Composable
private fun NumberLimitSheet(title: String, current: Int, helper: String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var input by remember(title, current) { mutableStateOf(if (current > 0) current.toString() else "") }
    FormSheet(title, onDismiss) {
        OutlinedTextField(input, { input = it.filter { character -> character.isDigit() }.take(5) }, label = { Text(helper) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Text("Leave the field empty or enter 0 to remove the limit.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BigButton("Save limit") { onSave(input.toIntOrNull() ?: 0) }
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
