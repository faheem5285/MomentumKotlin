@file:Suppress("NestedBlockDepth")

package com.faheem.habits.features.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import com.faheem.habits.core.ui.installedAppIdentity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Network traffic attributed to one Android uid (normally one app). */
internal data class AppNetworkUsage(
    val packageName: String,
    val label: String,
    val uid: Int,
    val icon: ImageBitmap?,
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

/** Device traffic for one calendar day. */
internal data class DailyTraffic(
    val day: LocalDate,
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

internal data class AppNetworkReport(
    val apps: List<AppNetworkUsage>,
    val wifiBytes: Long,
    val mobileBytes: Long,
    val daily: List<DailyTraffic>,
    val supported: Boolean
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes
}

private fun dayStart(day: LocalDate): Long =
    day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Reads per-uid byte counters from NetworkStatsManager.
 *
 * Android exposes these counters only with Usage access. Some OEM builds throw
 * when a mobile subscriber id is unavailable, so each query is isolated: a
 * failure on one transport still returns the other one.
 */
private fun bytesByUid(context: Context, networkType: Int, start: Long, end: Long): Map<Int, Long> {
    return try {
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyMap()
        val stats = manager.querySummary(networkType, null, start, end)
        val bucket = NetworkStats.Bucket()
        val result = HashMap<Int, Long>()
        try {
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0) result[bucket.uid] = (result[bucket.uid] ?: 0L) + bytes
            }
        } finally {
            runCatching { stats.close() }
        }
        result
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun deviceBytes(context: Context, networkType: Int, start: Long, end: Long): Long {
    return try {
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return 0L
        @Suppress("DEPRECATION")
        val bucket = manager.querySummaryForDevice(networkType, null, start, end)
        (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
    } catch (_: Exception) {
        0L
    }
}

private fun appInfo(context: Context, uid: Int): Triple<String, String, ImageBitmap?> {
    val packages = try {
        context.packageManager.getPackagesForUid(uid)
    } catch (_: Exception) {
        null
    }
    val packageName = packages?.firstOrNull() ?: return Triple("uid:$uid", "System service", null)
    val identity = installedAppIdentity(context, packageName)
    return Triple(packageName, identity.label, identity.icon)
}

internal fun loadAppNetworkReport(context: Context, periodDays: Int = 7): AppNetworkReport {
    val today = LocalDate.now()
    val start = dayStart(today.minusDays((periodDays - 1).coerceAtLeast(1).toLong()))
    val end = System.currentTimeMillis()
    val wifiByUid = bytesByUid(context, ConnectivityManager.TYPE_WIFI, start, end)
    val mobileByUid = bytesByUid(context, ConnectivityManager.TYPE_MOBILE, start, end)
    val uids = (wifiByUid.keys + mobileByUid.keys).filter { it > 0 }
    val apps = uids.map { uid ->
        val (packageName, label, icon) = appInfo(context, uid)
        AppNetworkUsage(
            packageName = packageName,
            label = label,
            uid = uid,
            icon = icon,
            wifiBytes = wifiByUid[uid] ?: 0L,
            mobileBytes = mobileByUid[uid] ?: 0L
        )
    }.filter { it.packageName != context.packageName }
        .sortedByDescending { it.totalBytes }
        .take(40)
    val daily = (periodDays - 1 downTo 0).map { offset ->
        val day = today.minusDays(offset.toLong())
        val dayBegin = dayStart(day)
        val dayEnd = dayStart(day.plusDays(1)).coerceAtMost(end)
        DailyTraffic(
            day = day,
            wifiBytes = deviceBytes(context, ConnectivityManager.TYPE_WIFI, dayBegin, dayEnd),
            mobileBytes = deviceBytes(context, ConnectivityManager.TYPE_MOBILE, dayBegin, dayEnd)
        )
    }
    val wifi = daily.sumOf { it.wifiBytes }.takeIf { it > 0 } ?: wifiByUid.values.sum()
    val mobile = daily.sumOf { it.mobileBytes }.takeIf { it > 0 } ?: mobileByUid.values.sum()
    return AppNetworkReport(
        apps = apps,
        wifiBytes = wifi,
        mobileBytes = mobile,
        daily = daily,
        supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    )
}

internal fun trafficText(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format(Locale.US, "%.0f KB", bytes / 1_000.0)
    bytes > 0L -> "$bytes B"
    else -> "0 MB"
}
