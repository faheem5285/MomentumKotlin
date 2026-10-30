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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
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
            loadError = false
            try {
                report = withContext(Dispatchers.IO) { loadNetworkReport(context, period) }
                loadError = report?.wifi == null && report?.mobile == null
            } catch (_: Exception) {
                report = null
                loadError = true
            } finally {
                loading = false
            }
        } else {
            report = null
            loadError = false
            loading = false
        }
    }
    val settings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                HeroCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Network usage", Modifier.weight(1f), fontSize = 14.sp, color = Color.White.copy(alpha = .86f))
                        HeroRefreshButton(onClick = { refreshKey++ }, description = "Refresh network usage")
                    }
                    val total = report?.let { if (it.wifi != null && it.mobile != null) mb(it.wifi + it.mobile) else "Unavailable" } ?: "—"
                    Text(total, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
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
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Persistent network indicator", fontWeight = FontWeight.SemiBold)
                                Text("Keeps live speed and today’s Wi-Fi and mobile totals in a status-bar notification.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NetworkPill("Live speed in notification")
                                NetworkPill("Wi-Fi ${mb(report?.wifi)}")
                                NetworkPill("Data ${mb(report?.mobile)}")
                            }
                        }
                        if (!granted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                                Text("Grant Usage access below to include the daily Wi-Fi and data totals in the indicator.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetworkMetricCard("Wi-Fi", mb(report?.wifi), "Received + sent", Icons.Rounded.Wifi, Modifier.weight(1f))
                    NetworkMetricCard("Mobile data", mb(report?.mobile), "Received + sent", Icons.Rounded.CellTower, Modifier.weight(1f))
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(report?.country ?: "Country", fontWeight = FontWeight.SemiBold)
                            Text(report?.countrySource ?: "Reading network country…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!granted) item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow Usage access", fontWeight = FontWeight.Bold)
                        Text("Android requires Usage access to show device-wide Wi-Fi and mobile totals. The permission stays under your control.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = settings) { Text("Open Usage access settings") }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (loadError && !loading) item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Usage data unavailable", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Android could not provide network totals. Check Usage access and try again.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        TextButton(onClick = { refreshKey++ }) {
                            Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            item {
                Text("Totals use decimal MB (1 MB = 1,000,000 bytes). Android may keep recent or carrier-managed usage unavailable on some devices.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun startNetworkIndicator(context: Context) {
    context.getSharedPreferences(NetworkIndicatorService.PREFS, Context.MODE_PRIVATE).edit().putBoolean(NetworkIndicatorService.KEY_ENABLED, true).apply()
    ContextCompat.startForegroundService(context, Intent(context, NetworkIndicatorService::class.java))
}

@Composable
private fun NetworkPill(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
    }
}

@Composable
private fun NetworkMetricCard(title: String, value: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
