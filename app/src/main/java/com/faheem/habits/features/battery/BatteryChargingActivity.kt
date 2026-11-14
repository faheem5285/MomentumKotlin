package com.faheem.habits.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat

/**
 * Full-screen preview of the selected charging animation. The window itself is transparent,
 * so the animation floats over whatever screen was open behind it.
 */
class BatteryChargingActivity : ComponentActivity() {

    private var graphic: ChargingAnimationView? = null
    private var percentView: TextView? = null
    private var sourceView: TextView? = null
    private var receiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        val style = BatteryAnimationPreferences.style(this)
        val reading = batteryReading(
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isClickable = true
            setOnClickListener { finish() }
        }
        val animation = ChargingAnimationView(this, reading.percent.toFloat(), style)
        root.addView(
            animation,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        graphic = animation

        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val percentage = TextView(this).apply {
            text = "${reading.percent}%"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setShadowLayer(dp(10).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
            contentDescription = "Battery level"
        }
        percentView = percentage
        status.addView(
            percentage,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val source = TextView(this).apply {
            text = "${reading.source} · $style"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(AndroidColor.argb(225, 255, 255, 255))
            setShadowLayer(dp(8).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
        }
        sourceView = source
        status.addView(
            source,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        if (preview) {
            status.addView(TextView(this).apply {
                text = "ANIMATION PREVIEW"
                textSize = 10.5f
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.argb(160, 255, 255, 255))
                setShadowLayer(dp(6).toFloat(), 0f, 0f, AndroidColor.argb(160, 0, 0, 0))
            })
        }
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(72) }
        )

        root.addView(
            TextView(this).apply {
                text = "×"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(AndroidColor.argb(220, 255, 255, 255))
                setShadowLayer(dp(8).toFloat(), 0f, 0f, AndroidColor.argb(180, 0, 0, 0))
                setOnClickListener { finish() }
            },
            FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                Gravity.TOP or Gravity.END
            ).apply {
                topMargin = dp(32)
                marginEnd = dp(18)
            }
        )

        setContentView(root)
        registerBatteryUpdates()
    }

    private fun registerBatteryUpdates() {
        val updates = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val current = batteryReading(intent)
                percentView?.text = "${current.percent}%"
                graphic?.updatePercent(current.percent.toFloat())
            }
        }
        ContextCompat.registerReceiver(
            this,
            updates,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = updates
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PREVIEW = "preview_animation"
    }
}
