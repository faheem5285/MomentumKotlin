package com.faheem.habits.features.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class VaultCryptoTest {

    private val secret = VaultSecret(
        title = "Personal email",
        type = "login",
        username = "faheem@example.com",
        password = "A-long-test-password-123!",
        website = "https://example.com",
        notes = "Vault crypto test"
    )

    @Test
    fun encryptAndDecrypt_roundTripsAllFields() {
        val key = VaultCrypto.deriveKey("correct horse battery staple", VaultCrypto.newSalt())
        val (iv, ciphertext) = VaultCrypto.encrypt(secret, key)

        val decrypted = VaultCrypto.decrypt(iv, ciphertext, key)

        assertEquals(secret, decrypted)
    }

    @Test
    fun decryptWithWrongKey_rejectsCiphertext() {
        val salt = VaultCrypto.newSalt()
        val correctKey = VaultCrypto.deriveKey("correct password", salt)
        val wrongKey = VaultCrypto.deriveKey("incorrect password", salt)
        val (iv, ciphertext) = VaultCrypto.encrypt(secret, correctKey)

        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(iv, ciphertext, wrongKey)
        }
    }

    @Test
    fun decryptTamperedCiphertext_rejectsChanges() {
        val key = VaultCrypto.deriveKey("correct horse battery staple", VaultCrypto.newSalt())
        val (iv, ciphertext) = VaultCrypto.encrypt(secret, key)
        val tampered = ciphertext.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }

        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(iv, tampered, key)
        }
    }

    @Test
    fun masterKeyVerifier_matchesOnlyTheSameKey() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("correct password", salt)
        val otherKey = VaultCrypto.deriveKey("different password", salt)

        val verifier = VaultCrypto.verifier(key)

        assertEquals(true, VaultCrypto.matches(key, verifier))
        assertEquals(false, VaultCrypto.matches(otherKey, verifier))
    }
}
