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

    @Test
    fun vmkCanBeRewrappedWithoutChangingIt() {
        val vmk = VaultCrypto.newVmk()
        val oldPasswordKey = VaultCrypto.deriveKey("old password", VaultCrypto.newSalt())
        val newPasswordKey = VaultCrypto.deriveKey("new password", VaultCrypto.newSalt())

        val oldWrapper = VaultCrypto.wrapVmk(vmk, oldPasswordKey)
        val recovered = VaultCrypto.unwrapVmk(oldWrapper.first, oldWrapper.second, oldPasswordKey)
        val newWrapper = VaultCrypto.wrapVmk(recovered, newPasswordKey)

        assertEquals(vmk.toList(), VaultCrypto.unwrapVmk(newWrapper.first, newWrapper.second, newPasswordKey).toList())
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.unwrapVmk(newWrapper.first, newWrapper.second, oldPasswordKey)
        }
    }

    @Test
    fun vmkWrapperRejectsTampering() {
        val vmk = VaultCrypto.newVmk()
        val key = VaultCrypto.deriveKey("password", VaultCrypto.newSalt())
        val wrapped = VaultCrypto.wrapVmk(vmk, key)
        val tampered = wrapped.second.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.unwrapVmk(wrapped.first, tampered, key)
        }
    }

    @Test
    fun legacyRecordCanBeMigratedToVmkEncryption() {
        val legacyKey = VaultCrypto.deriveKey("legacy password", VaultCrypto.newSalt())
        val legacy = VaultCrypto.encrypt(secret, legacyKey)
        val vmk = VaultCrypto.newVmk()
        val migratedSecret = VaultCrypto.decrypt(legacy.first, legacy.second, legacyKey)
        val migrated = VaultCrypto.encrypt(migratedSecret, vmk)

        assertEquals(secret, VaultCrypto.decrypt(migrated.first, migrated.second, vmk))
        assertThrows(AEADBadTagException::class.java) {
            VaultCrypto.decrypt(migrated.first, migrated.second, legacyKey)
        }
    }
}
