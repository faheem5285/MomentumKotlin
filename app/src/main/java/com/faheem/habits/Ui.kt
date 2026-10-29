package com.faheem.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(LocalGradient.current))
            .padding(20.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.15f)),
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
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 10.dp.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
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
        Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
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


val BrandGradient = listOf(Color(0xFF5B5BD6), Color(0xFF8B5CF6))

// Each module provides its own gradient; HeroCard reads it automatically.
val LocalGradient = compositionLocalOf { BrandGradient }

enum class AppModule(val label: String, val icon: ImageVector, val gradient: List<Color>) {
    Habits("Habits", Icons.Rounded.CheckCircle, listOf(Color(0xFF5B5BD6), Color(0xFF8B5CF6))),
    Money("Money", Icons.Rounded.AccountBalanceWallet, listOf(Color(0xFF059669), Color(0xFF14B8A6))),
    Loans("Loans", Icons.Rounded.SwapHoriz, listOf(Color(0xFFEA580C), Color(0xFFF43F5E))),
    Files("Files", Icons.Rounded.Folder, listOf(Color(0xFF2563EB), Color(0xFF06B6D4))),
    Screen("Screen time", Icons.Rounded.Timer, listOf(Color(0xFF7C3AED), Color(0xFFD946EF)))
}
