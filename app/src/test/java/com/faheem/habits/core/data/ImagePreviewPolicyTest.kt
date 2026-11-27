package com.faheem.habits.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePreviewPolicyTest {
    @Test
    fun sampleSizeUsesPowersOfTwoUntilBothDimensionsFit() {
        assertEquals(1, ImagePreviewPolicy.sampleSize(1200, 800))
        assertEquals(2, ImagePreviewPolicy.sampleSize(3000, 2000))
        assertEquals(4, ImagePreviewPolicy.sampleSize(6000, 2000))
        assertEquals(8, ImagePreviewPolicy.sampleSize(9000, 9000))
    }

    @Test
    fun sampleSizeIsAlwaysPositiveAndBounded() {
        assertEquals(1, ImagePreviewPolicy.sampleSize(0, 0))
        assertEquals(64, ImagePreviewPolicy.sampleSize(100_000, 100_000))
        assertTrue(ImagePreviewPolicy.decodedDimensions(100_000, 100_000, 64).first <= 2048)
        assertEquals(400 to 200, ImagePreviewPolicy.decodedDimensions(800, 400, 2))
    }

    @Test
    fun pdfScaleFitsWithoutMagnifyingSmallPages() {
        assertEquals(2f, ImagePreviewPolicy.pdfScale(400, 400))
        assertEquals(0.5f, ImagePreviewPolicy.pdfScale(2200, 1600))
        assertEquals(0.5f, ImagePreviewPolicy.pdfScale(1000, 2800))
        assertEquals(1100 to 800, ImagePreviewPolicy.pdfDimensions(2200, 1600, 0.5f))
    }
}
