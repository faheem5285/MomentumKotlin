package com.faheem.habits.features.battery
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BatteryChargingMonitorService : Service() {
    private var powerReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        try {
            ensureChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            powerReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    handleAutomaticBatteryPowerEvent(this@BatteryChargingMonitorService, intent?.action, "Persistent background listener")
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            BatteryAnimationDiagnostics.record(this, "Background charging listener active", "The persistent listener service is running and registered for plug and unplug events.")
        } catch (error: Throwable) {
            getSharedPreferences(BatteryAnimationPreferences.NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_BACKGROUND_LISTENER, false).apply()
            BatteryAnimationDiagnostics.error(this, "Background charging listener failed to start", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(BatteryAnimationPreferences.NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_BACKGROUND_LISTENER, false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        powerReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        powerReceiver = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Background charging listener", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while Momentum listens for charger connection in the background."
                }
            )
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
        .setContentTitle("Charging animation listener active")
        .setContentText("Momentum will show your selected animation when power connects.")
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    companion object {
        const val CHANNEL_ID = "background_charging_listener"
        const val NOTIFICATION_ID = 7822
        const val KEY_BACKGROUND_LISTENER = "background_listener_enabled"
        const val ACTION_STOP = "com.faheem.habits.STOP_BATTERY_LISTENER"

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_BACKGROUND_LISTENER, enabled).apply()
            val intent = Intent(context, BatteryChargingMonitorService::class.java)
            if (enabled) ContextCompat.startForegroundService(context, intent)
            else context.stopService(intent)
        }
    }
}
