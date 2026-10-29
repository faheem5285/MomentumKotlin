package com.faheem.habits

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Positive = Color(0xFF16A34A)
val Warning = Color(0xFFF59E0B)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E3FF),
    onPrimaryContainer = Color(0xFF1B1B66),
    secondary = Color(0xFF0EA5A4),
    onSecondary = Color.White,
    tertiary = Color(0xFF8B5CF6),
    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF16161F),
    surface = Color.White,
    onSurface = Color(0xFF16161F),
    surfaceVariant = Color(0xFFECECF6),
    onSurfaceVariant = Color(0xFF5E5E70),
    outline = Color(0xFFC9C9D8),
    error = Color(0xFFE5484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9D9DFF),
    onPrimary = Color(0xFF14143D),
    primaryContainer = Color(0xFF2B2B66),
    onPrimaryContainer = Color(0xFFE4E3FF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color.Black,
    tertiary = Color(0xFFB69CFF),
    background = Color(0xFF0E0E16),
    onBackground = Color(0xFFEDEDF5),
    surface = Color(0xFF171722),
    onSurface = Color(0xFFEDEDF5),
    surfaceVariant = Color(0xFF242435),
    onSurfaceVariant = Color(0xFFA8A8BC),
    outline = Color(0xFF3A3A50),
    error = Color(0xFFFF6B6F)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MomentumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
