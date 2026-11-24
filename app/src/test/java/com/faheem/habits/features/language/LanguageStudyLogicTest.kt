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

    @Test fun missedWordMovesBackAndSchedulesShortRetryWithoutIncreasingCorrectCount() {
        val now = 5_000L
        val previous = ReviewState(
            wordId = 42,
            box = 3,
            nextReviewAt = now,
            lastReviewedAt = now - 1_000,
            correctCount = 3,
            reviewCount = 4
        )

        val retry = LanguageStudyLogic.schedule(previous, now, correct = false)

        assertEquals(2, retry.box)
        assertEquals(now + LanguageStudyLogic.RETRY_DELAY_MILLIS, retry.nextReviewAt)
        assertEquals(now, retry.lastReviewedAt)
        assertEquals(3, retry.correctCount)
        assertEquals(5, retry.reviewCount)
    }

    @Test fun firstCorrectReviewStartsAtOneDay() {
        val now = 9_000L

        val scheduled = LanguageStudyLogic.schedule(null, now, correct = true)

        assertEquals(0, scheduled.box)
        assertEquals(now + 86_400_000L, scheduled.nextReviewAt)
        assertEquals(1, scheduled.correctCount)
        assertEquals(1, scheduled.reviewCount)
    }

    @Test fun firstMissedReviewRetriesSoonThenStartsAtOneDayAfterCorrectAnswer() {
        val now = 15_000L
        val retry = LanguageStudyLogic.schedule(null, now, correct = false)

        assertEquals(-1, retry.box)
        assertEquals(now + LanguageStudyLogic.RETRY_DELAY_MILLIS, retry.nextReviewAt)

        val nextCorrect = LanguageStudyLogic.schedule(retry, retry.nextReviewAt, correct = true)
        assertEquals(0, nextCorrect.box)
        assertEquals(retry.nextReviewAt + 86_400_000L, nextCorrect.nextReviewAt)
        assertEquals(1, nextCorrect.correctCount)
        assertEquals(2, nextCorrect.reviewCount)
    }
}
