package com.faheem.habits.features.network
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R
import com.faheem.habits.features.screen_time.hasUsageAccess


import android.app.usage.NetworkStatsManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NetworkPeriod(val label: String) { Today("Today"), Month("This month") }
private data class NetworkReport(val wifi: Long?, val mobile: Long?, val country: String, val countrySource: String)

private fun networkCountry(context: Context): Pair<String, String> {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val iso = try {
        if (Build.VERSION.SDK_INT >= 28) telephony?.networkCountryIso.orEmpty() else ""
    } catch (_: Exception) { "" }
    if (iso.isNotBlank()) return Locale("", iso.uppercase(Locale.ROOT)).getDisplayCountry(Locale.getDefault()) to "Current mobile network"
    val region = Locale.getDefault().country
    if (region.isNotBlank()) return Locale("", region).getDisplayCountry(Locale.getDefault()) to "Device region (network country unavailable)"
    return "Unavailable" to "Android did not provide a country"
}

private fun deviceNetworkBytes(context: Context, networkType: Int, start: Long, end: Long): Long? = try {
    val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    @Suppress("DEPRECATION")
    val bucket = manager.querySummaryForDevice(networkType, null, start, end)
    (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
} catch (_: Exception) { null }

private fun loadNetworkReport(context: Context, period: NetworkPeriod): NetworkReport {
    val zone = ZoneId.systemDefault()
    val startDate = when (period) {
        NetworkPeriod.Today -> LocalDate.now()
        NetworkPeriod.Month -> YearMonth.now().atDay(1)
    }
    val start = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = System.currentTimeMillis()
    val wifi = deviceNetworkBytes(context, ConnectivityManager.TYPE_WIFI, start, end)
    @Suppress("DEPRECATION")
    val mobile = deviceNetworkBytes(context, ConnectivityManager.TYPE_MOBILE, start, end)
    val country = networkCountry(context)
    return NetworkReport(wifi, mobile, country.first, country.second)
}

private fun mb(bytes: Long?): String = bytes?.let { String.format(Locale.getDefault(), "%.1f MB", it / 1_000_000.0) } ?: "Unavailable"

@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    var period by remember { mutableStateOf(NetworkPeriod.Today) }
    var granted by remember { mutableStateOf(hasUsageAccess(context)) }
    var report by remember { mutableStateOf<NetworkReport?>(null) }
    var appReport by remember { mutableStateOf<AppNetworkReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var indicatorEnabled by remember {
        mutableStateOf(context.getSharedPreferences(NetworkIndicatorService.PREFS, Context.MODE_PRIVATE).getBoolean(NetworkIndicatorService.KEY_ENABLED, false))
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) startNetworkIndicator(context) else indicatorEnabled = false
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshKey++ }
    LaunchedEffect(period, refreshKey) {
        granted = hasUsageAccess(context)
        if (granted) {
            loading = true
            report = withContext(Dispatchers.IO) { loadNetworkReport(context, period) }
            appReport = withContext(Dispatchers.IO) {
                val days = when (period) {
                    NetworkPeriod.Today -> 7
                    NetworkPeriod.Month -> LocalDate.now().dayOfMonth.coerceAtLeast(2)
                }
                loadAppNetworkReport(context, days)
            }
            loading = false
        } else {
            report = null
            appReport = null
        }
    }
    val settings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp, 6.dp, 18.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HeroCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Network usage", Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = .84f))
                        HeroRefreshButton(onClick = { refreshKey++ }, description = "Refresh network usage")
                    }
                    val total = report?.let { if (it.wifi != null && it.mobile != null) mb(it.wifi + it.mobile) else "Unavailable" } ?: "—"
                    Text(total, fontSize = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
                    Text("Device traffic · ${period.label.lowercase()}", fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                    Spacer(Modifier.height(14.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        NetworkPeriod.entries.forEachIndexed { index, item ->
                            SegmentedButton(
                                selected = period == item,
                                onClick = { period = item },
                                shape = SegmentedButtonDefaults.itemShape(index, NetworkPeriod.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = Color.White,
                                    activeContentColor = Color(0xFF26327A),
                                    activeBorderColor = Color.White,
                                    inactiveContainerColor = Color.Transparent,
                                    inactiveContentColor = Color.White,
                                    inactiveBorderColor = Color.White.copy(alpha = .8f)
                                )
                            ) { Text(item.label) }
                        }
                    }
                }
            }
            item {
                PremiumCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Speed, MaterialTheme.colorScheme.primary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Network indicator", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Live speed and today’s totals in your status bar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = indicatorEnabled, onCheckedChange = { enable ->
                            if (enable) {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else startNetworkIndicator(context)
                                indicatorEnabled = true
                            } else {
                                indicatorEnabled = false
                                context.getSharedPreferences(NetworkIndicatorService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(NetworkIndicatorService.KEY_ENABLED, false).apply()
                                context.startService(Intent(context, NetworkIndicatorService::class.java).setAction(NetworkIndicatorService.ACTION_STOP))
                            }
                        })
                    }
                    if (indicatorEnabled) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NetworkPill("Live speed")
                            NetworkPill("Wi-Fi ${mb(report?.wifi)}")
                            NetworkPill("Data ${mb(report?.mobile)}")
                        }
                    }
                    if (!granted) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Usage access adds daily Wi-Fi and data totals to the indicator.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile(
                        title = "Wi-Fi",
                        value = mb(report?.wifi),
                        detail = "Received + sent",
                        icon = Icons.Rounded.Wifi,
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        title = "Mobile",
                        value = mb(report?.mobile),
                        detail = "Received + sent",
                        icon = Icons.Rounded.CellTower,
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (granted && appReport != null) {
                item { SectionHeader("Traffic trend", subtitle = "Last 7 days") }
                item {
                    val daily = appReport!!.daily
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.TrendingUp, MaterialTheme.colorScheme.primary, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Daily data volume", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "Wi-Fi + mobile combined",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                trafficText(daily.sumOf { it.totalBytes }),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        BarChart(
                            values = daily.map { it.totalBytes / 1_000_000f },
                            labels = daily.map { DateTimeFormatter.ofPattern("EEE", Locale.US).format(it.day) },
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            ),
                            highlightIndex = daily.lastIndex,
                            height = 156.dp,
                            valueFormatter = { value ->
                                if (value >= 1000f) String.format(Locale.US, "%.1fG", value / 1000f)
                                else if (value >= 10f) value.toInt().toString()
                                else String.format(Locale.US, "%.1f", value)
                            }
                        )
                    }
                }
                item {
                    val wifi = appReport!!.daily.sumOf { it.wifiBytes }
                    val mobile = appReport!!.daily.sumOf { it.mobileBytes }
                    val slices = listOf(
                        ChartSlice("Wi-Fi", wifi.toFloat(), MaterialTheme.colorScheme.primary),
                        ChartSlice("Mobile", mobile.toFloat(), MaterialTheme.colorScheme.secondary)
                    )
                    PremiumCard(contentPadding = PaddingValues(16.dp)) {
                        Text("Connection mix", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Share of traffic in this period",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = slices,
                                modifier = Modifier.width(150.dp),
                                centerValue = trafficText(wifi + mobile),
                                centerLabel = "total",
                                thickness = 18.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                ChartLegend(
                                    slices = slices,
                                    valueFormatter = { trafficText(it.toLong()) },
                                    maxRows = 2
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (mobile > wifi) "Mobile data is the larger share"
                                    else "Wi-Fi carries most of the traffic",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader(
                        "Data by app",
                        subtitle = "${appReport!!.apps.size} app${if (appReport!!.apps.size == 1) "" else "s"} · ${period.label.lowercase()}"
                    )
                }
                if (appReport!!.apps.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Rounded.Apps,
                            "No per-app data yet",
                            "Android starts reporting app traffic after apps use the network"
                        )
                    }
                } else {
                    item {
                        val slices = appReport!!.apps.take(6).mapIndexed { index, app ->
                            ChartSlice(app.label, app.totalBytes.toFloat(), chartColor(index))
                        }
                        PremiumCard(contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.DataUsage, MaterialTheme.colorScheme.secondary, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Top consumers", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "Ranked by total bytes",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            HorizontalBarList(
                                items = slices,
                                maxItems = 6,
                                valueFormatter = { trafficText(it.toLong()) }
                            )
                        }
                    }
                    items(appReport!!.apps.take(12), key = { "traffic-${it.uid}-${it.packageName}" }) { app ->
                        AppTrafficRow(app)
                    }
                }
            }
            item {
                PremiumCard(contentPadding = PaddingValues(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.Language, MaterialTheme.colorScheme.primary, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(report?.country ?: "Country", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(report?.countrySource ?: "Reading network country…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!granted) item {
                InfoBanner(
                    icon = Icons.Rounded.Security,
                    text = "Android requires Usage access to show device-wide Wi-Fi and mobile totals. The permission stays under your control.",
                    tint = MaterialTheme.colorScheme.primary,
                    actionLabel = "Open settings",
                    onAction = settings
                )
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) }
            item {
                Text(
                    "Totals use decimal MB (1 MB = 1,000,000 bytes). Android may keep recent or carrier-managed usage unavailable on some devices.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

private fun startNetworkIndicator(context: Context) {
    context.getSharedPreferences(NetworkIndicatorService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(NetworkIndicatorService.KEY_ENABLED, true).apply()
    ContextCompat.startForegroundService(context, Intent(context, NetworkIndicatorService::class.java))
}

@Composable
private fun AppTrafficRow(app: AppNetworkUsage) {
    PremiumCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) {
                Image(app.icon, app.label, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                IconBubble(Icons.Rounded.Apps, MaterialTheme.colorScheme.primary, 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (app.label == app.packageName) {
                    Text(
                        app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(trafficText(app.totalBytes), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        if (app.wifiBytes > 0L || app.mobileBytes > 0L) {
            Spacer(Modifier.height(10.dp))
            CompositionBar(
                slices = listOf(
                    ChartSlice("Wi-Fi", app.wifiBytes.toFloat(), MaterialTheme.colorScheme.primary),
                    ChartSlice("Mobile", app.mobileBytes.toFloat(), MaterialTheme.colorScheme.secondary)
                ),
                valueFormatter = { trafficText(it.toLong()) }
            )
        }
    }
}

@Composable
private fun NetworkPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}
