package com.faheem.habits.features.network
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R
import com.faheem.habits.features.screen_time.hasUsageAccess


import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

class NetworkIndicatorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitor: Job? = null
    private var lastRxBytes = -1L
    private var lastTxBytes = -1L
    private var lastSampleAt = 0L
    private var downloadText = "0 B/s"
    private var uploadText = "0 B/s"
    private var wifiToday: Long? = null
    private var mobileToday: Long? = null
    private var usagePeriod = NetworkLimitPeriod.DAILY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ZyloNotifications.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        if (monitor?.isActive != true) monitor = scope.launch { monitorLoop() }
        return START_STICKY
    }

    private suspend fun monitorLoop() {
        var lastUsageRefresh = 0L
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val bytes = if (rx >= 0L && tx >= 0L) rx + tx else -1L
            if (bytes >= 0 && lastRxBytes >= 0 && now > lastSampleAt) {
                val secondsMs = now - lastSampleAt
                downloadText = formatRate((rx - lastRxBytes).coerceAtLeast(0L) * 1000L / secondsMs)
                uploadText = formatRate((tx - lastTxBytes).coerceAtLeast(0L) * 1000L / secondsMs)
            }
            if (rx >= 0L) lastRxBytes = rx
            if (tx >= 0L) lastTxBytes = tx
            lastSampleAt = now
            if (now - lastUsageRefresh >= USAGE_REFRESH_MS) {
                val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                usagePeriod = NetworkLimitPeriod.fromKey(prefs.getString(KEY_LIMIT_PERIOD, null))
                val totals = loadPeriodTotals(this@NetworkIndicatorService, usagePeriod)
                wifiToday = totals.first
                mobileToday = totals.second
                lastUsageRefresh = now
                checkMobileLimit(mobileToday, usagePeriod)
                checkWifiLimit(wifiToday, usagePeriod)
            }

            withContext(Dispatchers.Main) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun checkMobileLimit(bytes: Long?, period: NetworkLimitPeriod) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val limitMb = prefs.getLong(KEY_MOBILE_LIMIT_MB, 0L)
        checkUsageThresholds(prefs, "mobile", "Mobile data", bytes, limitMb, period)
    }

    private fun checkWifiLimit(bytes: Long?, period: NetworkLimitPeriod) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val limitMb = prefs.getLong(KEY_WIFI_LIMIT_MB, 0L)
        checkUsageThresholds(prefs, "wifi", "Wi-Fi", bytes, limitMb, period)
    }

    private fun checkUsageThresholds(
        prefs: android.content.SharedPreferences,
        key: String,
        label: String,
        bytes: Long?,
        limitMb: Long,
        period: NetworkLimitPeriod
    ) {
        if (bytes == null || limitMb <= 0L) return
        val periodId = period.periodId()
        val periodKey = "${key}_alert_period_id"
        val limitKey = "${key}_alert_limit_mb"
        val levelKey = "${key}_alert_level"
        val sameSchedule = prefs.getString(periodKey, "") == periodId &&
            prefs.getLong(limitKey, -1L) == limitMb
        val lastAlertIndex = if (sameSchedule) prefs.getInt(levelKey, -1) else -1
        val plan = planNetworkAlerts(bytes, limitMb, lastAlertIndex)
        plan.alerts.forEachIndexed { position, alert ->
            UsageLimitNotifications.notifyNetwork(
                context = this,
                label = label,
                bytes = bytes,
                alertNumber = alert.index + 1,
                thresholdBytes = alert.thresholdBytes,
                period = period.label,
                collapsedOlderAlerts = if (position == plan.alerts.lastIndex) plan.collapsedOlderAlerts else 0
            )
        }
        if (!sameSchedule || plan.lastAlertIndex != lastAlertIndex) {
            prefs.edit()
                .putString(periodKey, periodId)
                .putLong(limitKey, limitMb)
                .putInt(levelKey, plan.lastAlertIndex)
                .apply()
        }
    }

    private fun buildNotification(): Notification {
        val open = ZyloNotifications.openApp(this, 9021, "Network")
        val stop = PendingIntent.getService(
            this, 9022, Intent(this, NetworkIndicatorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val wifi = wifiToday?.let(::formatBytes) ?: "Usage access off"
        val mobile = mobileToday?.let(::formatBytes) ?: "Usage access off"
        val speed = "↓ $downloadText · ↑ $uploadText"
        return NotificationCompat.Builder(this, ZyloNotifications.CHANNEL_NETWORK)
            .setSmallIcon(R.drawable.ic_stat_zylo)
        .setContentTitle("↓ $downloadText  ·  ↑ $uploadText")
            .setContentText("Wi-Fi $wifi · Mobile $mobile · ${usagePeriod.label} usage")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Network speed: ↓ $downloadText, ↑ $uploadText\nWi-Fi usage (${usagePeriod.label.lowercase()}): $wifi\nMobile data usage (${usagePeriod.label.lowercase()}): $mobile"))
            .setContentTitle("Network · ${usagePeriod.label.lowercase()}")
            .setContentText("$speed  ·  Wi-Fi $wifi  ·  Mobile $mobile")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$speed\nWi-Fi $wifi  ·  Mobile $mobile"
                )
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .build()
    }

    override fun onDestroy() {
        monitor?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "network_indicator"
        const val KEY_ENABLED = "enabled"
        const val KEY_MOBILE_LIMIT_MB = "mobile_limit_mb"
        const val KEY_WIFI_LIMIT_MB = "wifi_limit_mb"
        const val KEY_LIMIT_PERIOD = "limit_period"
        const val KEY_RESUME_PENDING = "resume_pending"
        const val ACTION_STOP = "com.faheem.habits.NETWORK_INDICATOR_STOP"
        private const val NOTIFICATION_ID = 9020
        private const val SAMPLE_INTERVAL_MS = 3_000L
        private const val USAGE_REFRESH_MS = 15_000L

        fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun pendingResume(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_RESUME_PENDING, false)

        fun markResumePending(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RESUME_PENDING, true).apply()
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                startIfEnabled(context)
            } else {
                context.stopService(Intent(context, NetworkIndicatorService::class.java))
                resetAlarmProgress(context)
            }
        }

        /** Starts only when persisted state asks for it; returns false if Android blocks the start. */
        fun startIfEnabled(context: Context): Boolean {
            if (!enabled(context)) return false
            return try {
                ContextCompat.startForegroundService(
                    context, Intent(context, NetworkIndicatorService::class.java)
                )
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_RESUME_PENDING, false).apply()
                true
            } catch (_: Exception) {
                markResumePending(context)
                false
            }
        }

        fun currentSpeed(context: Context): String = "Live in notification"

        fun resetAlarmProgress(context: Context) {
            UsageLimitNotifications.clearNetworkAlerts(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("mobile_alert_period_id").remove("mobile_alert_limit_mb").remove("mobile_alert_level")
                .remove("wifi_alert_period_id").remove("wifi_alert_limit_mb").remove("wifi_alert_level")
                .apply()
        }

        private fun loadPeriodTotals(context: Context, period: NetworkLimitPeriod): Pair<Long?, Long?> {
            if (!hasUsageAccess(context)) return null to null
            val start = period.startDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = System.currentTimeMillis()
            return queryBytes(context, ConnectivityManager.TYPE_WIFI, start, end) to
                queryBytes(context, ConnectivityManager.TYPE_MOBILE, start, end)
        }

        private fun queryBytes(context: Context, type: Int, start: Long, end: Long): Long? = try {
            val stats = context.getSystemService(Context.NETWORK_STATS_SERVICE) as android.app.usage.NetworkStatsManager
            @Suppress("DEPRECATION")
            val bucket = stats.querySummaryForDevice(type, null, start, end)
            (bucket.rxBytes + bucket.txBytes).coerceAtLeast(0L)
        } catch (_: Exception) { null }

        private fun formatBytes(value: Long): String = String.format(Locale.getDefault(), "%.1f MB", value / 1_000_000.0)
        private fun formatRate(bytesPerSecond: Long): String = when {
            bytesPerSecond >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB/s", bytesPerSecond / 1_000_000.0)
            bytesPerSecond >= 1_000 -> String.format(Locale.getDefault(), "%.0f KB/s", bytesPerSecond / 1_000.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}
