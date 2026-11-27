package com.faheem.habits.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatPolicyTest {
    @Test
    fun currentFormatTargetsRoomSchema22() {
        assertEquals(4, BackupFormatPolicy.VERSION)
        assertEquals(22, BackupFormatPolicy.CURRENT_SCHEMA)
        assertTrue(BackupFormatPolicy.isSupported(4, 22))
        assertFalse(BackupFormatPolicy.isSupported(4, 20))
    }

    @Test
    fun legacyFormatsRemainReadable() {
        assertTrue(BackupFormatPolicy.isSupported(3, 20))
        assertTrue(BackupFormatPolicy.isSupported(2, 11))
        assertTrue(BackupFormatPolicy.isSupported(2, 13))
        assertFalse(BackupFormatPolicy.isSupported(2, 20))
        assertFalse(BackupFormatPolicy.isSupported(5, 22))
    }

    @Test
    fun allUserDataTablesAreBackedUpButVaultIsNot() {
        assertEquals(20, BackupFormatPolicy.TABLE_KEYS.size)
        assertEquals(
            setOf(
                "habits", "completions", "habitSkips", "reminders", "expenses", "categoryBudgets",
                "contacts", "loanPeople", "loans", "loanAttachments", "loanPayments", "files",
                "todos", "lifeRules", "lifeRuleFiles", "customNotifications",
                "vocabularyWords", "grammarNotes", "reviewStates", "translationHistory"
            ),
            BackupFormatPolicy.TABLE_KEYS.toSet()
        )
        assertFalse(BackupFormatPolicy.TABLE_KEYS.any { it.contains("vault", ignoreCase = true) })
    }
}
