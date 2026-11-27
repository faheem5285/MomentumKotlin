@file:Suppress("FunctionNaming")

package com.faheem.habits.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.faheem.habits.core.settings.ThemeMode

@Composable
private fun DesignSystemGallery() {
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeroMetricCard("2h 14m", "Screen time today", "18 min less than yesterday")
        GlassCard(contentPadding = PaddingValues(16.dp)) {
            MiniBarChart(listOf(18f, 22f, 14f, 26f, 20f, 12f, 16f), listOf("M", "T", "W", "T", "F", "S", "S"), 3, {})
        }
        SettingsGroup("Account") {
            SettingsRow("Cloud backup", "Sync your data when connected", Icons.Rounded.Lock, checked = true, onCheckedChange = {})
            SettingsRow("Profile", "Edit your name and email", Icons.Rounded.Lock, onClick = {})
        }
        GlassCard(contentPadding = PaddingValues(16.dp)) {
            PasswordField("", {}, label = "Password")
            StrengthMeter("Momentum2026!")
        }
        PermissionCard("Usage access", "See your daily screen time by app.", "Open settings", {})
    }
}

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun DesignSystemLightPreview() {
    MomentumTheme(themeMode = ThemeMode.Light) { DesignSystemGallery() }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DesignSystemDarkPreview() {
    MomentumTheme(themeMode = ThemeMode.Dark) { DesignSystemGallery() }
}

@Preview(name = "Large text 1.3x", showBackground = true, fontScale = 1.3f)
@Composable
private fun DesignSystemLargeTextPreview() {
    MomentumTheme(themeMode = ThemeMode.Light) { DesignSystemGallery() }
}

@Preview(name = "Compact 360dp", widthDp = 360, showBackground = true)
@Composable
private fun DesignSystemCompactPreview() {
    MomentumTheme(themeMode = ThemeMode.Light) { DesignSystemGallery() }
}

@Preview(name = "Large text 2x", widthDp = 360, fontScale = 2f, showBackground = true)
@Composable
private fun DesignSystemLargeText2xPreview() {
    MomentumTheme(themeMode = ThemeMode.Light, reduceEffects = true) { DesignSystemGallery() }
}

@Preview(name = "Dark reduced motion", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DesignSystemDarkReducedMotionPreview() {
    MomentumTheme(themeMode = ThemeMode.Dark, reduceEffects = true) { DesignSystemGallery() }
}

@Preview(name = "Dynamic accent", showBackground = true)
@Composable
private fun DesignSystemDynamicAccentPreview() {
    MomentumTheme(themeMode = ThemeMode.Light, dynamicAccent = true) { DesignSystemGallery() }
}

@Preview(name = "Glass intensity low", widthDp = 360, showBackground = true)
@Composable
private fun DesignSystemLowGlassPreview() {
    MomentumTheme(themeMode = ThemeMode.Light, glassIntensity = 0.88f) { DesignSystemGallery() }
}

@Preview(name = "Glass intensity high", widthDp = 360, showBackground = true)
@Composable
private fun DesignSystemHighGlassPreview() {
    MomentumTheme(themeMode = ThemeMode.Light, glassIntensity = 1.12f) { DesignSystemGallery() }
}
