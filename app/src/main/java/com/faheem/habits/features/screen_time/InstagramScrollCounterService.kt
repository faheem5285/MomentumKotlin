package com.faheem.habits.features.screen_time

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import java.time.LocalDate

/**
 * Best-effort counter for vertical scroll events while Instagram is foreground.
 * It does not retrieve the view hierarchy or inspect screen text/content.
 */
class InstagramScrollCounterService : AccessibilityService() {
    private var lastCountedEventMs = 0L
    private var counterView: TextView? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayedHide = Runnable { hideCounter() }

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as? WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            when {
                packageName == INSTAGRAM_PACKAGE -> keepCounterVisible()
                packageName == null || packageName == getPackageName() -> Unit
                else -> scheduleHide()
            }
        }
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED || packageName != INSTAGRAM_PACKAGE) return
        keepCounterVisible()

        val now = event.eventTime
        if (lastCountedEventMs > 0 && now >= lastCountedEventMs && now - lastCountedEventMs < EVENT_DEBOUNCE_MS) return
        lastCountedEventMs = now

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = countKey(LocalDate.now())
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        updateCounter(count)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedHide)
        hideCounter()
        windowManager = null
        super.onDestroy()
    }

    private fun overlayEnabled(): Boolean =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_FLOATING_COUNTER, false) &&
            (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this))

    private fun showCounter() {
        if (!overlayEnabled() || counterView != null) return
        val wm = windowManager ?: return
        val count = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(countKey(LocalDate.now()), 0)
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = counterLabel(count)
            textSize = 14.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.015f
            setTextColor(android.graphics.Color.WHITE)
            setShadowLayer(3 * density, 0f, 1 * density, android.graphics.Color.argb(120, 0, 0, 0))
            setPadding((18 * density).toInt(), (10 * density).toInt(), (18 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                android.graphics.Color.argb(142, 20, 48, 88),
                android.graphics.Color.argb(122, 18, 118, 139)
            )).apply {
                cornerRadius = 24 * density
                setStroke((1.25f * density).toInt(), android.graphics.Color.argb(170, 226, 248, 255))
            }
            elevation = 8 * density
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (54 * density).toInt()
        }
        try {
            wm.addView(view, params)
            counterView = view
        } catch (error: RuntimeException) {
            Log.w("InstagramScrollCounter", "Unable to display the floating counter.", error)
        }
    }

    private fun updateCounter(count: Int) {
        if (counterView == null) showCounter()
        counterView?.text = counterLabel(count)
    }

    private fun keepCounterVisible() {
        mainHandler.removeCallbacks(delayedHide)
        if (counterView == null) showCounter()
    }

    private fun scheduleHide() {
        mainHandler.removeCallbacks(delayedHide)
        mainHandler.postDelayed(delayedHide, OVERLAY_HIDE_DELAY_MS)
    }

    private fun hideCounter() {
        val view = counterView ?: return
        try {
            windowManager?.removeView(view)
        } catch (error: RuntimeException) {
            Log.w("InstagramScrollCounter", "Unable to remove the floating counter.", error)
        } finally {
            counterView = null
        }
    }

    private fun counterLabel(count: Int) = "Reels today: $count"

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val PREFS_NAME = "screen_time"
        const val PREF_LIMIT = "reels_daily_limit"
        const val PREF_FLOATING_COUNTER = "reels_floating_counter"
        const val EVENT_DEBOUNCE_MS = 900L
        const val OVERLAY_HIDE_DELAY_MS = 12_000L
        fun countKey(day: LocalDate) = "instagram_scroll_count_${day}"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val target = ComponentName(context, InstagramScrollCounterService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(target, ignoreCase = true) }
        }
    }
}
