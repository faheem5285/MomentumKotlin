package com.faheem.habits.features.network
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R
import com.faheem.habits.app.MainActivity
import com.faheem.habits.features.screen_time.hasUsageAccess


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
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
                val totals = loadTodayTotals(this@NetworkIndicatorService)
                wifiToday = totals.first
                mobileToday = totals.second
                lastUsageRefresh = now
            }
            withContext(Dispatchers.Main) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
            }
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Network speed and usage", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Persistent live network speed and today's Wi-Fi and mobile data totals."
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 9021, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 9022, Intent(this, NetworkIndicatorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val wifi = wifiToday?.let(::formatBytes) ?: "Usage access off"
        val mobile = mobileToday?.let(::formatBytes) ?: "Usage access off"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("↓ $downloadText  ·  ↑ $uploadText")
            .setContentText("Wi-Fi $wifi today  ·  Mobile $mobile today")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Network speed: ↓ $downloadText, ↑ $uploadText\nWi-Fi used today: $wifi\nMobile data used today: $mobile"))
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
        const val ACTION_STOP = "com.faheem.habits.NETWORK_INDICATOR_STOP"
        private const val CHANNEL_ID = "persistent_network_indicator"
        private const val NOTIFICATION_ID = 9020
        private const val SAMPLE_INTERVAL_MS = 3_000L
        private const val USAGE_REFRESH_MS = 60_000L

        fun enabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun currentSpeed(context: Context): String = "Live in notification"

        private fun loadTodayTotals(context: Context): Pair<Long?, Long?> {
            if (!hasUsageAccess(context)) return null to null
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
