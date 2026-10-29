package com.faheem.habits

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

fun hasUsageAccess(ctx: Context): Boolean {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun loadUsage(ctx: Context): List<AppUsage> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
    val pm = ctx.packageManager
    return stats.values
        .filter { it.totalTimeInForeground >= 60_000 }
        .sortedByDescending { it.totalTimeInForeground }
        .take(40)
        .map {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
            } catch (_: Exception) {
                it.packageName
            }
            val icon = try {
                pm.getApplicationIcon(it.packageName).toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
            AppUsage(it.packageName, label, it.totalTimeInForeground / 60_000, icon)
        }
}

fun durationText(min: Long) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

@Composable
fun ScreenTimeScreen() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    var usage by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    LaunchedEffect(tick) {
        granted = hasUsageAccess(ctx)
        if (granted) usage = withContext(Dispatchers.IO) { loadUsage(ctx) }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconBubble(Icons.Rounded.Timer, MaterialTheme.colorScheme.primary, 88.dp)
            Spacer(Modifier.height(20.dp))
            Text("See your screen time", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Momentum needs Usage access to show how long you use each app today. Everything stays on your phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            BigButton("Open Usage access settings") {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Find Momentum in the list, turn it on, then come back.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = usage.sumOf { it.minutes }
    val maxMin = (usage.maxOfOrNull { it.minutes } ?: 1L).coerceAtLeast(1L)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Screen time today", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
                        Text(durationText(total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("${usage.size} apps used", fontSize = 14.sp)
                    }
                    FilledTonalButton(
                        onClick = { tick++ },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        )
                    ) { Text("Refresh") }
                }
            }
        }
        item { SectionLabel("Most used apps") }
        if (usage.isEmpty()) {
            item { EmptyState(Icons.Rounded.Timer, "Nothing yet", "Apps you use for 1+ minute show up here") }
        }
        items(usage, key = { it.pkg }) { u ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (u.icon != null) {
                        Image(u.icon, null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)))
                    } else {
                        IconBubble(Icons.Rounded.PhoneAndroid, MaterialTheme.colorScheme.primary, 42.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(u.label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(durationText(u.minutes), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(
                            fraction = u.minutes.toFloat() / maxMin,
                            color = MaterialTheme.colorScheme.primary,
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            height = 6.dp
                        )
                    }
                }
            }
        }
    }
}


/** Total minutes of today's screen time, or null if Usage access is not granted. */
fun todayScreenMinutes(ctx: Context): Long? {
    if (!hasUsageAccess(ctx)) return null
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
        .filter { it.totalTimeInForeground >= 60_000 }
        .sumOf { it.totalTimeInForeground / 60_000 }
}
