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
import android.provider.Settings
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import java.util.Date

internal data class BatteryReading(val percent: Int, val charging: Boolean, val source: String)

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
    return BatteryReading(
        percent = if (level < 0) 0 else (level * 100f / scale).toInt().coerceIn(0, 100),
        charging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL),
        source = source
    )
}

@Composable
fun BatteryScreen() {
    val context = LocalContext.current
    var diagnostic by remember { mutableStateOf(BatteryAnimationDiagnostics.latest(context)) }
    var lastPluggedState by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(BatteryReading(0, false, "Reading battery…")) }
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
                    }
                }
                diagnostic = BatteryAnimationDiagnostics.latest(appContext)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    diagnostic = BatteryAnimationDiagnostics.latest(appContext)
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
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
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
        backgroundListener = context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE)
            .getBoolean(BatteryChargingMonitorService.KEY_BACKGROUND_LISTENER, false)
        notificationsAllowed = MomentumNotificationSettings.notificationsAllowed(context)
    }
    val styles = listOf("Pulse", "Flow", "Glow", "Neon Ring", "Dual Orbit", "Spark", "Wave", "Heartbeat", "Rainbow", "Comet", "Solar Flare", "Ripple", "Segment", "Liquid", "Aurora", "Matrix", "Firefly", "Prism", "Galaxy", "Radar", "Energy Core", "Spiral", "Minimal")

    Column(Modifier.fillMaxSize()) {
        LazyBatteryContent(
            reading, style, styles, autoShow, backgroundListener, overlayAllowed, notificationsAllowed,
            diagnostic = diagnostic,
            onRefreshDiagnostics = { diagnostic = BatteryAnimationDiagnostics.latest(context) },
            onCopyDiagnostics = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Momentum charging diagnostics", "${diagnostic.status}\n${diagnostic.detail}"))
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
            onPopupPreview = { if (Settings.canDrawOverlays(context)) showBatteryChargingOverlayPreview(context) else openOverlaySettings(context) }
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
    onPopupPreview: () -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 12.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeroCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Battery studio", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = .85f))
                    Text(if (reading.charging) "Charging" else "On battery", fontSize = 12.sp, color = Color.White.copy(alpha = .9f))
                }
                Spacer(Modifier.height(4.dp))
                Text(if (reading.charging) "Charging in progress" else "Battery at a glance", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Live device status with a calm charging animation", fontSize = 13.sp, color = Color.White.copy(alpha = .88f))
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    BatteryOrb(reading.percent, reading.charging, style)
                }
                Spacer(Modifier.height(14.dp))
                Text("${reading.percent}%", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (reading.charging) "${reading.source} · charging" else reading.source, fontSize = 14.sp, color = Color.White.copy(alpha = .9f))
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onPreview) { Text("Preview full-screen animation") }
            }
        }
        item { SectionLabel("Animation style") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                styles.forEach { option ->
                    val selected = style == option
                    Card(
                        onClick = { onStyle(option) },
                        modifier = Modifier.width(132.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(82.dp).clip(CircleShape).background(Color(0xFF12203A)), contentAlignment = Alignment.Center) {
                                BatteryOrb(72, true, option, Modifier.size(72.dp))
                            }
                            Text(option, fontWeight = FontWeight.SemiBold, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
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
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Live while this screen is open", fontWeight = FontWeight.SemiBold)
                        Text("The animation follows the device battery level and charging state.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        item { SectionLabel("Automatic charging animation") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryChargingFull, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Show when power connects", fontWeight = FontWeight.SemiBold)
                            Text("Displays the selected animation for a few seconds when you plug in.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoShow, onCheckedChange = onAutoShow)
                    }
                    if (autoShow) {
                        Text(
                            when {
                                overlayAllowed -> "Ready: $style animation will appear when the screen is on. When locked, Momentum uses a notification."
                                notificationsAllowed -> "Notifications are ready. Allow Display over other apps for the animation to pop up over the current screen."
                                else -> "Allow notifications for a reliable fallback, and Display over other apps for the pop-up animation."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!notificationsAllowed) OutlinedButton(onClick = onNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                            Text("Allow charging notifications")
                        }
                        if (!overlayAllowed) OutlinedButton(onClick = onOverlayPermission, modifier = Modifier.fillMaxWidth()) {
                            Text("Allow Display over other apps")
                        }
                        if (overlayAllowed) OutlinedButton(onClick = onPopupPreview, modifier = Modifier.fillMaxWidth()) {
                            Text("Test charging pop-up")
                        }
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Keep listener active in background", fontWeight = FontWeight.SemiBold)
                                Text("Needed after Momentum is removed from Recents. Android keeps a small ongoing notification while this is on.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = backgroundListener, onCheckedChange = onBackgroundListener)
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("The automatic pop-up is optional and dismisses itself. If Android blocks overlays, or the phone is locked, Momentum shows a notification instead. Lock-screen display follows Android notification settings.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Charging diagnostics", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (diagnostic.timestamp == 0L) "Waiting for a charger event" else java.text.DateFormat.getDateTimeInstance().format(Date(diagnostic.timestamp)),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRefreshDiagnostics) {
                            Icon(Icons.Rounded.Refresh, "Refresh diagnostics")
                        }
                    }
                    Text(diagnostic.status, fontWeight = FontWeight.SemiBold, color = if (diagnostic.status.contains("failed", true) || diagnostic.status.contains("blocked", true) || diagnostic.status.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(diagnostic.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8)
                    OutlinedButton(onClick = onCopyDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Copy diagnostic details") }
                    Text("After plugging in, refresh here to see whether Android delivered the event, the overlay opened, or a permission blocked it.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun batteryStyleDescription(style: String): String = when (style) {
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
fun BatteryOrb(percent: Int, charging: Boolean, style: String, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(percent / 100f, tween(650), label = "battery-level")
    val pulse by rememberInfiniteTransition(label = "battery-pulse").animateFloat(
        initialValue = .72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (style == "Flow") 850 else 1400), RepeatMode.Reverse), label = "pulse"
    )
    val alpha = if (charging && style != "Glow") pulse else .9f
    val color = if (charging) Color(0xFFBBF7D0) else Color.White
    Box(modifier.size(154.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val bounds = androidx.compose.ui.geometry.Offset(inset, inset)
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val track = Color.White.copy(alpha = .22f)
            val active = color.copy(alpha = alpha)
            drawArc(track, -90f, 360f, false, bounds, arcSize, style = Stroke(stroke))
            drawArc(active, -90f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            when (style) {
                "Segment" -> repeat(24) { i ->
                    val sweep = 360f / 24f - 4f
                    drawArc(if (i < (24 * progress).toInt()) active else track, -90f + i * 15f, sweep, false, bounds, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                "Dual Orbit", "Galaxy", "Firefly", "Spark", "Matrix", "Comet", "Flow" -> {
                    val count = when (style) { "Dual Orbit" -> 2; "Galaxy" -> 6; "Firefly" -> 5; "Spark" -> 9; "Matrix" -> 12; else -> 3 }
                    repeat(count) { i ->
                        val angle = (pulse * 360f + i * 360f / count) * (Math.PI / 180)
                        val radius = size.minDimension * if (style == "Galaxy") .30f + (i % 3) * .055f else .46f
                        val dot = if (style == "Spark") 2.dp.toPx() else 3.5.dp.toPx()
                        drawCircle(if (style == "Matrix") Color(0xFF86EFAC) else active, dot, androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius))
                    }
                }
                "Glow", "Neon Ring", "Prism", "Energy Core" -> {
                    drawCircle(active.copy(alpha = if (style == "Energy Core") .22f * pulse else .12f * pulse), size.minDimension * .46f)
                    if (style == "Neon Ring" || style == "Prism") drawArc(Color(0xFF67E8F9), -90f + pulse * 90f, 100f, false, bounds, arcSize, style = Stroke(3.dp.toPx()))
                }
                "Solar Flare" -> repeat(16) { i ->
                    val angle = i * 22.5 * Math.PI / 180
                    val r1 = size.minDimension * .39f
                    val r2 = size.minDimension * (.44f + .025f * pulse)
                    drawLine(active.copy(alpha = .7f), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * r1, center.y + kotlin.math.sin(angle).toFloat() * r1), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * r2, center.y + kotlin.math.sin(angle).toFloat() * r2), 2.dp.toPx())
                }
                "Ripple", "Wave", "Aurora", "Liquid" -> {
                    repeat(if (style == "Ripple") 3 else 2) { i ->
                        val radius = size.minDimension * (.25f + i * .10f + .025f * pulse)
                        drawCircle(active.copy(alpha = .22f - i * .05f), radius, center, style = Stroke(2.dp.toPx()))
                    }
                }
                "Heartbeat" -> drawArc(active, -90f + pulse * 8f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke * (1f + .25f * pulse), cap = StrokeCap.Round))
                "Rainbow" -> drawArc(Color(0xFF93C5FD), -90f, 360f * progress, false, bounds, arcSize, style = Stroke(stroke * .55f, cap = StrokeCap.Round))
                "Radar" -> drawArc(Color(0xFF67E8F9).copy(alpha = .8f), pulse * 360f, 70f, false, bounds, arcSize, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                "Spiral" -> repeat(5) { i ->
                    val angle = (pulse * 360f + i * 55f) * Math.PI / 180
                    val radius = size.minDimension * (.12f + i * .065f)
                    drawCircle(active.copy(alpha = .8f), 3.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius))
                }
                "Minimal" -> drawArc(Color.White.copy(alpha = .8f), -90f, 360f * progress, false, bounds, arcSize, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Icon(if (charging) Icons.Rounded.Bolt else if (percent >= 80) Icons.Rounded.BatteryFull else Icons.Rounded.Power, null, Modifier.size(58.dp), tint = color.copy(alpha = alpha))
    }
}
