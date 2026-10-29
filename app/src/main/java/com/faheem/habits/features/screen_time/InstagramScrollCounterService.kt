package com.faheem.habits.features.screen_time
import com.faheem.habits.core.data.*
import com.faheem.habits.core.notifications.*
import com.faheem.habits.core.ui.*
import com.faheem.habits.R


import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import java.time.LocalDate

/**
 * Best-effort counter for vertical scroll events while Instagram is foreground.
 * It does not retrieve the view hierarchy or inspect screen text/content.
 */
class InstagramScrollCounterService : AccessibilityService() {
    private var lastCountedEventMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return

        val now = event.eventTime
        if (lastCountedEventMs > 0 && now >= lastCountedEventMs && now - lastCountedEventMs < EVENT_DEBOUNCE_MS) return
        lastCountedEventMs = now

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = countKey(LocalDate.now())
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val PREFS_NAME = "screen_time"
        const val PREF_LIMIT = "reels_daily_limit"
        const val EVENT_DEBOUNCE_MS = 900L
        fun countKey(day: LocalDate) = "instagram_scroll_count_${day}"

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val target = ComponentName(context, InstagramScrollCounterService::class.java).flattenToString()
            return enabled.split(':').any { it.equals(target, ignoreCase = true) }
        }
    }
}
