@file:Suppress("FunctionNaming")

package com.faheem.habits.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

/** Hero value card with a concise trend that does not rely on color alone. */
@Composable
fun HeroMetricCard(
    value: String,
    label: String,
    trend: String? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        trend?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Seven point Canvas chart. The full bar remains a 48dp action target. */
@Composable
fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueDescription: (Int, Float) -> String = { index, value -> "${labels.getOrElse(index) { "Day ${index + 1}" }}: ${value.toInt()}" }
) {
    require(values.size <= 7) { "MiniBarChart supports at most seven values." }
    val maxValue = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val barColor = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            Column(
                Modifier.weight(1f).height(92.dp).clickable { onSelect(index) }
                    .semantics { contentDescription = valueDescription(index, value) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    val stroke = size.width.coerceAtMost(14.dp.toPx())
                    val x = size.width / 2f
                    val top = size.height * (1f - (value / maxValue).coerceIn(0f, 1f))
                    drawLine(
                        color = barColor.copy(alpha = if (index == selectedIndex) 1f else .42f),
                        start = Offset(x, size.height), end = Offset(x, top), strokeWidth = stroke, cap = StrokeCap.Round
                    )
                    if (index == selectedIndex) drawCircle(barColor, stroke / 2f, Offset(x, top))
                }
                Text(labels.getOrElse(index) { "" }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** A named group keeps related preferences visually and semantically together. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MomentumTokens.space2)) {
        SectionHeader(title)
        GlassCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    supportingText: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            checked != null && onCheckedChange != null -> Switch(checked, onCheckedChange, modifier = Modifier.padding(start = 8.dp))
            value != null -> Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onClick != null -> Icon(Icons.Rounded.ChevronRight, "Open $title", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PremiumTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    GlassTopBar(title = title, onBack = onBack, actions = actions)
}

@Composable
fun PermissionCard(
    title: String,
    benefit: String,
    grantLabel: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(benefit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(MomentumTokens.space2))
        PrimaryButton(grantLabel, onGrant)
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Password",
    enabled: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    MomentumTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(48.dp)) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide password" else "Show password")
            }
        }
    )
}

fun passwordStrength(password: String): Int = listOf(
    password.length >= 8,
    password.any(Char::isLowerCase) && password.any(Char::isUpperCase),
    password.any(Char::isDigit),
    password.any { !it.isLetterOrDigit() }
).count { it }

@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    val score = passwordStrength(password)
    val label = when (score) { 0, 1 -> "Weak"; 2 -> "Fair"; 3 -> "Good"; else -> "Strong" }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Password strength", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                Box(Modifier.weight(1f).height(5.dp).background(
                    if (index < score) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(4.dp)
                ))
            }
        }
    }
}
