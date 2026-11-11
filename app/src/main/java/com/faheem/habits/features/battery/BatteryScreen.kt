@file:Suppress("FunctionNaming", "LongParameterList", "CyclomaticComplexMethod", "TooManyFunctions")

package com.faheem.habits.features.battery
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.faheem.habits.features.screen_time.hasUsageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

internal data class BatteryReading(
    val percent: Int,
    val charging: Boolean,
    val source: String,
    val health: String = "Unknown",
    val temperatureC: Float? = null,
    val voltageMv: Int? = null,
    val technology: String = "Unknown"
)

internal fun batteryReading(intent: Intent?): BatteryReading {
    if (intent == null) return BatteryReading(0, false, "Battery status unavailable")
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val source = when {
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless power"
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB power"
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC power"
        else -> "On battery"
    }
    val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
    val temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
    val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Unknown"
    return BatteryReading(
        percent = if (level < 0) 0 else (level * 100f / scale).toInt().coerceIn(0, 100),
        charging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL),
        source = source,
        health = batteryHealthLabel(health),
        temperatureC = if (temperatureTenths == Int.MIN_VALUE) null else temperatureTenths / 10f,
        voltageMv = voltage.takeIf { it > 0 },
        technology = technology
    )
}

private fun batteryHealthLabel(value: Int): String = when (value) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Replace"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
    else -> "Unknown"
}

@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    var diagnostic by remember { mutableStateOf(BatteryAnimationDiagnostics.latest(context)) }
    var lastPluggedState by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(BatteryReading(0, false, "Reading battery…")) }
    var sessions by remember { mutableStateOf(BatterySessionHistory.recent(context)) }
    var usageReport by remember { mutableStateOf<BatteryUsageReport?>(null) }
    var levels by remember { mutableStateOf(BatteryLevelHistory.samples(context)) }
    var usageAllowed by remember { mutableStateOf(hasUsageAccess(context)) }
    var usageRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val appContext = context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> BatteryAnimationDiagnostics.record(appContext, "Plug-in signal received by open Battery screen", "The live receiver observed ACTION_POWER_CONNECTED. The manifest receiver may separately launch the overlay.")
                    Intent.ACTION_POWER_DISCONNECTED -> BatteryAnimationDiagnostics.record(appContext, "Unplug signal received by open Battery screen", "The live receiver observed ACTION_POWER_DISCONNECTED.")
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val next = batteryReading(intent)
                        if (next.charging != lastPluggedState) {
                            BatteryAnimationDiagnostics.record(
                                appContext,
                                if (next.charging) "Live battery state changed to charging" else "Live battery state changed to unplugged",
                                "ACTION_BATTERY_CHANGED changed the state from ${if (lastPluggedState) "charging" else "on battery"} to ${if (next.charging) "charging" else "on battery"} (${next.source}, ${next.percent}%)."
                            )
                        }
                        lastPluggedState = next.charging
                        reading = next
                        BatteryLevelHistory.record(appContext, next.percent, next.charging)
                        BatteryUsageEstimator.ensureDailyAnchor(appContext)
                    }
                }
                diagnostic = BatteryAnimationDiagnostics.latest(appContext)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    diagnostic = BatteryAnimationDiagnostics.latest(appContext)
                    sessions = BatterySessionHistory.recent(appContext)
                }, 350)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val initial = ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        reading = batteryReading(initial ?: context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        lastPluggedState = reading.charging
        BatteryLevelHistory.record(context, reading.percent, reading.charging)
        BatteryUsageEstimator.ensureDailyAnchor(context)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(reading.percent, reading.charging, usageRefresh) {
        levels = BatteryLevelHistory.samples(context)
        usageAllowed = hasUsageAccess(context)
        usageReport = withContext(Dispatchers.IO) { BatteryUsageEstimator.load(context) }
    }
    var style by remember { mutableStateOf(BatteryAnimationPreferences.style(context)) }
    var autoShow by remember { mutableStateOf(BatteryAnimationPreferences.autoShow(context)) }
    var backgroundListener by remember {
        mutableStateOf(context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE)
            .getBoolean(BatteryChargingMonitorService.KEY_BACKGROUND_LISTENER, false))
    }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notificationsAllowed by remember { mutableStateOf(MomentumNotificationSettings.notificationsAllowed(context)) }
    LaunchedEffect(reading) {
        delay(500)
        diagnostic = BatteryAnimationDiagnostics.latest(context)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        notificationsAllowed = allowed && MomentumNotificationSettings.notificationsAllowed(context)
        if (allowed && !Settings.canDrawOverlays(context)) openOverlaySettings(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayAllowed = Settings.canDrawOverlays(context)
        autoShow = BatteryAnimationPreferences.autoShow(context)
        sessions = BatterySessionHistory.recent(context)
        backgroundListener = context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE)
            .getBoolean(BatteryChargingMonitorService.KEY_BACKGROUND_LISTENER, false)
        notificationsAllowed = MomentumNotificationSettings.notificationsAllowed(context)
    }
    val styles = BATTERY_ANIMATION_STYLES

    Column(Modifier.fillMaxSize()) {
        LazyBatteryContent(
            reading, style, styles, autoShow, backgroundListener, overlayAllowed, notificationsAllowed,
            diagnostic = diagnostic,
            onRefreshDiagnostics = { diagnostic = BatteryAnimationDiagnostics.latest(context) },
            onCopyDiagnostics = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Zylo charging diagnostics", "${diagnostic.status}\n${diagnostic.detail}"))
            },
            onStyle = {
                style = it
                context.getSharedPreferences(BatteryAnimationPreferences.NAME, android.content.Context.MODE_PRIVATE)
                    .edit().putString(BatteryAnimationPreferences.KEY_STYLE, it).apply()
            },
            onAutoShow = {
                autoShow = it
                context.getSharedPreferences(BatteryAnimationPreferences.NAME, android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean(BatteryAnimationPreferences.KEY_AUTO_SHOW, it).apply()
                if (it && Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else if (it && !Settings.canDrawOverlays(context)) {
                    openOverlaySettings(context)
                }
            },
            onBackgroundListener = { enabled ->
                try {
                    BatteryChargingMonitorService.setEnabled(context, enabled)
                    backgroundListener = enabled
                } catch (error: Throwable) {
                    backgroundListener = false
                    BatteryAnimationDiagnostics.error(context, "Could not enable background charging listener", error)
                    diagnostic = BatteryAnimationDiagnostics.latest(context)
                }
            },
            onOverlayPermission = { openOverlaySettings(context) },
            onNotificationPermission = {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else runCatching { context.startActivity(Intent(ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
            },
            onPreview = { context.startActivity(Intent(context, BatteryChargingActivity::class.java).putExtra(BatteryChargingActivity.EXTRA_PREVIEW, true)) },
            onPopupPreview = { if (Settings.canDrawOverlays(context)) showBatteryChargingOverlayPreview(context) else openOverlaySettings(context) },
            sessions = sessions,
            onClearSessions = {
                BatterySessionHistory.clear(context)
                sessions = BatterySessionHistory.recent(context)
            },
            usage = usageReport,
            levels = levels,
            usageAllowed = usageAllowed,
            onUsageSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            onRefreshUsage = { usageRefresh++ }
        )
    }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LazyBatteryContent(
    reading: BatteryReading,
    style: String,
    styles: List<String>,
    autoShow: Boolean,
    backgroundListener: Boolean,
    overlayAllowed: Boolean,
    notificationsAllowed: Boolean,
    diagnostic: BatteryDiagnostic,
    onRefreshDiagnostics: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onStyle: (String) -> Unit,
    onAutoShow: (Boolean) -> Unit,
    onBackgroundListener: (Boolean) -> Unit,
    onOverlayPermission: () -> Unit,
    onNotificationPermission: () -> Unit,
    onPreview: () -> Unit,
    onPopupPreview: () -> Unit,
    sessions: List<BatteryChargeSession>,
    onClearSessions: () -> Unit,
    usage: BatteryUsageReport?,
    levels: List<BatteryLevelSample>,
    usageAllowed: Boolean,
    onUsageSettings: () -> Unit,
    onRefreshUsage: () -> Unit
) {
    val estimate = rememberBatteryEstimate(reading.percent, reading.charging)
    val displayPercent = estimate.percent
    val reduceMotion = LocalReduceEffects.current
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MomentumTokens.screenPadding,
            vertical = MomentumTokens.space2
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Battery studio", Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                    StatusPill(
                        if (reading.charging) "Charging" else "On battery",
                        if (reading.charging) Positive else Color.White
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (reading.charging) "Charging in progress" else "Battery at a glance",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
                )
                Text("Live device status with a calm charging animation", fontSize = 12.5.sp, color = Color.White.copy(alpha = .88f))
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    BatteryOrb(displayPercent, reading.charging, style, animate = reading.charging && !reduceMotion)
                }
                Spacer(Modifier.height(14.dp))
                AnimatedBatteryPercent(
                    displayPercent,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(if (reading.charging) "${reading.source} · charging" else reading.source, fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                estimate.minutesToFull?.let { minutes ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        etaText(minutes),
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = .92f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Preview full-screen animation", onClick = onPreview, icon = Icons.Rounded.BatteryChargingFull)
            }
        }
        item { SectionHeader("Device health", subtitle = "Live readings from Android") }
        item { BatteryHealthCard(reading) }
        item { SectionHeader("Battery level", subtitle = "Recorded over the last 24 hours") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Insights, MaterialTheme.colorScheme.primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Charge trend", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (levels.size < 2) "Collecting samples while Zylo runs"
                            else "${levels.size} samples · lowest ${levels.minOf { it.percent }}%",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (levels.size >= 2) {
                        val delta = levels.last().percent - levels.first().percent
                        StatusPill(
                            "${if (delta >= 0) "+" else ""}$delta%",
                            if (delta >= 0) Positive else Warning
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (levels.size < 2) {
                    ChartPlaceholder("A trend appears after a few readings")
                } else {
                    LineChart(
                        values = levels.map { it.percent.toFloat() },
                        color = MaterialTheme.colorScheme.primary,
                        height = 148.dp,
                        normalized = false
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            sampleTime(levels.first().timeMillis),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            sampleTime(levels.last().timeMillis),
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { SectionHeader("App energy", subtitle = "Estimated battery use today") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.BatterySaver, MaterialTheme.colorScheme.secondary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Which apps use the battery", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            usage?.let { "Measured from ${it.sinceLabel} · ${it.confidence}" }
                                ?: "Reading usage data…",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefreshUsage) { Icon(Icons.Rounded.Refresh, "Refresh app energy") }
                }
                Spacer(Modifier.height(12.dp))
                val report = usage
                if (report == null || !usageAllowed) {
                    Text(
                        if (!usageAllowed) "Allow Usage access to see which apps used the battery."
                        else "Reading today's app usage…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!usageAllowed) {
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Allow Usage access", onClick = onUsageSettings, icon = Icons.Rounded.Security)
                    }
                } else if (!report.hasData) {
                    ChartPlaceholder("No foreground app usage recorded today")
                } else {
                    val appSlices = report.apps.take(5).mapIndexed { index, app ->
                        ChartSlice(app.label, app.estimatedMah, chartColor(index))
                    }
                    val systemSlices = if (report.systemMah > 0f) {
                        appSlices + ChartSlice(
                            "System & background",
                            report.systemMah,
                            MaterialTheme.colorScheme.outline
                        )
                    } else appSlices
                    CompositionBar(
                        slices = systemSlices,
                        valueFormatter = { energyText(it) }
                    )
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        report.apps.take(5).forEach { app ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.icon, app.label, 32.dp, cornerRadius = 9.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    app.label,
                                    Modifier.weight(1f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(energyText(app.estimatedMah), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Total used today",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            energyText(report.observedDrainMah) +
                                if (report.capacityMah > 0f) " of ${energyText(report.capacityMah)}" else "",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Estimated from foreground time and the charge counter.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { SectionHeader("Animation style", subtitle = "Choose the charging look you like") }
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(styles, key = { it }) { option ->
                    val selected = style == option
                    PremiumCard(
                        onClick = { onStyle(option) },
                        modifier = Modifier.width(132.dp),
                        accent = if (selected) MaterialTheme.colorScheme.primary else null,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(82.dp).clip(CircleShape).background(Color(0xFF12203A)), contentAlignment = Alignment.Center) {
                                BatteryOrb(72f, true, option, Modifier.size(72.dp), animate = selected && !reduceMotion)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            option,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            batteryStyleDescription(option),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        item {
            InfoBanner(
                icon = Icons.Rounded.BatteryChargingFull,
                text = "Live while this screen is open.",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        item { SectionHeader("Automatic charging animation") }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.BatteryChargingFull, MaterialTheme.colorScheme.primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Show when power connects", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Displays the selected animation for a few seconds when you plug in.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = autoShow, onCheckedChange = onAutoShow)
                }
                if (autoShow) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            overlayAllowed -> "Ready. When the phone is locked, Zylo uses a notification instead."
                            notificationsAllowed -> "Allow Display over other apps for the pop-up to appear over the current screen."
                            else -> "Allow notifications for the fallback, and Display over other apps for the pop-up."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    if (!notificationsAllowed) SecondaryButton(
                        "Allow charging notifications",
                        onClick = onNotificationPermission,
                        icon = Icons.Rounded.NotificationsActive
                    )
                    if (!notificationsAllowed) Spacer(Modifier.height(8.dp))
                    if (!overlayAllowed) SecondaryButton(
                        "Allow Display over other apps",
                        onClick = onOverlayPermission,
                        icon = Icons.Rounded.Power
                    )
                    if (!overlayAllowed) Spacer(Modifier.height(8.dp))
                    if (overlayAllowed) SecondaryButton(
                        "Test charging pop-up",
                        onClick = onPopupPreview,
                        icon = Icons.Rounded.Bolt
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep listener active in background", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Keeps the pop-up working after Zylo leaves Recents.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = backgroundListener, onCheckedChange = onBackgroundListener)
                    }
                }
            }
        }
        item {
            InfoBanner(
                icon = Icons.Rounded.NotificationsActive,
                text = "If overlays are blocked or the phone is locked, Zylo shows a notification instead.",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        item { SectionHeader("Charge sessions", subtitle = if (sessions.isEmpty()) null else "${sessions.size} recent") }
        if (sessions.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.History,
                    "No charge sessions yet",
                    "Plug in the charger and Zylo will log each session here"
                )
            }
        }
        items(sessions, key = { it.startedAt }) { session -> ChargeSessionCard(session) }
        if (sessions.isNotEmpty()) {
            item {
                SecondaryButton("Clear charge history", onClick = onClearSessions, icon = Icons.Rounded.DeleteSweep)
            }
        }
        item {
            PremiumCard(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.BugReport, MaterialTheme.colorScheme.primary, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Charging diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (diagnostic.timestamp == 0L) "Waiting for a charger event" else java.text.DateFormat.getDateTimeInstance().format(Date(diagnostic.timestamp)),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefreshDiagnostics) {
                        Icon(Icons.Rounded.Refresh, "Refresh diagnostics")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    diagnostic.status,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (diagnostic.status.contains("failed", true) || diagnostic.status.contains("blocked", true) || diagnostic.status.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text(diagnostic.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Copy diagnostic details", onClick = onCopyDiagnostics, icon = Icons.Rounded.ContentCopy)
                Spacer(Modifier.height(8.dp))
                Text("Refresh after plugging in to confirm delivery.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BatteryHealthCard(reading: BatteryReading) {
    val healthColor = when (reading.health) {
        "Good" -> Positive
        "Unknown" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Warning
    }
    PremiumCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.HealthAndSafety, healthColor, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Battery condition", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "Reported by the device fuel gauge",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(reading.health, healthColor)
        }
        Spacer(Modifier.height(10.dp))
        BatteryInfoRow(
            "Temperature",
            reading.temperatureC?.let { String.format(java.util.Locale.US, "%.1f °C", it) } ?: "Not reported"
        )
        BatteryInfoRow("Voltage", reading.voltageMv?.let { "$it mV" } ?: "Not reported")
        BatteryInfoRow("Technology", reading.technology)
        BatteryInfoRow("Power source", reading.source)
    }
}

@Composable
private fun BatteryInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChargeSessionCard(session: BatteryChargeSession) {
    PremiumCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.BatteryChargingFull, MaterialTheme.colorScheme.primary, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${session.startPercent}% → ${session.endPercent}%",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
                Text(
                    "${sessionDateFormat(session.startedAt)} · ${durationLabel(session.durationMinutes)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                "+${session.gainedPercent}%",
                if (session.gainedPercent > 0) Positive else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(session.source, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun etaText(minutes: Int): String = when {
    minutes <= 0 -> "Almost full"
    minutes < 60 -> "About $minutes min until full"
    else -> "About ${minutes / 60} h ${minutes % 60} min until full"
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 1 -> "Under a minute"
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun sessionDateFormat(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(Date(millis))

private fun sampleTime(millis: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(millis))

private fun batteryStyleDescription(style: String): String = when (style) {
    "Liquid Glass" -> "Liquid-filled cell"
    "Plasma Storm" -> "Electric storm arcs"
    "Pulse" -> "Soft breathing"
    "Flow" -> "Moving energy"
    "Glow" -> "Radiant glow"
    "Neon Ring" -> "Bright neon edge"
    "Dual Orbit" -> "Two orbiting lights"
    "Spark" -> "Electric sparks"
    "Wave" -> "Wave-like motion"
    "Heartbeat" -> "Heartbeat rhythm"
    "Rainbow" -> "Color cycling"
    "Comet" -> "Trailing light"
    "Solar Flare" -> "Radiant rays"
    "Ripple" -> "Expanding rings"
    "Segment" -> "Segmented progress"
    "Liquid" -> "Fluid energy"
    "Aurora" -> "Northern lights"
    "Matrix" -> "Digital pulses"
    "Firefly" -> "Floating lights"
    "Prism" -> "Prismatic accents"
    "Galaxy" -> "Orbiting stars"
    "Radar" -> "Scanning sweep"
    "Energy Core" -> "Bright center"
    "Spiral" -> "Spinning spiral"
    else -> "Clean minimal ring"
}

@Composable
fun BatteryOrb(percent: Float, charging: Boolean, style: String, modifier: Modifier = Modifier, animate: Boolean = charging) {
    val progress = percent.coerceIn(0f, 100f) / 100f
    val pulse = if (animate) {
        rememberInfiniteTransition(label = "battery-pulse").animateFloat(
            initialValue = .72f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (style == "Flow") 850 else 1400), RepeatMode.Reverse), label = "pulse"
        )
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(.86f) }
    }
    val color = if (charging) Color(0xFFBBF7D0) else Color.White
    Box(modifier.size(154.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Read animation state in the draw phase so charging frames redraw
            // the orb without recomposing its surrounding Compose hierarchy.
            val framePulse = pulse.value
            val alpha = if (charging && style != "Glow") framePulse else .9f
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val bounds = androidx.compose.ui.geometry.Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val track = Color.White.copy(alpha = .22f)
            val active = color.copy(alpha = alpha)
            if (style == "Liquid Glass") {
                val cellW = size.minDimension * .58f
                val cellH = size.minDimension * .82f
                val corner = cellW * .24f
                val left = center.x - cellW / 2
                val top = center.y - cellH / 2 + size.minDimension * .04f
                val cellPath = Path().apply {
                    addRoundRect(RoundRect(left, top, left + cellW, top + cellH, corner, corner))
                }
                val nubW = cellW * .32f
                val nubH = size.minDimension * .055f
                drawRoundRect(
                    color = Color.White.copy(alpha = .42f),
                    topLeft = Offset(center.x - nubW / 2, top - nubH),
                    size = Size(nubW, nubH),
                    cornerRadius = CornerRadius(nubH / 2)
                )
                drawPath(cellPath, Color.White.copy(alpha = .10f))
                val inset = cellW * .11f
                val innerPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left + inset, top + inset,
                            left + cellW - inset, top + cellH - inset,
                            corner * .7f, corner * .7f
                        )
                    )
                }
                val innerTop = top + inset
                val innerHeight = cellH - inset * 2
                val liquidTop = innerTop + innerHeight * (1f - progress)
                val wave = size.minDimension * .035f
                val liquid = Path().apply {
                    moveTo(left + inset, top + cellH - inset)
                    lineTo(left + inset, liquidTop)
                    val steps = 36
                    for (step in 0..steps) {
                        val t = step / steps.toFloat()
                        val x = left + inset + (cellW - inset * 2) * t
                        val y = liquidTop + kotlin.math.sin(t * Math.PI * 3.0 + framePulse * Math.PI * 2).toFloat() * wave
                        lineTo(x, y)
                    }
                    lineTo(left + cellW - inset, top + cellH - inset)
                    close()
                }
                clipPath(innerPath) {
                    drawPath(
                        liquid,
                        Brush.verticalGradient(
                            listOf(Color(0xFF67E8F9), Color(0xFF8B5CF6), Color(0xFFC026D3)),
                            startY = liquidTop,
                            endY = top + cellH
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = .38f), Color.Transparent),
                            endY = innerTop + innerHeight * .55f
                        ),
                        topLeft = Offset(left, top),
                        size = Size(cellW, innerHeight * .55f)
                    )
                }
                if (progress > .05f) {
                    repeat(3) { index ->
                        val bubbleX = left + inset + (cellW - inset * 2) * (.25f + index * .22f)
                        val travel = ((framePulse + index * .27f) % 1f) * innerHeight * progress
                        val bubbleY = top + cellH - inset - travel
                        if (bubbleY > liquidTop) {
                            drawCircle(Color.White.copy(alpha = .30f), 1.7.dp.toPx(), Offset(bubbleX, bubbleY))
                        }
                    }
                }
                drawPath(
                    cellPath,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .80f), Color.White.copy(alpha = .12f))
                    ),
                    style = Stroke(3.dp.toPx())
                )
                return@Canvas
            }
            drawArc(track, -90f, 360f, false, bounds, arcSize, style = Stroke(stroke))
            drawArc(active, -90f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            when (style) {
                "Plasma Storm" -> {
                    drawCircle(active.copy(alpha = .16f * framePulse), size.minDimension * .46f)
                    val stormRadius = size.minDimension * .38f
                    repeat(6) { boltIndex ->
                        val baseAngle = (boltIndex * 60f + framePulse * 150f) * (Math.PI / 180)
                        val bolt = Path()
                        val segments = 8
                        for (segment in 0..segments) {
                            val t = segment / segments.toFloat()
                            val angle = baseAngle + (t - .5f) * .55f
                            val jitter = (
                                kotlin.math.sin((boltIndex * 3 + segment * 5).toDouble() + framePulse * 8) * .5 + .5
                                ).toFloat() * size.minDimension * .05f
                            val radius = stormRadius + if (segment % 2 == 0) jitter else -jitter
                            val x = center.x + kotlin.math.cos(angle).toFloat() * radius
                            val y = center.y + kotlin.math.sin(angle).toFloat() * radius
                            if (segment == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
                        }
                        drawPath(
                            bolt,
                            Color(0xFFA5F3FC).copy(alpha = .85f),
                            style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawCircle(Color(0xFFE0F2FE).copy(alpha = .45f * framePulse), size.minDimension * .10f)
                }
                "Segment" -> repeat(24) { i ->
                    val sweep = 360f / 24f - 4f
                    drawArc(if (i < (24 * progress).toInt()) active else track, -90f + i * 15f, sweep, false, bounds, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                "Dual Orbit", "Galaxy", "Firefly", "Spark", "Matrix", "Comet", "Flow" -> {
                    val count = when (style) { "Dual Orbit" -> 2; "Galaxy" -> 6; "Firefly" -> 5; "Spark" -> 9; "Matrix" -> 12; else -> 3 }
                    repeat(count) { i ->
                        val angle = (framePulse * 360f + i * 360f / count) * (Math.PI / 180)
                        val radius = size.minDimension * if (style == "Galaxy") .30f + (i % 3) * .055f else .46f
                        val dot = if (style == "Spark") 2.dp.toPx() else 3.5.dp.toPx()
                        drawCircle(if (style == "Matrix") Color(0xFF86EFAC) else active, dot, androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius))
                    }
                }
                "Glow", "Neon Ring", "Prism", "Energy Core" -> {
                    drawCircle(active.copy(alpha = if (style == "Energy Core") .22f * framePulse else .12f * framePulse), size.minDimension * .46f)
                    if (style == "Neon Ring" || style == "Prism") drawArc(Color(0xFF67E8F9), -90f + framePulse * 90f, 100f, false, bounds, arcSize, style = Stroke(3.dp.toPx()))
                }
                "Solar Flare" -> repeat(16) { i ->
                    val angle = i * 22.5 * Math.PI / 180
                    val r1 = size.minDimension * .39f
                    val r2 = size.minDimension * (.44f + .025f * framePulse)
                    drawLine(active.copy(alpha = .7f), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * r1, center.y + kotlin.math.sin(angle).toFloat() * r1), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * r2, center.y + kotlin.math.sin(angle).toFloat() * r2), 2.dp.toPx())
                }
                "Ripple", "Wave", "Aurora", "Liquid" -> {
                    repeat(if (style == "Ripple") 3 else 2) { i ->
                        val radius = size.minDimension * (.25f + i * .10f + .025f * framePulse)
                        drawCircle(active.copy(alpha = .22f - i * .05f), radius, center, style = Stroke(2.dp.toPx()))
                    }
                }
                "Heartbeat" -> drawArc(active, -90f + framePulse * 8f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke * (1f + .25f * framePulse), cap = StrokeCap.Round))
                "Rainbow" -> drawArc(Color(0xFF93C5FD), -90f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke * .55f, cap = StrokeCap.Round))
                "Radar" -> drawArc(Color(0xFF67E8F9).copy(alpha = .8f), framePulse * 360f, 70f, false, bounds, arcSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                "Spiral" -> repeat(5) { i ->
                    val angle = (framePulse * 360f + i * 55f) * Math.PI / 180
                    val radius = size.minDimension * (.12f + i * .065f)
                    drawCircle(active.copy(alpha = .8f), 3.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius))
                }
                "Minimal" -> drawArc(Color.White.copy(alpha = .8f), -90f, 360f * progress, false, bounds, arcSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        if (style != "Liquid Glass") {
            Icon(
                if (charging) Icons.Rounded.Bolt else if (percent >= 80f) Icons.Rounded.BatteryFull else Icons.Rounded.Power,
                null,
                Modifier.size(58.dp).graphicsLayer {
                    alpha = if (animate && style != "Glow") pulse.value else .9f
                },
                tint = color
            )
        }
    }
}

@Composable
internal fun AnimatedBatteryPercent(
    percent: Float,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 54.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.White
) {
    Text(
        text = String.format(java.util.Locale.ROOT, "%.2f%%", percent.coerceIn(0f, 100f)),
        modifier = modifier.semantics {
            contentDescription = "Estimated battery level ${String.format(java.util.Locale.ROOT, "%.2f", percent.coerceIn(0f, 100f))} percent. Android reports battery level in whole percentages."
        },
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        maxLines = 1
    )
}

internal data class BatteryEstimate(val percent: Float, val minutesToFull: Int?)

@Composable
internal fun rememberBatteryEstimate(percent: Int, charging: Boolean): BatteryEstimate {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val estimator = remember { BatteryPercentEstimator(percent, charging, now) }

    LaunchedEffect(estimator) {
        while (true) {
            delay(1_000)
            now = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(estimator, percent, charging) {
        estimator.update(percent, charging, SystemClock.elapsedRealtime())
        now = SystemClock.elapsedRealtime()
    }
    return BatteryEstimate(estimator.estimate(now), estimator.minutesToFull(now))
}

@Composable
internal fun rememberBatteryPercentEstimate(percent: Int, charging: Boolean): Float =
    rememberBatteryEstimate(percent, charging).percent

internal class BatteryPercentEstimator(initialPercent: Int, initialCharging: Boolean, now: Long) {
    private var lastReportedPercent = initialPercent.coerceIn(0, 100)
    private var charging = initialCharging
    private var anchorPercent = (lastReportedPercent + .5f).coerceIn(0f, 100f)
    private var anchorTime = now
    private var lastReportedAt = now
    private var ratePerSecond = defaultRate(initialCharging)

    fun update(percent: Int, isCharging: Boolean, now: Long) {
        val reported = percent.coerceIn(0, 100)
        val currentEstimate = estimate(now)

        // The screen's initial placeholder is replaced by Android's sticky
        // battery broadcast shortly after composition; treat that as a sync,
        // not as a real multi-percent battery change.
        if (now - lastReportedAt < 10_000L && kotlin.math.abs(reported - lastReportedPercent) > 1) {
            charging = isCharging
            ratePerSecond = defaultRate(isCharging)
            anchorPercent = (reported + if (isCharging) .5f else 0f).coerceIn(0f, 100f)
            anchorTime = now
            lastReportedPercent = reported
            lastReportedAt = now
            return
        }

        if (isCharging != charging) {
            charging = isCharging
            ratePerSecond = defaultRate(isCharging)
            anchorPercent = currentEstimate.coerceIn(lowerBound(reported, isCharging), upperBound(reported, isCharging))
            anchorTime = now
            lastReportedPercent = reported
            lastReportedAt = now
            return
        }

        if (reported != lastReportedPercent) {
            val elapsedSeconds = (now - lastReportedAt).coerceAtLeast(1L) / 1_000f
            val observedRate = kotlin.math.abs(reported - lastReportedPercent) / elapsedSeconds
            if (elapsedSeconds in 15f..3_600f && observedRate.isFinite() && observedRate > 0f) {
                ratePerSecond = (ratePerSecond * .65f + observedRate * .35f)
                    .coerceIn(1f / 3_600f, 1f / 20f)
            }
            anchorPercent = currentEstimate.coerceIn(lowerBound(reported, charging), upperBound(reported, charging))
            anchorTime = now
            lastReportedPercent = reported
            lastReportedAt = now
        }
    }

    fun estimate(now: Long): Float {
        val elapsedSeconds = (now - anchorTime).coerceAtLeast(0L) / 1_000f
        val direction = if (charging) 1f else -1f
        val estimate = anchorPercent + elapsedSeconds * ratePerSecond * direction
        return estimate.coerceIn(lowerBound(lastReportedPercent, charging), upperBound(lastReportedPercent, charging))
    }

    fun minutesToFull(now: Long): Int? {
        val remaining = 100f - estimate(now)
        return when {
            !charging -> null
            remaining <= 0f -> 0
            else -> (remaining / ratePerSecond / 60f).toInt().coerceAtLeast(1)
        }
    }

    private fun lowerBound(reported: Int, isCharging: Boolean) =
        if (isCharging) reported.toFloat() else (reported - .99f).coerceAtLeast(0f)

    private fun upperBound(reported: Int, isCharging: Boolean) =
        if (isCharging) (reported + .99f).coerceAtMost(100f) else reported.toFloat()

    private fun defaultRate(isCharging: Boolean) = 1f / if (isCharging) 240f else 360f
}
