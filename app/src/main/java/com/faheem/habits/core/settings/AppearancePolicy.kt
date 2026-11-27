package com.faheem.habits.core.settings

/**
 * Pure appearance migration/backup contract.
 *
 * Appearance values live in the app DataStore so they are included in backups.
 * SharedPreferences remains a first-frame cache only; it is never the source of
 * truth after synchronization.
 */
data class AppearanceValues(
    val themeMode: ThemeMode = ThemeMode.System,
    val reduceMotion: Boolean = false,
    val dynamicAccent: Boolean = false,
    val glassIntensity: GlassIntensity = GlassIntensity.Balanced
)

object AppearanceBackupPolicy {
    const val PREFIX = "appearance_"
    const val THEME_MODE = "${PREFIX}theme_mode"
    const val REDUCE_MOTION = "${PREFIX}reduce_motion"
    const val DYNAMIC_ACCENT = "${PREFIX}dynamic_accent"
    const val GLASS_INTENSITY = "${PREFIX}glass_intensity"

    val KEYS = listOf(THEME_MODE, REDUCE_MOTION, DYNAMIC_ACCENT, GLASS_INTENSITY)

    /** Legacy SharedPreferences keys, kept only for first-frame migration. */
    private val LEGACY_THEME_MODE = "theme_mode"
    private val LEGACY_REDUCE_MOTION = "reduce_motion"
    private val LEGACY_DYNAMIC_ACCENT = "dynamic_accent"
    private val LEGACY_GLASS_INTENSITY = "glass_intensity"

    fun defaults(): AppearanceValues = AppearanceValues()

    fun fromRaw(raw: Map<String, Any?>): AppearanceValues {
        fun theme(key: String): ThemeMode = runCatching {
            ThemeMode.valueOf(raw[key]?.toString() ?: ThemeMode.System.name)
        }.getOrDefault(ThemeMode.System)

        fun flag(key: String): Boolean = when (val value = raw[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> false
        }

        fun glass(key: String): GlassIntensity = runCatching {
            GlassIntensity.valueOf(raw[key]?.toString() ?: GlassIntensity.Balanced.name)
        }.getOrDefault(GlassIntensity.Balanced)

        return AppearanceValues(
            themeMode = theme(THEME_MODE),
            reduceMotion = flag(REDUCE_MOTION),
            dynamicAccent = flag(DYNAMIC_ACCENT),
            glassIntensity = glass(GLASS_INTENSITY)
        )
    }

    /** Reads either a DataStore-style map or legacy SharedPreferences-style map. */
    fun fromLegacy(raw: Map<String, Any?>): AppearanceValues = fromRaw(
        mapOf(
            THEME_MODE to (raw[THEME_MODE] ?: raw[LEGACY_THEME_MODE]),
            REDUCE_MOTION to (raw[REDUCE_MOTION] ?: raw[LEGACY_REDUCE_MOTION]),
            DYNAMIC_ACCENT to (raw[DYNAMIC_ACCENT] ?: raw[LEGACY_DYNAMIC_ACCENT]),
            GLASS_INTENSITY to (raw[GLASS_INTENSITY] ?: raw[LEGACY_GLASS_INTENSITY])
        )
    )

    fun toRaw(values: AppearanceValues): Map<String, Any> = mapOf(
        THEME_MODE to values.themeMode.name,
        REDUCE_MOTION to values.reduceMotion,
        DYNAMIC_ACCENT to values.dynamicAccent,
        GLASS_INTENSITY to values.glassIntensity.name
    )

    /** All four keys are written, so a partial legacy import still has stable values. */
    fun migrationFromLegacy(raw: Map<String, Any?>): Map<String, Any> = toRaw(fromLegacy(raw))

    fun isAppearanceKey(key: String): Boolean = key in KEYS || key.startsWith(PREFIX)
}
