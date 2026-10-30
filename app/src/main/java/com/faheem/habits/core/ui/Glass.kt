@file:Suppress("FunctionNaming", "MayBeConst")

package com.faheem.habits.core.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassLevel { Low, Medium, High }

object MomentumTokens {
    val radiusSmall = 14.dp
    val radiusMedium = 20.dp
    val radiusLarge = 28.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 24.dp
    val glassBorder = 0.22f
    val glassHighlight = 0.55f
    val blurRadius = 18.dp
    const val motionShort = 140
    const val motionMedium = 260
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Medium,
    shape: RoundedCornerShape = RoundedCornerShape(MomentumTokens.radiusMedium),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val alpha = when (level) {
        GlassLevel.Low -> 0.58f
        GlassLevel.Medium -> 0.76f
        GlassLevel.High -> 0.92f
    }
    Surface(
        modifier = modifier
            .clip(shape)
            .glassBlur(),
        shape = shape,
        color = colors.surface.copy(alpha = alpha),
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = MomentumTokens.glassBorder)),
        shadowElevation = if (LocalReduceEffects.current) 0.dp else 2.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(MomentumTokens.space4),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MomentumTokens.space5, vertical = MomentumTokens.space3),
            verticalArrangement = Arrangement.spacedBy(MomentumTokens.space3)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = MomentumTokens.space2, vertical = MomentumTokens.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun GlassBottomBar(content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(topStart = MomentumTokens.radiusLarge, topEnd = MomentumTokens.radiusLarge)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MomentumTokens.space2, vertical = MomentumTokens.space2),
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}

@Composable
fun StatTile(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    GlassCard(modifier, GlassLevel.Low) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(MomentumTokens.space2))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Box(
        modifier
            .height(72.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(MomentumTokens.radiusMedium))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
    AlertDialog(
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

@Composable
private fun Modifier.glassBlur(): Modifier {
    if (LocalReduceEffects.current || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val radius = with(LocalDensity.current) { MomentumTokens.blurRadius.toPx() }
    return graphicsLayer {
        renderEffect = android.graphics.RenderEffect
            .createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
