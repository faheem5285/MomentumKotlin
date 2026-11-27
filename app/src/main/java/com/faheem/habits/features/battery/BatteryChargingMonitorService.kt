package com.faheem.habits.features.battery
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps a low-priority status notification while the charging listener is enabled.
 * Power events are handled only by the manifest-owned BatteryChargingReceiver, so plug/unplug
 * actions are never processed twice when Zylo is in the foreground or this service is running.
 */
class BatteryChargingMonitorService : Service() {
    override fun onCreate() {
        super.onCreate()
        try {
            ZyloNotifications.ensureChannels(this)
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            BatteryAnimationDiagnostics.record(
                this,
                "Background charging listener active",
                "The status notification is active. Android delivers power events to Zylo's manifest receiver."
            )
        } catch (error: Throwable) {
            BatteryChargingMonitorService.persistEnabled(this, false)
            BatteryAnimationDiagnostics.error(this, "Background charging listener failed to start", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !enabled(this)) {
            BatteryChargingMonitorService.persistEnabled(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification = ZyloNotifications.base(this, ZyloNotifications.CHANNEL_BATTERY_LISTENER)
        .setContentTitle("Charging animation listener active")
        .setContentText("Zylo will show your selected animation when power connects.")
        .setCategory(android.app.Notification.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        const val NOTIFICATION_ID = 7822
        const val KEY_BACKGROUND_LISTENER = "background_listener_enabled"
        const val KEY_RESUME_PENDING = "resume_pending"
        const val ACTION_STOP = "com.faheem.habits.STOP_BATTERY_LISTENER"

        fun enabled(context: Context) = context.getSharedPreferences(
            BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE
        ).getBoolean(KEY_BACKGROUND_LISTENER, false)

        fun pendingResume(context: Context) = context.getSharedPreferences(
            BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE
        ).getBoolean(KEY_RESUME_PENDING, false)

        fun markResumePending(context: Context) {
            context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RESUME_PENDING, true).apply()
        }

        private fun persistEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_BACKGROUND_LISTENER, enabled)
                .putBoolean(KEY_RESUME_PENDING, false)
                .apply()
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_BACKGROUND_LISTENER, enabled)
                .putBoolean(KEY_RESUME_PENDING, false)
                .apply()
            if (enabled) {
                startIfEnabled(context)
            } else {
                context.stopService(Intent(context, BatteryChargingMonitorService::class.java))
            }
        }

        /** Starts only when persisted state asks for it; returns false if Android blocks the start. */
        fun startIfEnabled(context: Context): Boolean {
            if (!enabled(context)) return false
            return try {
                ContextCompat.startForegroundService(
                    context, Intent(context, BatteryChargingMonitorService::class.java)
                )
                context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_RESUME_PENDING, false).apply()
                true
            } catch (_: Exception) {
                markResumePending(context)
                false
            }
        }
    }
}
