package com.faheem.habits.features.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSensitivePolicyTest {
    @Test
    fun threatModelExplainsAccountKeyEscrowAndNoE2ee() {
        val copy = VAULT_CLOUD_KEY_THREAT_MODEL.lowercase()

        assertTrue(copy.contains("stored with your signed-in account"))
        assertTrue(copy.contains("not end-to-end encryption"))
        assertTrue(copy.contains("backup password"))
    }

    @Test
    fun clipboardHashIsStableAndDoesNotContainTheSecret() {
        val secret = "JBSWY3DPEHPK3PXP"
        val first = sensitiveClipboardHash(secret)
        val second = sensitiveClipboardHash(secret)

        assertArrayEquals(first, second)
        assertFalse(VaultCrypto.encode(first).contains(secret))
        assertFalse(first.contentEquals(sensitiveClipboardHash(secret + "x")))
    }

    @Test
    fun maskNeverRevealsTheValueOrItsLength() {
        assertFalse(MASKED_SENSITIVE_VALUE.contains("short"))
        assertFalse(MASKED_SENSITIVE_VALUE.contains("a-much-longer-secret-value"))
        assertTrue(MASKED_SENSITIVE_VALUE.length == 8)
    }
}
