package com.faheem.habits.features.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultSecret(
    val title: String,
    val type: String,
    val username: String,
    val password: String,
    val website: String,
    val notes: String
)

data class VaultListItem(val id: Int, val secret: VaultSecret, val updatedAt: Long)

object VaultCrypto {
    const val SALT_PREF = "vault_salt_v1"
    const val CHECK_PREF = "vault_check_v1"
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    @Serializable
    private data class VaultPayload(
        val title: String,
        val type: String,
        val username: String,
        val password: String,
        val website: String,
        val notes: String
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun newSalt(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    fun deriveKey(masterPassword: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(masterPassword.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verifier(key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal("momentum-vault-master-check-v1".toByteArray(Charsets.UTF_8))
    }

    fun matches(key: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(verifier(key), expected)

    fun encrypt(secret: VaultSecret, key: ByteArray): Pair<ByteArray, ByteArray> {
        val plaintext = json.encodeToString(
            VaultPayload(
                title = secret.title,
                type = secret.type,
                username = secret.username,
                password = secret.password,
                website = secret.website,
                notes = secret.notes
            )
        ).toByteArray(Charsets.UTF_8)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            iv to cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, key: ByteArray): VaultSecret {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return try {
            val payload = json.decodeFromString<VaultPayload>(String(plaintext, Charsets.UTF_8))
            VaultSecret(
                title = payload.title,
                type = payload.type,
                username = payload.username,
                password = payload.password,
                website = payload.website,
                notes = payload.notes
            )
        } finally {
            plaintext.fill(0)
        }
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
