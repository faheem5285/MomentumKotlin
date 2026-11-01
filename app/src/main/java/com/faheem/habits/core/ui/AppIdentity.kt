package com.faheem.habits.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class InstalledAppIdentity(
    val label: String,
    val icon: ImageBitmap?
)

fun installedAppIdentity(context: Context, packageName: String): InstalledAppIdentity {
    val packageManager = context.packageManager
    val info = runCatching {
        packageManager.getApplicationInfo(packageName, 0)
    }.getOrNull()
    if (info == null) return InstalledAppIdentity(packageName, null)

    val label = info.loadLabel(packageManager).toString()
        .takeIf { it.isNotBlank() && it != packageName }
        ?: packageName
    val icon = runCatching {
        info.loadIcon(packageManager).toImageBitmap()
    }.getOrNull()
    return InstalledAppIdentity(label, icon)
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, width, height)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
