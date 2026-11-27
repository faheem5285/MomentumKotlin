package com.faheem.habits.core.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupCryptoTest {
    private val plaintext = "{\"format\":\"momentum-backup\",\"name\":\"Casey\"}".toByteArray(Charsets.UTF_8)
    private val passphrase = "correct horse battery staple".toCharArray()

    @Test
    fun roundTripHidesPlaintextAndRestoresExactBytes() {
        val packed = PortableBackupCrypto.encrypt(plaintext, passphrase, iterations = 1_000)

        assertTrue(PortableBackupCrypto.isEncrypted(packed))
        assertFalse(String(packed, Charsets.UTF_8).contains("Casey"))
        assertFalse(String(packed, Charsets.UTF_8).contains("momentum-backup"))
        assertArrayEquals(plaintext, PortableBackupCrypto.decrypt(packed, passphrase))
    }

    @Test
    fun wrongPassphraseAndTamperingAreRejectedWithSanitizedErrors() {
        val packed = PortableBackupCrypto.encrypt(plaintext, passphrase, iterations = 1_000)
        val wrongPassword = runCatching {
            PortableBackupCrypto.decrypt(packed, "wrong password".toCharArray())
        }
        assertTrue(wrongPassword.isFailure)
        assertEquals(
            "The backup password is incorrect or the backup is damaged.",
            wrongPassword.exceptionOrNull()?.message
        )

        val tampered = packed.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 1).toByte()
        assertTrue(runCatching { PortableBackupCrypto.decrypt(tampered, passphrase) }.isFailure)
    }

    @Test
    fun unsupportedEnvelopesAndEmptyPassphrasesAreRejected() {
        assertFalse(PortableBackupCrypto.isEncrypted(plaintext))

        val notEncrypted = runCatching { PortableBackupCrypto.decrypt(plaintext, passphrase) }
        assertTrue(notEncrypted.isFailure)
        assertEquals(
            "This is not an encrypted Momentum backup.",
            notEncrypted.exceptionOrNull()?.message
        )

        val packed = PortableBackupCrypto.encrypt(plaintext, passphrase, iterations = 1_000)
        val missingPassword = runCatching { PortableBackupCrypto.decrypt(packed, charArrayOf()) }
        assertTrue(missingPassword.isFailure)
        assertEquals("Enter the backup password.", missingPassword.exceptionOrNull()?.message)

        val emptyPassword = runCatching {
            PortableBackupCrypto.encrypt(plaintext, charArrayOf(), iterations = 1_000)
        }
        assertTrue(emptyPassword.isFailure)
        assertEquals("Enter a backup password.", emptyPassword.exceptionOrNull()?.message)
    }
}
