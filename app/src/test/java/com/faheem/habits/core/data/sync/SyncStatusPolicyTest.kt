package com.faheem.habits.core.data.sync

import com.faheem.habits.core.data.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStatusPolicyTest {
    @Test
    fun conflictIsCountedOnlyForDivergentDirtyLocalRows() {
        val local = SyncCandidate("id", 10, "device-a", false, dirty = true)
        val remote = SyncCandidate("id", 20, "device-b", false)
        val decision = decideRemote(local, remote)
        assertFalse(decision.applyRemote)
        assertTrue(decision.conflict)

        val sameRevision = decideRemote(local, local.copy(dirty = true))
        assertTrue(sameRevision.applyRemote.not())
        assertFalse(sameRevision.conflict)

        val noLocal = decideRemote(null, remote)
        assertTrue(noLocal.applyRemote)
        assertFalse(noLocal.conflict)

        val clean = decideRemote(local.copy(dirty = false), remote)
        assertTrue(clean.applyRemote)
        assertFalse(clean.conflict)
    }

    @Test
    fun statusCountsExcludeSyntheticRowAndFailuresAreNotMaskedByPending() {
        val states = listOf(
            SyncState(SYNC_STATUS_TABLE, pendingCount = 99, lastSyncEpochMillis = 1_000L),
            SyncState("momentum_habits", pendingCount = 2, conflictCount = 1),
            SyncState("momentum_expenses", lastError = "Cloud access was denied.")
        )
        val summary = summarizeSyncStatus(
            states = states,
            failedTables = 1,
            workRunning = false,
            workQueued = false,
            workFailed = false
        )
        assertEquals(2, summary.pending)
        assertEquals(1, summary.conflicts)
        assertEquals(2, summary.trackedTables)
        assertEquals(1, summary.errorTables)
        assertTrue(summary.hasSynced)
        assertTrue(summary.needsAttention)
        assertTrue(summary.waiting)
    }

    @Test
    fun runningWorkTakesPrecedenceButErrorsStillNeedAttention() {
        val states = listOf(SyncState("momentum_habits", pendingCount = 3))
        val running = summarizeSyncStatus(states, workRunning = true)
        assertTrue(running.running)
        assertFalse(running.needsAttention)

        val failed = summarizeSyncStatus(states, workFailed = true, workQueued = true)
        assertFalse(failed.running)
        assertTrue(failed.waiting)
        assertTrue(failed.needsAttention)
    }
}
