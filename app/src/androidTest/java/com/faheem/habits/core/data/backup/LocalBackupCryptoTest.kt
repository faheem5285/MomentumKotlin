package com.faheem.habits.core.data.backup

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Keystore round trip; compiled here, not executed without a device. */
@RunWith(AndroidJUnit4::class)
class LocalBackupCryptoTest {
    @Test
    fun encryptsAndDecryptsBoundedPayload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val plaintext = """{"format":"momentum-backup","rows":123}""".toByteArray(Charsets.UTF_8)
        val encrypted = LocalBackupCrypto.encrypt(context, plaintext)
        assertFalse(String(encrypted, Charsets.UTF_8).contains("momentum-backup"))
        assertArrayEquals(plaintext, LocalBackupCrypto.decrypt(context, encrypted))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val encrypted = LocalBackupCrypto.encrypt(context, "sample".toByteArray(Charsets.UTF_8))
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex] + 1).toByte()
        val rejected = runCatching { LocalBackupCrypto.decrypt(context, tampered) }.isFailure
        assertTrue(rejected)
    }
}
