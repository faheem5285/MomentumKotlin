@file:Suppress("FunctionNaming", "MayBeConst", "TooManyFunctions", "LongParameterList")

package com.faheem.habits.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassLevel { Low, Medium, High }

/** Shared spacing, radii and motion constants. */
object MomentumTokens {
    val radiusSmall = 14.dp
    val radiusMedium = 20.dp
    val radiusLarge = 28.dp
    val radiusXLarge = 32.dp

    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 24.dp
    val space6 = 32.dp

    val screenPadding = 18.dp
    val cardPadding = 18.dp

    val glassBorder = 0.22f
    val glassHighlight = 0.55f
    val blurRadius = 18.dp

    val elevationLow = 2.dp
    val elevationMedium = 6.dp
    val elevationHigh = 14.dp

    const val motionShort = 140
    const val motionMedium = 260
}

/* ------------------------------------------------------------------------- */
/* Glass primitives                                                          */
/* ------------------------------------------------------------------------- */

private fun Color.scaledAlpha(factor: Float): Color =
    copy(alpha = (alpha * factor).coerceIn(0f, 1f))

@Composable
private fun glassTones(level: GlassLevel): Pair<Color, Color> {
    val glass = LocalGlassPalette.current
    val factor = when (level) {
        GlassLevel.Low -> 0.68f
        GlassLevel.Medium -> 1f
        GlassLevel.High -> 1.34f
    }
    return glass.cardTop.scaledAlpha(factor) to glass.cardBottom.scaledAlpha(factor)
}

@Composable
private fun glassElevation(level: GlassLevel): Dp {
    if (LocalReduceEffects.current) return 0.dp
    return when (level) {
        GlassLevel.Low -> MomentumTokens.elevationLow
        GlassLevel.Medium -> MomentumTokens.elevationMedium
        GlassLevel.High -> MomentumTokens.elevationHigh
    }
}

private fun Modifier.glassShadow(
    elevation: Dp,
    shape: RoundedCornerShape,
    glass: GlassPalette,
    glow: Color = glass.haloPrimary
): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = glow.copy(alpha = if (glass.dark) 0.55f else 0.28f),
        spotColor = glow.copy(alpha = if (glass.dark) 0.42f else 0.20f)
    )

/** The card glow follows the active module so each screen is lit by its own neon. */
@Composable
private fun neonGlow(): Color =
    LocalGradient.current.maxByOrNull { it.luminance() } ?: LocalGlassPalette.current.haloPrimary

/** A diagonal light sweep that reads as a reflection on the glass surface. */
fun glassSheenBrush(glass: GlassPalette): Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = if (glass.dark) 0.10f else 0.20f),
    0.55f to Color.White.copy(alpha = 0f),
    1f to Color.White.copy(alpha = 0f)
)

/**
 * A neon-lit glass edge: a bright white lip that fades into the accent halo
 * and dies out along the bottom of the pane.
 */
fun neonEdgeBrush(glass: GlassPalette, glow: Color = glass.haloPrimary): Brush =
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (glass.dark) 0.78f else 0.95f),
        0.12f to glow.copy(alpha = if (glass.dark) 0.58f else 0.40f),
        0.55f to glow.copy(alpha = if (glass.dark) 0.16f else 0.10f),
        1f to glow.copy(alpha = 0f)
    )

/** Colour that bleeds down from the top edge of a frosted pane. */
fun neonWashBrush(glass: GlassPalette, glow: Color = glass.haloPrimary): Brush =
    Brush.verticalGradient(
        0f to glow.copy(alpha = if (glass.dark) 0.20f else 0.10f),
        0.42f to Color.Transparent
    )

/**
 * Full-screen glass canvas: a soft tinted wash with slow aurora blooms that
 * show through every translucent card placed on top of it.
 */
@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassPalette.current
    val dark = glass.dark
    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = if (dark) 0.34f else 0.18f),
                        0.42f to Color.Transparent,
                        1f to glass.haloSecondary.copy(alpha = if (dark) 0.18f else 0.12f)
                    )
                )
        )
        if (!LocalReduceEffects.current) {
            AuroraBloom(
                Modifier.align(Alignment.TopEnd).offset(x = 120.dp, y = (-188).dp),
                size = 400.dp,
                color = tint,
                alpha = glass.glowAlpha * 0.78f
            )
            AuroraBloom(
                Modifier.align(Alignment.CenterStart).offset(x = (-190).dp, y = (-60).dp),
                size = 360.dp,
                color = glass.haloSecondary,
                alpha = glass.glowAlpha * 0.58f
            )
            AuroraBloom(
                Modifier.align(Alignment.BottomEnd).offset(x = 148.dp, y = 150.dp),
                size = 340.dp,
                color = glass.haloAccent,
                alpha = glass.glowAlpha * 0.62f
            )
        }
        content()
    }
}

@Composable
private fun AuroraBloom(
    modifier: Modifier,
    size: Dp,
    color: Color,
    alpha: Float
) {
    Box(
        modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.42f),
                        color.copy(alpha = 0f)
                    )
                ),
                CircleShape
            )
    )
}

/**
 * The primary frosted panel. A translucent gradient fill, a hairline white
 * highlight along the top edge and a soft coloured shadow create the glass look.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Medium,
    shape: RoundedCornerShape = RoundedCornerShape(MomentumTokens.radiusMedium),
    contentPadding: PaddingValues = PaddingValues(MomentumTokens.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassPalette.current
    val (top, bottom) = glassTones(level)
    val elevation = glassElevation(level)
    val glow = neonGlow()
    Column(
        modifier
            .fillMaxWidth()
            .glassShadow(elevation, shape, glass, glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, glow))
            .border(1.dp, neonEdgeBrush(glass, glow), shape)
            .padding(contentPadding),
        content = content
    )
}

/** A tappable frosted card with an optional glowing accent rail. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    shape: RoundedCornerShape = RoundedCornerShape(MomentumTokens.radiusMedium),
    contentPadding: PaddingValues = PaddingValues(MomentumTokens.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassPalette.current
    val (top, bottom) = glassTones(GlassLevel.Medium)
    val elevation = glassElevation(GlassLevel.Medium)
    val glow = accent ?: neonGlow()
    Box(
        modifier
            .fillMaxWidth()
            .glassShadow(elevation, shape, glass, glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, glow))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(1.dp, neonEdgeBrush(glass, glow), shape)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) {
                Box(
                    Modifier
                        .padding(vertical = 13.dp, horizontal = 3.dp)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .shadow(
                            if (LocalReduceEffects.current) 0.dp else 10.dp,
                            CircleShape,
                            clip = false,
                            ambientColor = accent.copy(alpha = 0.7f),
                            spotColor = accent.copy(alpha = 0.5f)
                        )
                        .background(
                            Brush.verticalGradient(
                                listOf(accent, accent.copy(alpha = 0.25f))
                            )
                        )
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glass.panel,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = MomentumTokens.radiusXLarge, topEnd = MomentumTokens.radiusXLarge),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = MomentumTokens.space5, vertical = MomentumTokens.space4),
            verticalArrangement = Arrangement.spacedBy(MomentumTokens.space3)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(glass.panelBorder, glass.panelBorder.copy(alpha = 0f))
                        )
                    )
            )
            content()
            Spacer(Modifier.height(MomentumTokens.space2))
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val glass = LocalGlassPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MomentumTokens.space3, vertical = MomentumTokens.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(end = MomentumTokens.space1)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(glass.cardTop)
                    .border(1.dp, neonEdgeBrush(glass), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(20.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun GlassBottomBar(content: @Composable RowScope.() -> Unit) {
    val glass = LocalGlassPalette.current
    val (top, bottom) = glassTones(GlassLevel.High)
    val glow = neonGlow()
    val shape = RoundedCornerShape(MomentumTokens.radiusLarge)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = MomentumTokens.space4, vertical = MomentumTokens.space2)
            .glassShadow(glassElevation(GlassLevel.High), shape, glass, glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, glow))
            .border(1.dp, neonEdgeBrush(glass, glow), shape)
            .padding(horizontal = MomentumTokens.space2, vertical = MomentumTokens.space2),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Glass module header used above every feature screen.
 * Shows a glowing gradient icon, an eyebrow label, the module name and actions.
 */
@Composable
fun ModuleHeader(
    title: String,
    eyebrow: String,
    icon: ImageVector,
    gradient: List<Color>,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val glass = LocalGlassPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MomentumTokens.space3, vertical = MomentumTokens.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(end = MomentumTokens.space2)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(glass.cardTop)
                    .border(1.dp, neonEdgeBrush(glass), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(20.dp))
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .shadow(
                    14.dp,
                    RoundedCornerShape(16.dp),
                    clip = false,
                    ambientColor = gradient.first().copy(alpha = if (glass.dark) 0.80f else 0.45f),
                    spotColor = gradient.last().copy(alpha = if (glass.dark) 0.55f else 0.35f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(gradient))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent, Color.Black.copy(alpha = 0.12f))
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.70f), gradient.last().copy(alpha = 0.25f))
                    ),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        }
        Spacer(Modifier.width(MomentumTokens.space3))
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

/** Pill-shaped segmented tabs, floating on the glass canvas. */
data class MomentumTab(val label: String, val icon: ImageVector)

@Composable
fun MomentumTabs(
    tabs: List<MomentumTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassPalette.current
    val (top, bottom) = glassTones(GlassLevel.Medium)
    val glow = neonGlow()
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MomentumTokens.space4, vertical = MomentumTokens.space2)
            .glassShadow(MomentumTokens.elevationMedium, shape, glass, glow)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(glassSheenBrush(glass))
            .border(1.dp, neonEdgeBrush(glass, glow), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            val accent = LocalGradient.current.firstOrNull() ?: colors.primary
            val background = if (active) {
                Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = 0.72f))
                )
            } else {
                Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            }
            Row(
                Modifier
                    .weight(1f)
                    .then(
                        if (active) {
                            Modifier.shadow(
                                14.dp,
                                RoundedCornerShape(18.dp),
                                clip = false,
                                ambientColor = accent.copy(alpha = 0.75f),
                                spotColor = (LocalGradient.current.lastOrNull() ?: colors.tertiary).copy(alpha = 0.50f)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(background)
                    .background(
                        if (active) {
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .then(
                        if (active) {
                            Modifier.border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.10f))
                                ),
                                RoundedCornerShape(18.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    Modifier.size(18.dp),
                    tint = if (active) colors.onPrimary else colors.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) colors.onPrimary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Section title with an optional trailing action. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().padding(top = MomentumTokens.space2, bottom = MomentumTokens.space1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

/** Compact frosted metric tile used inside dashboards and hero sections. */
@Composable
fun MetricTile(
    title: String,
    value: String,
    detail: String? = null,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassPalette.current
    val (top, bottom) = glassTones(GlassLevel.Medium)
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    Column(
        modifier
            .glassShadow(glassElevation(GlassLevel.Low), shape, glass, accent)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .background(glassSheenBrush(glass))
            .background(neonWashBrush(glass, accent))
            .border(1.dp, neonEdgeBrush(glass, accent), shape)
            .padding(MomentumTokens.space4),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .shadow(
                            8.dp,
                            RoundedCornerShape(9.dp),
                            clip = false,
                            ambientColor = accent.copy(alpha = 0.55f),
                            spotColor = accent.copy(alpha = 0.35f)
                        )
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.10f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(16.dp), tint = accent)
                }
                Spacer(Modifier.width(MomentumTokens.space2))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatTile(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) = MetricTile(title, value, detail, accent = accent, modifier = modifier)

/** Soft tinted glass banner for paused reminders, sync state, or setup hints. */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = tint.copy(alpha = 0.45f),
                spotColor = tint.copy(alpha = 0.30f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.07f))
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.45f), tint.copy(alpha = 0.40f))
                ),
                shape
            )
            .padding(horizontal = MomentumTokens.space4, vertical = MomentumTokens.space3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(MomentumTokens.space3))
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .shadow(
                16.dp,
                shape,
                clip = false,
                ambientColor = colors.primary.copy(alpha = if (glass.dark) 0.70f else 0.45f),
                spotColor = colors.tertiary.copy(alpha = if (glass.dark) 0.45f else 0.30f)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.primary,
                        colors.primary.copy(alpha = 0.80f)
                    )
                ),
                shape
            )
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.26f), Color.Transparent, Color.Black.copy(alpha = 0.10f))
                ),
                shape
            ),
        enabled = enabled,
        shape = shape,
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.10f)))
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onPrimary
        ),
        elevation = null
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp
            )
            icon != null -> {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(MomentumTokens.space2))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
            else -> Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val glass = LocalGlassPalette.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val glow = neonGlow()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                10.dp,
                shape,
                clip = false,
                ambientColor = glow.copy(alpha = 0.35f),
                spotColor = glow.copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.chip, glass.chip.copy(alpha = 0.35f))))
            .background(glassSheenBrush(glass))
            .border(1.dp, neonEdgeBrush(glass, glow), shape),
        shape = shape,
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface
        )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(MomentumTokens.space2))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Shared text field. Frosted fill, soft accent border and a consistent radius
 * so forms feel like part of the glass canvas rather than stock Material.
 */
@Composable
fun MomentumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    val glass = LocalGlassPalette.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MomentumTokens.radiusSmall + 2.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        readOnly = readOnly,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        isError = isError,
        supportingText = supportingText,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = glass.chip,
            unfocusedContainerColor = glass.chip.copy(alpha = 0.55f),
            disabledContainerColor = glass.chip.copy(alpha = 0.35f),
            focusedBorderColor = colors.primary.copy(alpha = 0.85f),
            unfocusedBorderColor = glass.panelBorder,
            disabledBorderColor = glass.panelBorder.copy(alpha = 0.4f),
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
            focusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.7f),
            cursorColor = colors.primary,
            focusedLeadingIconColor = colors.primary,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.primary,
            unfocusedTrailingIconColor = colors.onSurfaceVariant
        )
    )
}

/** Rounded selectable chip with a solid accent when active. */
@Composable
fun MomentumChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val glass = LocalGlassPalette.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MomentumTokens.radiusSmall)
    val background by animateColorAsState(
        if (selected) colors.primary else glass.chip,
        label = "chip-bg"
    )
    val contentColor by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSurfaceVariant,
        label = "chip-content"
    )
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (selected) {
                    Modifier.shadow(
                        10.dp,
                        shape,
                        clip = false,
                        ambientColor = colors.primary.copy(alpha = 0.55f),
                        spotColor = colors.primary.copy(alpha = 0.35f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else glass.panelBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = contentColor)
            Spacer(Modifier.width(MomentumTokens.space1))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** Frosted outlined button for secondary actions that should not fill the row. */
@Composable
fun MomentumOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val glass = LocalGlassPalette.current
    val colors = MaterialTheme.colorScheme
    val glow = neonGlow()
    val shape = RoundedCornerShape(MomentumTokens.radiusSmall + 2.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.chip, glass.chip.copy(alpha = 0.35f))))
            .border(1.dp, neonEdgeBrush(glass, glow), shape),
        enabled = enabled,
        shape = shape,
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(MomentumTokens.space2))
        }
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Shared extended FAB so every module floats the same action in the same way. */
@Composable
fun MomentumFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Add
) {
    val reduce = LocalReduceEffects.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, null, Modifier.size(20.dp)) },
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        shape = RoundedCornerShape(MomentumTokens.radiusMedium),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (reduce) 0.dp else MomentumTokens.elevationHigh,
            pressedElevation = if (reduce) 0.dp else MomentumTokens.elevationMedium
        ),
        modifier = modifier
    )
}

/** Small glass status pill. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier
            .shadow(8.dp, CircleShape, clip = false, ambientColor = color.copy(alpha = 0.55f), spotColor = color.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.08f))))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Rounded glass check button used for habit and task completion. */
@Composable
fun CompletionToggle(
    checked: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val background by animateColorAsState(if (checked) color else Color.Transparent, label = "toggle-bg")
    Box(
        modifier
            .size(size)
            .then(
                if (checked) {
                    Modifier.shadow(size * 0.32f, CircleShape, clip = false, ambientColor = color.copy(alpha = 0.70f), spotColor = color.copy(alpha = 0.55f))
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(background)
            .border(2.dp, if (checked) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.52f), tint = Color.White)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = MomentumTokens.space5),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(MomentumTokens.space3))
        Text(message, color = MaterialTheme.colorScheme.onSurface)
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.size(MomentumTokens.space1))
                Text("Try again")
            }
        }
    }
}

@Composable
fun SkeletonLoader(modifier: Modifier = Modifier) {
    val glass = LocalGlassPalette.current
    val shape = RoundedCornerShape(MomentumTokens.radiusMedium)
    Box(
        modifier
            .height(72.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(glass.cardTop, glass.cardBottom)))
            .background(glassSheenBrush(glass))
            .border(1.dp, neonEdgeBrush(glass), shape)
    )
}

/** Shared glass dialog shell so every modal matches the app surface. */
@Composable
fun MomentumDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    val glass = LocalGlassPalette.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        containerColor = glass.panel,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(MomentumTokens.radiusLarge),
        tonalElevation = 0.dp
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    MomentumDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun UndoSnackbar(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier)
}
