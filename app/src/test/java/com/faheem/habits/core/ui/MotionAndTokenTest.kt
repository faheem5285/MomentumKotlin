package com.faheem.habits.core.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAndTokenTest {
    @Test
    fun reducedEffectsUsesSnapInsteadOfATween() {
        assertTrue(animationSpecFor<Float>(reduceEffects = true) is SnapSpec<*>)
    }

    @Test
    fun normalMotionUsesTheSharedTokenDuration() {
        val spec = animationSpecFor<Float>(reduceEffects = false)
        assertTrue(spec is TweenSpec<*>)
        assertEquals(MomentumTokens.motionMedium, (spec as TweenSpec<Float>).durationMillis)
    }

    @Test
    fun spacingTokensMatchTheDocumentedRhythm() {
        assertEquals(4.dp, MomentumTokens.space1)
        assertEquals(8.dp, MomentumTokens.space2)
        assertEquals(12.dp, MomentumTokens.space3)
        assertEquals(16.dp, MomentumTokens.space4)
        assertEquals(24.dp, MomentumTokens.space5)
        assertEquals(32.dp, MomentumTokens.space6)
        assertEquals(18.dp, MomentumTokens.screenPadding)
        assertEquals(18.dp, MomentumTokens.cardPadding)
    }

    @Test
    fun chartSummaryAvoidsReadingEveryValue() {
        assertEquals("Bar chart, no data", chartSummaryDescription("Bar chart", emptyList()))
        assertEquals(
            "Bar chart, 3 values, from 2 to 9",
            chartSummaryDescription("Bar chart", listOf(4f, 9f, 2f))
        )
    }
}
