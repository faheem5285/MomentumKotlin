package com.faheem.habits.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePolicyTest {
    @Test
    fun defaultsAreStable() {
        val defaults = AppearanceBackupPolicy.defaults()
        assertEquals(ThemeMode.System, defaults.themeMode)
        assertFalse(defaults.reduceMotion)
        assertFalse(defaults.dynamicAccent)
        assertEquals(GlassIntensity.Balanced, defaults.glassIntensity)
    }

    @Test
    fun legacyPreferencesMigrateToAllFourDataStoreKeys() {
        val migrated = AppearanceBackupPolicy.migrationFromLegacy(
            mapOf(
                "theme_mode" to "Dark",
                "reduce_motion" to true,
                "dynamic_accent" to false,
                "glass_intensity" to "Rich"
            )
        )
        assertEquals(
            mapOf(
                AppearanceBackupPolicy.THEME_MODE to "Dark",
                AppearanceBackupPolicy.REDUCE_MOTION to true,
                AppearanceBackupPolicy.DYNAMIC_ACCENT to false,
                AppearanceBackupPolicy.GLASS_INTENSITY to "Rich"
            ),
            migrated
        )
        assertEquals(AppearanceBackupPolicy.KEYS.toSet(), migrated.keys)
    }

    @Test
    fun invalidOrMissingValuesFallBackToDefaults() {
        val values = AppearanceBackupPolicy.fromRaw(
            mapOf(
                AppearanceBackupPolicy.THEME_MODE to "Neon",
                AppearanceBackupPolicy.REDUCE_MOTION to "true",
                AppearanceBackupPolicy.DYNAMIC_ACCENT to 1,
                AppearanceBackupPolicy.GLASS_INTENSITY to "Unknown"
            )
        )
        assertEquals(ThemeMode.System, values.themeMode)
        assertTrue(values.reduceMotion)
        assertTrue(values.dynamicAccent)
        assertEquals(GlassIntensity.Balanced, values.glassIntensity)
    }

    @Test
    fun backupRoundTripKeepsCompleteAppearance() {
        val original = AppearanceValues(
            themeMode = ThemeMode.Light,
            reduceMotion = true,
            dynamicAccent = true,
            glassIntensity = GlassIntensity.Subtle
        )
        val stored = AppearanceBackupPolicy.toRaw(original)
        assertTrue(stored.keys.all { AppearanceBackupPolicy.isAppearanceKey(it) })
        assertEquals(original, AppearanceBackupPolicy.fromRaw(stored))
    }
}
