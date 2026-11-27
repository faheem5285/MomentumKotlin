package com.faheem.habits.features.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionTimeoutTest {
    @Test
    fun vaultDoesNotLockBeforeTimeout() {
        assertFalse(VaultSessionTimeout.shouldLock(59_999L, 0L))
        assertFalse(VaultSessionTimeout.shouldLock(100_000L, 99_999L))
    }

    @Test
    fun vaultLocksAtTimeout() {
        assertTrue(VaultSessionTimeout.shouldLock(120_001L, 1L))
        assertTrue(VaultSessionTimeout.shouldLock(200_000L, 80_000L))
    }

    @Test
    fun clockRegressionDoesNotLock() {
        assertFalse(VaultSessionTimeout.shouldLock(10_000L, 20_000L))
    }
}
