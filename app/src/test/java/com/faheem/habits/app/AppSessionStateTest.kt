package com.faheem.habits.app

import com.faheem.habits.core.data.supabase.SessionRestoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionStateTest {
    @Test
    fun noSessionKeepsLocalAppReady() {
        val state = reduceAppSession(AppSessionState.Initial, AppSessionEvent.RestoreFinished(SessionRestoreResult.NO_SESSION))
        assertEquals(AppSessionPhase.READY, state.phase)
        assertFalse(state.cloudSignedIn)
        assertFalse(state.requiresAccountAttention)
    }

    @Test
    fun authenticatedSessionEnablesCloud() {
        val state = reduceAppSession(AppSessionState.Initial, AppSessionEvent.RestoreFinished(SessionRestoreResult.AUTHENTICATED))
        assertEquals(AppSessionPhase.READY, state.phase)
        assertTrue(state.cloudSignedIn)
        assertFalse(state.requiresAccountAttention)
    }

    @Test
    fun offlineRestoreKeepsLocalDataAndShowsNotice() {
        val state = reduceAppSession(AppSessionState.Initial, AppSessionEvent.RestoreFinished(SessionRestoreResult.TEMPORARY_FAILURE))
        assertEquals(AppSessionPhase.READY, state.phase)
        assertFalse(state.cloudSignedIn)
        assertTrue(state.notice!!.contains("local records are available", ignoreCase = true))
    }

    @Test
    fun wipeAfterRestoreRoutesToAccountWithoutBlockingLocalUse() {
        val state = reduceAppSession(
            AppSessionState.Initial,
            AppSessionEvent.RestoreFinished(SessionRestoreResult.AUTHENTICATED, wipeApplied = true)
        )
        assertEquals(AppSessionPhase.READY, state.phase)
        assertFalse(state.cloudSignedIn)
        assertTrue(state.requiresAccountAttention)
    }

    @Test
    fun signOutKeepsLocalAppReady() {
        val signedIn = reduceAppSession(
            AppSessionState.Initial,
            AppSessionEvent.RestoreFinished(SessionRestoreResult.AUTHENTICATED)
        )
        val state = reduceAppSession(signedIn, AppSessionEvent.SignedOut(keepLocalData = true))
        assertEquals(AppSessionPhase.READY, state.phase)
        assertFalse(state.cloudSignedIn)
        assertTrue(state.notice!!.contains("Local records", ignoreCase = true))
    }

    @Test
    fun wipeEventAlwaysClearsCloudState() {
        val signedIn = reduceAppSession(
            AppSessionState.Initial,
            AppSessionEvent.RestoreFinished(SessionRestoreResult.AUTHENTICATED)
        )
        val state = reduceAppSession(signedIn, AppSessionEvent.WipeApplied)
        assertFalse(state.cloudSignedIn)
        assertTrue(state.requiresAccountAttention)
    }

    @Test
    fun noticeCanBeDismissedWithoutChangingPhase() {
        val state = reduceAppSession(AppSessionState.Initial, AppSessionEvent.WipeApplied)
        val dismissed = reduceAppSession(state, AppSessionEvent.NoticeDismissed)
        assertEquals(AppSessionPhase.READY, dismissed.phase)
        assertEquals(null, dismissed.notice)
        assertTrue(dismissed.requiresAccountAttention)
    }
}
