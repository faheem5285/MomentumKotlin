package com.faheem.habits.features.vault

import com.faheem.habits.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Test

class VaultQaResetReleaseTest {
    @Test
    fun vaultQaResetIsDisabledInReleaseBuilds() {
        assertFalse("Release builds must not report DEBUG", BuildConfig.DEBUG)
        assertFalse("Vault QA reset must not be available in release builds", VaultQaReset.isEnabled())
    }
}
