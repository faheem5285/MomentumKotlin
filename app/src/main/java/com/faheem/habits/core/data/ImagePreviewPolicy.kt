package com.faheem.habits.core.data

/** Pure sizing rules shared by image, avatar and PDF preview decoders. */
object ImagePreviewPolicy {
    const val MAX_SOURCE_BYTES: Long = 8L * 1024 * 1024
    const val AVATAR_SOURCE_BYTES: Long = 2L * 1024 * 1024
    const val MAX_DECODE_DIMENSION = 2048
    const val AVATAR_DECODE_DIMENSION = 512
    const val MAX_PDF_WIDTH = 1100
    const val MAX_PDF_HEIGHT = 1400
    const val MAX_PDF_SCALE = 2f
    private const val MAX_SAMPLE_SIZE = 1_073_741_824

    /** Power-of-two sample size that keeps both decoded dimensions within the cap. */
    fun sampleSize(
        width: Int,
        height: Int,
        maxDimension: Int = MAX_DECODE_DIMENSION
    ): Int {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val cap = maxDimension.coerceAtLeast(1)
        var sample = 1
        while ((safeWidth / sample > cap || safeHeight / sample > cap) && sample < MAX_SAMPLE_SIZE) {
            sample *= 2
        }
        return sample
    }

    fun decodedDimensions(width: Int, height: Int, sampleSize: Int): Pair<Int, Int> =
        (width.coerceAtLeast(1) / sampleSize.coerceAtLeast(1)) to
            (height.coerceAtLeast(1) / sampleSize.coerceAtLeast(1))

    /** Scale that fits a PDF page inside the preview without magnifying small pages. */
    fun pdfScale(width: Int, height: Int): Float {
        val safeWidth = width.coerceAtLeast(1).toFloat()
        val safeHeight = height.coerceAtLeast(1).toFloat()
        return minOf(
            MAX_PDF_WIDTH / safeWidth,
            MAX_PDF_HEIGHT / safeHeight,
            MAX_PDF_SCALE
        )
    }

    fun pdfDimensions(width: Int, height: Int, scale: Float): Pair<Int, Int> {
        val safeScale = scale.coerceAtLeast(0f)
        return (width * safeScale).toInt().coerceAtLeast(1) to
            (height * safeScale).toInt().coerceAtLeast(1)
    }
}
