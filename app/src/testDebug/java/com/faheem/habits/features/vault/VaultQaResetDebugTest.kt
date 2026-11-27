package com.faheem.habits.features.vault

import com.faheem.habits.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultQaResetDebugTest {
    @Test
    fun vaultQaResetGateFollowsBuildConfiguration() {
        assertTrue(BuildConfig.DEBUG)
        assertTrue(VaultQaReset.isEnabled())
        assertFalse(VaultQaReset.isEnabled(false))
    }
}
