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

/** The cool blue lights used throughout the Home screen glass surfaces. */
val NeonViolet = Color(0xFF596DEB)
val NeonCyan = Color(0xFF36B9DD)
val NeonMagenta = Color(0xFF9A7AE8)

/**
 * Momentum neon glass palette.
 *
 * Light: a pale blue-lavender canvas with blue, cyan and violet light diffusing
 * behind milky frosted panes, matching the Home screen.
 * Dark: a deep navy canvas where the same hues glow through smoked glass.
 *
 * Surfaces intentionally carry alpha so the aurora canvas shows through them.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF2463E9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF102A62),
    secondary = Color(0xFF168EBA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F3FA),
    onSecondaryContainer = Color(0xFF103847),
    tertiary = Color(0xFF7654D6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAE3FF),
    onTertiaryContainer = Color(0xFF2A1B57),
    background = Color(0xFFF0F4FC),
    onBackground = Color(0xFF151B2D),
    surface = Color(0xC2FFFFFF),
    onSurface = Color(0xFF151B2D),
    surfaceVariant = Color(0x2E477CE0),
    onSurfaceVariant = Color(0xFF566178),
    surfaceTint = Color.Transparent,
    outline = Color(0xFFAFBCD3),
    outlineVariant = Color(0x334778D8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0x99150B26),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FE),
    surfaceContainer = Color(0xFFEDF2FC),
    surfaceContainerHigh = Color(0xFFF2F6FD),
    surfaceContainerHighest = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BB9FF),
    onPrimary = Color(0xFF102653),
    primaryContainer = Color(0xFF1E438B),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF78D8EF),
    onSecondary = Color(0xFF073541),
    secondaryContainer = Color(0xFF15566C),
    onSecondaryContainer = Color(0xFFD7F3FA),
    tertiary = Color(0xFFC1ABFF),
    onTertiary = Color(0xFF2B1A57),
    tertiaryContainer = Color(0xFF554094),
    onTertiaryContainer = Color(0xFFEAE3FF),
    background = Color(0xFF0C1426),
    onBackground = Color(0xFFF0F4FC),
    surface = Color(0x1FFFFFFF),
    onSurface = Color(0xFFF0F4FC),
    surfaceVariant = Color(0x1F7396E8),
    onSurfaceVariant = Color(0xFFB7C4DE),
    surfaceTint = Color.Transparent,
    outline = Color(0x667DA2F1),
    outlineVariant = Color(0x337DA2F1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xCC000000),
    surfaceContainerLowest = Color(0xFF0A1020),
    surfaceContainerLow = Color(0xFF101A2E),
    surfaceContainer = Color(0xFF142039),
    surfaceContainerHigh = Color(0xFF1A2945),
    surfaceContainerHighest = Color(0xFF213252)
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
    panel = Color(0xFFF5F8FE),
    panelBorder = Color(0x1A315AA8),
    shadow = Color(0x2E315AA8),
    glowAlpha = 0.34f,
    haloPrimary = Color(0xFF3974EE),
    haloSecondary = Color(0xFF36B9DD),
    haloAccent = Color(0xFF9A7AE8)
)

private val DarkGlass = GlassPalette(
    dark = true,
    cardTop = Color(0x1FFFFFFF),
    cardBottom = Color(0x0AFFFFFF),
    borderTop = Color(0x66FFFFFF),
    borderBottom = Color(0x14FFFFFF),
    sheen = Color(0x1FFFFFFF),
    chip = Color(0x1AFFFFFF),
    panel = Color(0xFF101A2E),
    panelBorder = Color(0x1F7DA2F1),
    shadow = Color(0x66000000),
    glowAlpha = 0.55f,
    haloPrimary = Color(0xFF739AFF),
    haloSecondary = Color(0xFF58CBE5),
    haloAccent = Color(0xFFAA91F2)
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
