package com.faheem.habits.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCloudDeletePolicyTest {
    private val accountId = "5f4e2c8a-6b1d-4e73-9a20-2f6d9b7c1e44"

    @Test
    fun accountKeyIsStableAndDoesNotContainTheAccountId() {
        val first = pendingDeleteAccountKey(accountId)
        val second = pendingDeleteAccountKey(" ${accountId.uppercase()} ")
        assertEquals(first, second)
        assertTrue(first.startsWith("pending_"))
        assertFalse(first.contains(accountId, ignoreCase = true))
        assertFalse(first.contains("-"))
        assertEquals(72, first.length)
    }

    @Test
    fun queueIsMergedAndCompletedWithoutDuplicates() {
        val queued = mergePendingDeletes(setOf("momentum_habits"), listOf("momentum_expenses", "momentum_habits"))
        assertEquals(setOf("momentum_habits", "momentum_expenses"), queued)
        assertEquals(setOf("momentum_expenses"), withoutPendingDelete(queued, "momentum_habits"))
        assertEquals(queued, withoutPendingDelete(queued, "momentum_unknown"))
    }
}
