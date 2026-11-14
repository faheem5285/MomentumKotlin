@file:Suppress("FunctionNaming", "MagicNumber")

package com.faheem.habits.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * App badge used by every module that lists device apps.
 *
 * Shows the real launcher icon when Android can provide it and falls back to a
 * neon monogram so no row is left with a blank or generic placeholder.
 */
@Composable
fun AppIcon(
    icon: ImageBitmap?,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = size * 0.28f
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (icon != null) {
        Box(
            modifier
                .size(size)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = icon,
                contentDescription = label,
                modifier = Modifier.size(size).clip(shape),
                contentScale = ContentScale.Fit
            )
        }
        return
    }
    val base = chartColor(label.hashCode())
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.62f))))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.Black.copy(alpha = 0.16f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.24f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.trim().take(1).uppercase().ifBlank { "•" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}
