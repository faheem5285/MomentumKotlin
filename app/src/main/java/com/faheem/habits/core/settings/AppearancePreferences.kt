package com.faheem.habits.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which colour scheme Momentum should use. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

/**
 * App-wide appearance preferences.
 *
 * Backed by SharedPreferences so the very first frame can already use the saved
 * values, before any DataStore work completes. The in-memory flows keep Compose
 * screens in sync when a setting changes.
 */
object AppearancePreferences {
    private const val PREFS = "momentum_appearance"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_REDUCE_MOTION = "reduce_motion"

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.System.name) ?: ThemeMode.System.name)
        }.getOrDefault(ThemeMode.System)
        _reduceMotion.value = prefs.getBoolean(KEY_REDUCE_MOTION, false)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setReduceMotion(context: Context, enabled: Boolean) {
        _reduceMotion.value = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply()
    }
}
