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

enum class GlassIntensity(val label: String, val factor: Float) {
    Subtle("Subtle", 0.94f), Balanced("Balanced", 1f), Rich("Rich", 1.06f)
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
    private const val KEY_DYNAMIC_ACCENT = "dynamic_accent"
    private const val KEY_GLASS_INTENSITY = "glass_intensity"

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _dynamicAccent = MutableStateFlow(false)
    val dynamicAccent: StateFlow<Boolean> = _dynamicAccent.asStateFlow()

    private val _glassIntensity = MutableStateFlow(GlassIntensity.Balanced)
    val glassIntensity: StateFlow<GlassIntensity> = _glassIntensity.asStateFlow()

    fun load(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _themeMode.value = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.System.name) ?: ThemeMode.System.name)
        }.getOrDefault(ThemeMode.System)
        _reduceMotion.value = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        _dynamicAccent.value = prefs.getBoolean(KEY_DYNAMIC_ACCENT, false)
        _glassIntensity.value = runCatching {
            GlassIntensity.valueOf(prefs.getString(KEY_GLASS_INTENSITY, GlassIntensity.Balanced.name) ?: GlassIntensity.Balanced.name)
        }.getOrDefault(GlassIntensity.Balanced)
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

    fun setDynamicAccent(context: Context, enabled: Boolean) {
        _dynamicAccent.value = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_ACCENT, enabled).apply()
    }

    fun setGlassIntensity(context: Context, intensity: GlassIntensity) {
        _glassIntensity.value = intensity
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_GLASS_INTENSITY, intensity.name).apply()
    }
}
