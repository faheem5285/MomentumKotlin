@file:Suppress("FunctionNaming")

package com.faheem.habits.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.faheem.habits.R

/**
 * The luminous glass hero panel at the top of every module. It carries the
 * module gradient and presents the single most important number on the screen.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    gradient: List<Color> = LocalGradient.current,
    contentPadding: Dp = 22.dp,
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
        Box(Modifier.matchParentSize()) {
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
        }
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
                Modifier.fillMaxWidth().padding(contentPadding),
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

/** Standard supporting line used under module hero headings. */
@Composable
fun HeroCaption(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 13.sp,
        color = Color.White.copy(alpha = 0.84f),
        maxLines = maxLines
    )
}

@Deprecated("Use SectionHeader directly.", ReplaceWith("SectionHeader(text)"))
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
fun ListLoadingState(modifier: Modifier = Modifier) {
    val loadingDescription = stringResource(R.string.state_loading)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp)
                .semantics { contentDescription = loadingDescription }
        )
    }
}

@Composable
fun ListEmptyState(
    icon: ImageVector,
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .padding(horizontal = MomentumTokens.space5, vertical = MomentumTokens.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(MomentumTokens.space3))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        subtitle?.let {
            Spacer(Modifier.height(MomentumTokens.space1))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(MomentumTokens.space4))
        PrimaryButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier.widthIn(max = 240.dp)
        )
    }
}

@Composable
fun ListErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .padding(MomentumTokens.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(MomentumTokens.space3))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(MomentumTokens.space4))
        PrimaryButton(
            text = stringResource(R.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.widthIn(max = 240.dp)
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
    val spec = rememberAnimationSpecFor<Float>(LocalReduceEffects.current, 500)
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spec, label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
            .progressSemantics("Progress", fraction)
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
fun ProgressRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    caption: String = "today",
    progressColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    labelColor: Color = Color.White,
    captionColor: Color = Color.White.copy(alpha = 0.75f)
) {
    val spec = rememberAnimationSpecFor<Float>(LocalReduceEffects.current)
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), spec, label = "ring")
    Box(
        modifier
            .size(96.dp)
            .progressSemantics(label, progress),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            // Neon bloom behind the progress arc.
            drawArc(
                color = progressColor.copy(alpha = 0.18f),
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw * 2.4f, cap = StrokeCap.Round)
            )
            drawArc(
                color = trackColor,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = tl, size = arcSize,
                style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = labelColor)
            Text(caption, fontSize = 10.sp, color = captionColor)
        }
    }
}

@Deprecated("Use GlassSheet directly.", ReplaceWith("GlassSheet(title, onDismiss, content)"))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    GlassSheet(title, onDismiss) { content() }
}

@Deprecated("Use PrimaryButton directly.", ReplaceWith("PrimaryButton(text, onClick)"))
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

val BrandGradient = listOf(
    Color(0xFF234AA8),
    Color(0xFF2463E9),
    Color(0xFF168EBA),
    Color(0xFF58CBE5)
)

// Each module provides its own gradient; HeroCard reads it automatically.
val LocalGradient = compositionLocalOf { BrandGradient }

/**
 * Module identity: label, icon and signature gradient.
 *
 * Every gradient runs deep -> vivid -> bright so it works in three ways:
 *  - the deep stop anchors hero panels and screen backgrounds,
 *  - the vivid middle stop is the readable accent for text, charts and glows,
 *  - the bright stop catches the light on glass edges and card corners.
 */
enum class AppModule(val label: String, val icon: ImageVector, val gradient: List<Color>) {
    Habits("Habits", Icons.Rounded.CheckCircle, listOf(Color(0xFF4C1D95), Color(0xFF8B5CF6), Color(0xFFD8B4FE))),
    ToDos("To-dos", Icons.Rounded.CalendarMonth, listOf(Color(0xFF155E75), Color(0xFF06B6D4), Color(0xFF67E8F9))),
    Money("Money", Icons.Rounded.AccountBalanceWallet, listOf(Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF6EE7B7))),
    Loans("Loans", Icons.Rounded.SwapHoriz, listOf(Color(0xFF881337), Color(0xFFF43F5E), Color(0xFFFDA4AF))),
    Contacts("Contacts", Icons.Rounded.People, listOf(Color(0xFF164E63), Color(0xFF0891B2), Color(0xFF67E8F9))),
    Files("Files", Icons.Rounded.Folder, listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF93C5FD))),
    LifeRules("Life rules", Icons.AutoMirrored.Rounded.MenuBook, listOf(Color(0xFF78350F), Color(0xFFF59E0B), Color(0xFFFDE68A))),
    Screen("Screen time", Icons.Rounded.Timer, listOf(Color(0xFF581C87), Color(0xFFA855F7), Color(0xFFE879F9))),
    Vault("Vault", Icons.Rounded.Lock, listOf(Color(0xFF134E4A), Color(0xFF14B8A6), Color(0xFF5EEAD4))),
    Battery("Battery", Icons.Rounded.Bolt, listOf(Color(0xFF365314), Color(0xFF84CC16), Color(0xFFBEF264))),
    Network("Network", Icons.Rounded.SignalCellularAlt, listOf(Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFA5B4FC))),
    Language("Language", Icons.AutoMirrored.Rounded.MenuBook, listOf(Color(0xFF7C2D12), Color(0xFFF97316), Color(0xFFFDBA74))),
    Supabase("Cloud & Sync", Icons.Rounded.Cloud, listOf(Color(0xFF14532D), Color(0xFF22C55E), Color(0xFF86EFAC))),
    Profile("Profile", Icons.Rounded.Person, listOf(Color(0xFF312E81), Color(0xFF6366F1), Color(0xFFA5B4FC))),
    Settings("Settings", Icons.Rounded.SettingsSuggest, listOf(Color(0xFF1E293B), Color(0xFF64748B), Color(0xFFCBD5E1)));

    /** Readable, saturated accent for text, charts and small icons. */
    val accent: Color get() = gradient.getOrElse(1) { gradient.first() }

    /** Deepest tone, used for screen washes and hero backgrounds. */
    val deep: Color get() = gradient.first()
}
