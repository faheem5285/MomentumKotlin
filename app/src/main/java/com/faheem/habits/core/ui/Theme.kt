package com.faheem.habits.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Positive = Color(0xFF16834A)
val Warning = Color(0xFFB76E00)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4657C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E6FF),
    onPrimaryContainer = Color(0xFF111D65),
    secondary = Color(0xFF087F8C),
    onSecondary = Color.White,
    tertiary = Color(0xFF7652C8),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF171B2A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B2A),
    surfaceVariant = Color(0xFFEAECF5),
    onSurfaceVariant = Color(0xFF555D73),
    outline = Color(0xFFC5CAD9),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C2FF),
    onPrimary = Color(0xFF17246D),
    primaryContainer = Color(0xFF303F9F),
    onPrimaryContainer = Color(0xFFE1E6FF),
    secondary = Color(0xFF80D5DF),
    onSecondary = Color(0xFF00363D),
    tertiary = Color(0xFFD0BCFF),
    background = Color(0xFF10131D),
    onBackground = Color(0xFFE9ECF7),
    surface = Color(0xFF181C28),
    onSurface = Color(0xFFE9ECF7),
    surfaceVariant = Color(0xFF262C3B),
    onSurfaceVariant = Color(0xFFB9C0D3),
    outline = Color(0xFF41495C),
    error = Color(0xFFFFB4AB)
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
