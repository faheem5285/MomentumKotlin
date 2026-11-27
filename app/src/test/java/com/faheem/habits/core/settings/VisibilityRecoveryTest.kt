package com.faheem.habits.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibilityRecoveryTest {
    private val modules = listOf("Habits", "Vault", "Battery")

    @Test
    fun absentKeysAreVisibleAndFalseKeysAreHidden() {
        val visibility = mapOf("Vault" to false)
        assertEquals(listOf("Vault"), VisibilityRecovery.hiddenModules(visibility, modules))
        assertEquals(1, VisibilityRecovery.hiddenCount(visibility, modules))
    }

    @Test
    fun recoveryRestoresEveryModuleAndKeepsUnrelatedKeys() {
        val recovered = VisibilityRecovery.recoverAll(
            mapOf("Habits" to false, "Vault" to false, "Unknown" to false),
            modules
        )
        assertTrue(recovered.getValue("Habits"))
        assertTrue(recovered.getValue("Vault"))
        assertTrue(recovered.getValue("Battery"))
        assertFalse(recovered.getValue("Unknown"))
    }
}
