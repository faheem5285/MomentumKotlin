package com.faheem.habits.features.battery
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.content.BroadcastReceiver
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object BatteryAnimationPreferences {
    const val NAME = "momentum_battery"
    const val KEY_STYLE = "charge_animation_style"
    const val KEY_AUTO_SHOW = "charge_animation_auto_show"
    const val NOTIFICATION_CHANNEL = "charging_animation"
    const val NOTIFICATION_ID = 7821
    fun style(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(KEY_STYLE, "Pulse") ?: "Pulse"
    fun autoShow(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_SHOW, true)
}

internal data class BatteryDiagnostic(val status: String, val detail: String, val timestamp: Long)

internal object BatteryAnimationDiagnostics {
    private const val PREFS = "momentum_battery_diagnostics"
    private const val TAG = "MomentumCharging"

    fun record(context: Context, status: String, detail: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("status", status)
            .putString("detail", detail.take(8_000))
            .putLong("timestamp", System.currentTimeMillis())
            .apply()
        Log.i(TAG, "$status: $detail")
    }

    fun latest(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
        BatteryDiagnostic(getString("status", "No charging event recorded") ?: "No charging event recorded", getString("detail", "Connect the charger to capture diagnostics.") ?: "", getLong("timestamp", 0L))
    }

    fun error(context: Context, status: String, error: Throwable) {
        val detail = buildString {
            append(error.javaClass.name)
            error.message?.let { append(": ").append(it) }
            error.cause?.let { append("\nCaused by: ").append(it.javaClass.name).append(": ").append(it.message.orEmpty()) }
            append("\n\n").append(Log.getStackTraceString(error))
        }
        Log.e(TAG, status, error)
        record(context, status, detail)
    }
}

fun showBatteryChargingOverlayPreview(context: Context) {
    ChargeAnimationOverlay.show(context)
}

class BatteryChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        handleAutomaticBatteryPowerEvent(context, intent?.action, "Android manifest receiver")
    }
}

internal fun handleAutomaticBatteryPowerEvent(context: Context, action: String?, source: String) {
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                BatteryAnimationDiagnostics.record(context, "Power connected event received", "$source received Android's ACTION_POWER_CONNECTED event. Auto-show is ${BatteryAnimationPreferences.autoShow(context)}.")
                val sessionReading = batteryReading(context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                BatterySessionHistory.start(context, sessionReading.percent, sessionReading.source)
                if (!BatteryAnimationPreferences.autoShow(context)) {
                    BatteryAnimationDiagnostics.record(context, "Animation skipped", "Automatic charging animation is turned off in Battery settings.")
                    return
                }
                val preferences = context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val previousEvent = preferences.getLong("last_auto_show_event", 0L)
                if (now - previousEvent < 2_000L) return
                preferences.edit().putLong("last_auto_show_event", now).apply()
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (Settings.canDrawOverlays(context) && power.isInteractive && !keyguard.isKeyguardLocked) {
                    ChargeAnimationOverlay.show(context)
                } else {
                    val reason = when {
                        !Settings.canDrawOverlays(context) -> "Display over other apps permission is missing."
                        !power.isInteractive -> "The screen is off; Android does not allow the pop-up in this state."
                        keyguard.isKeyguardLocked -> "The device is locked; Android does not allow the pop-up over the lock screen."
                        else -> "Android did not allow the overlay in the current device state."
                    }
                    BatteryAnimationDiagnostics.record(context, "Using notification fallback", reason)
                    postChargingNotification(context)
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                val sessionReading = batteryReading(context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                BatterySessionHistory.finish(context, sessionReading.percent)
                context.getSharedPreferences(BatteryAnimationPreferences.NAME, Context.MODE_PRIVATE)
                    .edit().putLong("last_auto_show_event", 0L).apply()
                ChargeAnimationOverlay.dismiss(context)
                NotificationManagerCompat.from(context).cancel(BatteryAnimationPreferences.NOTIFICATION_ID)
                BatteryAnimationDiagnostics.record(context, "Power disconnected", "The charger was unplugged; any charging overlay and fallback notification were dismissed.")
            }
            else -> BatteryAnimationDiagnostics.record(context, "Unexpected battery receiver event", "Received action: ${action ?: "null"}")
        }
}

private object ChargeAnimationOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var window: View? = null
    private var manager: WindowManager? = null
    private var percentView: TextView? = null
    private var chargingGraphic: ChargingAnimationView? = null
    private var percentEstimator: BatteryPercentEstimator? = null
    private var percentTicker: Runnable? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private val dismissTask = Runnable { dismissInternal() }

    fun show(context: Context, onComplete: () -> Unit = {}) {
        handler.post {
            val appContext = context.applicationContext
            try {
                dismissInternal()
                if (!Settings.canDrawOverlays(appContext)) {
                    BatteryAnimationDiagnostics.record(appContext, "Overlay permission unavailable", "SYSTEM_ALERT_WINDOW permission was not granted when the overlay was about to be added.")
                    postChargingNotification(appContext)
                    return@post
                }
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val reading = currentBattery(appContext)
            val estimator = BatteryPercentEstimator(reading.first, true, SystemClock.elapsedRealtime())
            val initialPercent = estimator.estimate(SystemClock.elapsedRealtime())
            val style = BatteryAnimationPreferences.style(appContext)
            val root = FrameLayout(appContext).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isClickable = true
                setOnClickListener { dismissInternal() }
            }
            val graphic = ChargingAnimationView(appContext, initialPercent, style)
            root.addView(graphic, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            val status = LinearLayout(appContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            val percentage = TextView(appContext).apply {
                text = formatEstimatedPercent(initialPercent)
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.WHITE)
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                setShadowLayer(dp(appContext, 10).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
                contentDescription = "Estimated battery level. Android reports whole percentages."
            }
            status.addView(percentage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            status.addView(TextView(appContext).apply {
                text = "${reading.second} · $style"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.argb(225, 255, 255, 255))
                setShadowLayer(dp(appContext, 8).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            status.addView(TextView(appContext).apply {
                text = "Tap anywhere to dismiss"
                textSize = 10.5f
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
                setTextColor(AndroidColor.argb(160, 255, 255, 255))
                setShadowLayer(dp(appContext, 6).toFloat(), 0f, 0f, AndroidColor.argb(160, 0, 0, 0))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            root.addView(
                status,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = dp(appContext, 72) }
            )
            root.addView(
                TextView(appContext).apply {
                    text = "×"
                    textSize = 26f
                    gravity = Gravity.CENTER
                    setTextColor(AndroidColor.argb(220, 255, 255, 255))
                    setShadowLayer(dp(appContext, 8).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
                    setOnClickListener { dismissInternal() }
                },
                FrameLayout.LayoutParams(
                    dp(appContext, 48),
                    dp(appContext, 48),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(appContext, 32)
                    marginEnd = dp(appContext, 18)
                }
            )

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
                wm.addView(root, layoutParams)
                window = root
                manager = wm
                percentView = percentage
                chargingGraphic = graphic
                percentEstimator = estimator
                val batteryUpdates = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val reading = batteryReading(intent)
                        estimator.update(reading.percent, reading.charging, SystemClock.elapsedRealtime())
                        renderEstimatedPercent(estimator.estimate(SystemClock.elapsedRealtime()))
                    }
                }
                ContextCompat.registerReceiver(
                    appContext,
                    batteryUpdates,
                    android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                batteryReceiver = batteryUpdates
                val ticker = object : Runnable {
                    override fun run() {
                        val activeEstimator = percentEstimator ?: return
                        renderEstimatedPercent(activeEstimator.estimate(SystemClock.elapsedRealtime()))
                        handler.postDelayed(this, 1_000)
                    }
                }
                percentTicker = ticker
                handler.post(ticker)
                handler.removeCallbacks(dismissTask)
                handler.postDelayed(dismissTask, 10_000)
                BatteryAnimationDiagnostics.record(appContext, "Charging overlay shown", "WindowManager added the ${BatteryAnimationPreferences.style(appContext)} animation. Screen interactive=true, overlay permission=true, battery=${reading.first}%, source=${reading.second}.")
            } catch (error: Throwable) {
                BatteryAnimationDiagnostics.error(appContext, "Charging overlay failed", error)
                runCatching { postChargingNotification(appContext) }.onFailure {
                    BatteryAnimationDiagnostics.error(appContext, "Fallback notification also failed", it)
                }
            } finally {
                onComplete()
            }
        }
    }

    fun dismiss(context: Context) { handler.post { dismissInternal() } }

    private fun dismissInternal() {
        handler.removeCallbacks(dismissTask)
        percentTicker?.let(handler::removeCallbacks)
        percentTicker = null
        batteryReceiver?.let { receiver -> runCatching { window?.context?.unregisterReceiver(receiver) } }
        batteryReceiver = null
        percentView = null
        chargingGraphic = null
        percentEstimator = null
        window?.let { view -> runCatching { manager?.removeView(view) } }
        window = null
        manager = null
    }

    private fun renderEstimatedPercent(percent: Float) {
        percentView?.text = formatEstimatedPercent(percent)
        chargingGraphic?.updatePercent(percent)
    }

    private fun formatEstimatedPercent(percent: Float) = String.format(Locale.ROOT, "%.2f%%", percent.coerceIn(0f, 100f))
}


private fun postChargingNotification(context: Context) {
    if (!MomentumNotificationSettings.notificationsAllowed(context)) {
        BatteryAnimationDiagnostics.record(context, "Fallback notification not allowed", "Android notification permission is denied or notifications are disabled for Zylo.")
        return
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(NotificationChannel(
            BatteryAnimationPreferences.NOTIFICATION_CHANNEL,
            "Charging animation",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "A shortcut to the selected charging animation when the phone is charging." })
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(BatteryAnimationPreferences.NOTIFICATION_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
        BatteryAnimationDiagnostics.record(context, "Fallback notification channel is blocked", "The Charging animation notification channel is disabled in Android settings.")
        return
    }
    val open = PendingIntent.getActivity(
        context, BatteryAnimationPreferences.NOTIFICATION_ID,
        Intent(context, BatteryChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, BatteryAnimationPreferences.NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_zylo)
        .setContentTitle("Charging animation")
        .setContentText("Tap to view your ${BatteryAnimationPreferences.style(context)} charging animation.")
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        BatteryAnimationDiagnostics.record(context, "Fallback notification permission unavailable", "Android notification permission is not granted.")
        return
    }
    runCatching {
        NotificationManagerCompat.from(context).notify(BatteryAnimationPreferences.NOTIFICATION_ID, notification)
        BatteryAnimationDiagnostics.record(context, "Fallback notification posted", "Android accepted the charging-animation notification. Tap it to open the full-screen animation.")
    }.onFailure { BatteryAnimationDiagnostics.error(context, "Fallback notification failed", it) }
}

private fun currentBattery(context: Context): Pair<Int, String> {
    val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
    val percent = (level * 100f / scale).toInt().coerceIn(0, 100)
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val source = when {
        plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless charging"
        plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB charging"
        plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Plugged in"
        else -> "Charging"
    }
    return percent to source
}

private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
