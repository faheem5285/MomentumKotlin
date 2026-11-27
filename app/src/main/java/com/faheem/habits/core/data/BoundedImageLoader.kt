package com.faheem.habits.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Decodes a SAF URI with byte and pixel budgets, returning null on any failure. */
fun decodeBoundedImage(
    context: Context,
    uri: Uri,
    maxBytes: Long = ImagePreviewPolicy.MAX_SOURCE_BYTES,
    maxDimension: Int = ImagePreviewPolicy.MAX_DECODE_DIMENSION
): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        decodeBoundedStream(input, maxBytes, maxDimension)
    }
} catch (_: Exception) {
    null
}

/** Decodes an already-open stream without trusting its declared length. */
fun decodeBoundedStream(
    input: InputStream,
    maxBytes: Long = ImagePreviewPolicy.MAX_SOURCE_BYTES,
    maxDimension: Int = ImagePreviewPolicy.MAX_DECODE_DIMENSION
): Bitmap? {
    return try {
        val bytes = readBoundedBytes(input, maxBytes)
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImagePreviewPolicy.sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    }
}

private fun readBoundedBytes(input: InputStream, maxBytes: Long): ByteArray {
    val limited = BoundedInputStream(input, maxBytes)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = limited.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
