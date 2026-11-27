package com.faheem.habits.core.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstSyncBackupTest {
    @Test
    fun backupNamesAreAccountScopedAndPiiFree() {
        val first = FirstSyncBackup.fileNameForAccount("11111111-2222-3333-4444-555555555555")
        val second = FirstSyncBackup.fileNameForAccount("22222222-3333-4444-5555-666666666666")
        assertTrue(first.startsWith("sync-first-local-backup-"))
        assertTrue(first.endsWith(".enc"))
        assertNotEquals(first, second)
        assertFalse(first.contains("11111111"))
        assertFalse(first.contains("@"))
    }

    @Test
    fun missingAccountUsesStableLocalMarker() {
        assertEquals(FirstSyncBackup.fileNameForAccount(null), FirstSyncBackup.fileNameForAccount(""))
        assertEquals("sync-first-local-backup.json", FirstSyncBackup.LEGACY_PLAINTEXT_FILE)
        assertEquals(10 * 1024 * 1024, FirstSyncBackup.MAX_BYTES)
    }
}
