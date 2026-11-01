@file:Suppress("FunctionNaming")

package com.faheem.habits.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The luminous glass hero panel at the top of every module. It carries the
 * module gradient and presents the single most important number on the screen.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    gradient: List<Color> = LocalGradient.current,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = gradient.ifEmpty { BrandGradient }
    val shape = RoundedCornerShape(MomentumTokens.radiusLarge)
    val elevation = if (LocalReduceEffects.current) 0.dp else 24.dp
    Box(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation,
                shape,
                clip = false,
                ambientColor = colors.first().copy(alpha = 0.65f),
                spotColor = colors.last().copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(Brush.linearGradient(colors))
    ) {
        // Light passes through the top of the pane and falls away at the base.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.26f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.10f)
                        )
                    )
                )
        )
        // Decorative glass rings.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 66.dp, y = (-104).dp)
                .size(228.dp)
                .border(1.dp, colors.last().copy(alpha = 0.42f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 98.dp, y = (-72).dp)
                .size(150.dp)
                .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
        )
        // Soft light bloom plus a darker corner for depth.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 58.dp, y = (-84).dp)
                .size(210.dp)
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0f))),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-64).dp, y = 74.dp)
                .size(190.dp)
                .background(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0f))),
                    CircleShape
                )
        )
        // Neon light drifting across the pane.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.12f),
                        0.45f to Color.Transparent,
                        1f to colors.last().copy(alpha = 0.20f)
                    )
                )
        )
        // Hairline glass edge.
        Box(
            Modifier
                .matchParentSize()
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.78f),
                            colors.last().copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    ),
                    shape
                )
        )
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                content = content
            )
        }
    }
}

@Composable
fun HeroRefreshButton(onClick: () -> Unit, description: String = "Refresh") {
    FilledTonalIconButton(
        onClick = onClick,
        // Keep icon-only actions comfortably tappable, including on compact cards.
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White
        )
    ) {
        Icon(Icons.Rounded.Refresh, description, Modifier.size(19.dp))
    }
}

@Composable
fun SectionLabel(text: String) {
    SectionHeader(text)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .shadow(
                    14.dp,
                    RoundedCornerShape(20.dp),
                    clip = false,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(MomentumTokens.space4))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 44.dp) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(size * 0.34f)
    Box(
        Modifier
            .size(size)
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = color.copy(alpha = if (glass.dark) 0.60f else 0.38f),
                spotColor = color.copy(alpha = if (glass.dark) 0.45f else 0.26f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(color.copy(alpha = 0.40f), color.copy(alpha = 0.12f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = if (glass.dark) 0.42f else 0.55f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size / 2), tint = color)
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    fraction.coerceIn(0f, 1f),
                    0f..1f
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .shadow(
                    8.dp,
                    RoundedCornerShape(height / 2),
                    clip = false,
                    ambientColor = color.copy(alpha = 0.75f),
                    spotColor = color.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        listOf(color, color.copy(alpha = 0.78f), Color.White.copy(alpha = 0.55f))
                    )
                )
        )
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(
        modifier
            .size(96.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(
                    progress.coerceIn(0f, 1f),
                    0f..1f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            // Neon bloom behind the progress arc.
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw * 2.4f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White,
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text("today", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    GlassSheet(title, onDismiss) { content() }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit) {
    PrimaryButton(text, onClick)
}

data class Cat(val name: String, val icon: ImageVector, val color: Color)

val categories = listOf(
    Cat("Food", Icons.Rounded.Restaurant, Color(0xFFF97316)),
    Cat("Transport", Icons.Rounded.DirectionsCar, Color(0xFF3B82F6)),
    Cat("Bills", Icons.Rounded.Receipt, Color(0xFFEF4444)),
    Cat("Shopping", Icons.Rounded.ShoppingCart, Color(0xFFA855F7)),
    Cat("Other", Icons.Rounded.MoreHoriz, Color(0xFF64748B))
)

fun catOf(name: String): Cat = categories.firstOrNull { it.name == name } ?: categories.last()

val BrandGradient = listOf(Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF0E7490))

// Each module provides its own gradient; HeroCard reads it automatically.
val LocalGradient = compositionLocalOf { BrandGradient }

/**
 * Module identity: label, icon and signature gradient.
 * Gradients are deep, electric neon tones so white hero content stays readable
 * while the card still throws coloured light onto the glass around it.
 */
enum class AppModule(val label: String, val icon: ImageVector, val gradient: List<Color>) {
    Habits("Habits", Icons.Rounded.CheckCircle, listOf(Color(0xFF6D28D9), Color(0xFFA855F7))),
    ToDos("To-dos", Icons.Rounded.CalendarMonth, listOf(Color(0xFF0E7490), Color(0xFF06B6D4))),
    Money("Money", Icons.Rounded.AccountBalanceWallet, listOf(Color(0xFF047857), Color(0xFF10B981))),
    Loans("Loans", Icons.Rounded.SwapHoriz, listOf(Color(0xFFBE123C), Color(0xFFFB7185))),
    Files("Files", Icons.Rounded.Folder, listOf(Color(0xFF1D4ED8), Color(0xFF6366F1))),
    Screen("Screen time", Icons.Rounded.Timer, listOf(Color(0xFF7E22CE), Color(0xFFD946EF))),
    Vault("Vault", Icons.Rounded.Lock, listOf(Color(0xFF0F766E), Color(0xFF14B8A6))),
    Battery("Battery", Icons.Rounded.BatteryChargingFull, listOf(Color(0xFF4D7C0F), Color(0xFF84CC16))),
    Network("Network", Icons.Rounded.Wifi, listOf(Color(0xFF3730A3), Color(0xFF6366F1))),
    Settings("Settings", Icons.Rounded.Settings, listOf(Color(0xFF334155), Color(0xFF475569)))
}
