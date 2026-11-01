@file:Suppress("FunctionNaming")

package com.faheem.habits.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.faheem.habits.core.settings.ThemeMode

/** Semantic colors used across the app. */
val Positive = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)
val AccentViolet = Color(0xFF8B5CF6)
val AccentTeal = Color(0xFF22D3EE)
val AccentAmber = Color(0xFFFBBF24)

/** The three neon lights that bleed through the frosted glass. */
val NeonViolet = Color(0xFF8B5CF6)
val NeonCyan = Color(0xFF22D3EE)
val NeonMagenta = Color(0xFFF472B6)

/**
 * Momentum neon glass palette.
 *
 * Light: a pale lilac canvas with electric violet, cyan and magenta light
 * diffusing behind milky frosted panes.
 * Dark: an ink-black canvas where the same neon hues glow through smoked glass.
 *
 * Surfaces intentionally carry alpha so the aurora canvas shows through them.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFBE185D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCE7F3),
    onTertiaryContainer = Color(0xFF500724),
    background = Color(0xFFF2F1FA),
    onBackground = Color(0xFF14101F),
    surface = Color(0xC2FFFFFF),
    onSurface = Color(0xFF14101F),
    surfaceVariant = Color(0x2E7C3AED),
    onSurfaceVariant = Color(0xFF5A5470),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFB2A6D0),
    outlineVariant = Color(0x337C3AED),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0x99150B26),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F6FE),
    surfaceContainer = Color(0xFFF1EEFB),
    surfaceContainerHigh = Color(0xFFF6F4FD),
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB49BFF),
    onPrimary = Color(0xFF24105C),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF06333C),
    secondaryContainer = Color(0xFF155E75),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFF9A8D4),
    onTertiary = Color(0xFF4A0A2B),
    tertiaryContainer = Color(0xFF9D174D),
    onTertiaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF05060E),
    onBackground = Color(0xFFF2F0FF),
    surface = Color(0x1FFFFFFF),
    onSurface = Color(0xFFF2F0FF),
    surfaceVariant = Color(0x1F8B5CF6),
    onSurfaceVariant = Color(0xFFB9B3D2),
    surfaceTint = Color.Transparent,
    outline = Color(0x66A78BFA),
    outlineVariant = Color(0x33A78BFA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xCC000000),
    surfaceContainerLowest = Color(0xFF060713),
    surfaceContainerLow = Color(0xFF0B0C18),
    surfaceContainer = Color(0xFF101120),
    surfaceContainerHigh = Color(0xFF15162A),
    surfaceContainerHighest = Color(0xFF1A1B33)
)

/**
 * Glass-specific tones that cannot be expressed through the Material scheme:
 * gradient fills, hairline highlights, shadow tints and opaque panels for sheets.
 */
@Immutable
data class GlassPalette(
    val dark: Boolean,
    val cardTop: Color,
    val cardBottom: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val sheen: Color,
    val chip: Color,
    val panel: Color,
    val panelBorder: Color,
    val shadow: Color,
    val glowAlpha: Float,
    val haloPrimary: Color = NeonViolet,
    val haloSecondary: Color = NeonCyan,
    val haloAccent: Color = NeonMagenta
)

private val LightGlass = GlassPalette(
    dark = false,
    cardTop = Color(0xE8FFFFFF),
    cardBottom = Color(0xB8FFFFFF),
    borderTop = Color(0xFFFFFFFF),
    borderBottom = Color(0x33FFFFFF),
    sheen = Color(0x8AFFFFFF),
    chip = Color(0x4DFFFFFF),
    panel = Color(0xFFF6F4FC),
    panelBorder = Color(0x1A4C1D95),
    shadow = Color(0x2E4C1D95),
    glowAlpha = 0.34f,
    haloPrimary = Color(0xFF7C3AED),
    haloSecondary = Color(0xFF06B6D4),
    haloAccent = Color(0xFFDB2777)
)

private val DarkGlass = GlassPalette(
    dark = true,
    cardTop = Color(0x1FFFFFFF),
    cardBottom = Color(0x0AFFFFFF),
    borderTop = Color(0x66FFFFFF),
    borderBottom = Color(0x14FFFFFF),
    sheen = Color(0x1FFFFFFF),
    chip = Color(0x1AFFFFFF),
    panel = Color(0xFF0C0D18),
    panelBorder = Color(0x1FA78BFA),
    shadow = Color(0x66000000),
    glowAlpha = 0.55f,
    haloPrimary = Color(0xFF8B5CF6),
    haloSecondary = Color(0xFF22D3EE),
    haloAccent = Color(0xFFF472B6)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.9).sp
    ),
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )
)

val LocalReduceEffects = compositionLocalOf { false }
val LocalDynamicAccent = compositionLocalOf { false }
val LocalGlassPalette = compositionLocalOf { LightGlass }

@Composable
fun MomentumTheme(
    reduceEffects: Boolean = false,
    dynamicAccent: Boolean = false,
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = when {
        dynamicAccent && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            dark -> androidx.compose.material3.dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
        dynamicAccent && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
            androidx.compose.material3.dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(
                LocalReduceEffects provides reduceEffects,
                LocalDynamicAccent provides dynamicAccent,
                LocalGlassPalette provides if (dark) DarkGlass else LightGlass,
                content = content
            )
        }
    )
}
