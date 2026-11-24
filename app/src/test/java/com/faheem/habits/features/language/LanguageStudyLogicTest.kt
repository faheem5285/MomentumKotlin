package com.faheem.habits.features.language

import com.faheem.habits.core.data.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageStudyLogicTest {
    @Test fun advancesThroughLeitnerIntervalsAndCapsAtFiveBoxes() {
        val now = 1_000L
        var state: ReviewState? = null
        val expectedDays = listOf(1, 2, 4, 8, 16, 16)
        expectedDays.forEachIndexed { index, days ->
            state = LanguageStudyLogic.schedule(state?.copy(wordId = 42), now)
            assertEquals(index.coerceAtMost(4), state?.box)
            assertEquals(now + days * 86_400_000L, state?.nextReviewAt)
            assertEquals(index + 1, state?.reviewCount)
        }
    }
}
