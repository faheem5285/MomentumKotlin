package com.faheem.habits.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

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
 * SharedPreferences is only a first-frame cache. The DataStore copy is the
 * source of truth and is included in backups; [synchronize] reconciles the two
 * after startup or a backup import.
 */
object AppearancePreferences {
    private const val PREFS = "momentum_appearance"

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _dynamicAccent = MutableStateFlow(false)
    val dynamicAccent: StateFlow<Boolean> = _dynamicAccent.asStateFlow()

    private val _glassIntensity = MutableStateFlow(GlassIntensity.Balanced)
    val glassIntensity: StateFlow<GlassIntensity> = _glassIntensity.asStateFlow()

    fun load(context: Context) {
        apply(context, AppearanceBackupPolicy.fromLegacy(legacyValues(context)))
    }

    /** Applies a complete value set to both the in-memory flows and first-frame cache. */
    fun apply(context: Context, values: AppearanceValues) {
        _themeMode.value = values.themeMode
        _reduceMotion.value = values.reduceMotion
        _dynamicAccent.value = values.dynamicAccent
        _glassIntensity.value = values.glassIntensity
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", values.themeMode.name)
            .putBoolean("reduce_motion", values.reduceMotion)
            .putBoolean("dynamic_accent", values.dynamicAccent)
            .putString("glass_intensity", values.glassIntensity.name)
            .apply()
    }

    /**
     * DataStore wins when present; otherwise legacy values are migrated once and
     * written back so backup/export sees all four keys.
     */
    suspend fun synchronize(context: Context, repository: MomentumSettingsRepository) {
        val stored = repository.appearanceValues.first()
        val values = if (stored != null) {
            stored
        } else {
            val migrated = AppearanceBackupPolicy.fromLegacy(legacyValues(context))
            repository.setAppearance(migrated)
            migrated
        }
        apply(context, values)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        apply(context, current().copy(themeMode = mode))
    }

    fun setReduceMotion(context: Context, enabled: Boolean) {
        apply(context, current().copy(reduceMotion = enabled))
    }

    fun setDynamicAccent(context: Context, enabled: Boolean) {
        apply(context, current().copy(dynamicAccent = enabled))
    }

    fun setGlassIntensity(context: Context, intensity: GlassIntensity) {
        apply(context, current().copy(glassIntensity = intensity))
    }

    private fun current(): AppearanceValues = AppearanceValues(
        themeMode = _themeMode.value,
        reduceMotion = _reduceMotion.value,
        dynamicAccent = _dynamicAccent.value,
        glassIntensity = _glassIntensity.value
    )

    private fun legacyValues(context: Context): Map<String, Any?> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return mapOf(
            "theme_mode" to prefs.getString("theme_mode", null),
            "reduce_motion" to prefs.getBoolean("reduce_motion", false),
            "dynamic_accent" to prefs.getBoolean("dynamic_accent", false),
            "glass_intensity" to prefs.getString("glass_intensity", null)
        )
    }
}
