@file:Suppress("TooManyFunctions")

package com.faheem.habits.features.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import android.os.Build
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.SecretKey

data class VaultSecret(
    val title: String,
    val type: String,
    val username: String,
    val password: String,
    val website: String,
    val notes: String,
    val folder: String = "",
    val tags: String = "",
    val favorite: Boolean = false,
    val totp: String = ""
)

data class VaultListItem(val id: Int, val secret: VaultSecret, val updatedAt: Long)

data class VaultHealthReport(val weak: Int, val reused: Int, val duplicates: Int)

fun vaultHealthReport(items: List<VaultListItem>): VaultHealthReport {
    val passwords = items.map { it.secret.password }.filter(String::isNotBlank)
    val counts = passwords.groupingBy { it }.eachCount()
    return VaultHealthReport(
        weak = passwords.count { it.length < 12 || it.none(Char::isUpperCase) || it.none(Char::isDigit) },
        reused = counts.count { it.value > 1 },
        duplicates = passwords.size - counts.size
    )
}

object VaultCrypto {
    const val SALT_PREF = "vault_salt_v1"
    const val CHECK_PREF = "vault_check_v1"
    private const val PBKDF2_ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val VMK_BYTES = 32
    private const val BIOMETRIC_ALIAS = "momentum_vault_biometric_v1"

    @Serializable
    private data class VaultPayload(
        val title: String,
        val type: String,
        val username: String,
        val password: String,
        val website: String,
        val notes: String,
        val folder: String = "",
        val tags: String = "",
        val favorite: Boolean = false,
        val totp: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun newSalt(): ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)

    fun deriveKey(masterPassword: String, salt: ByteArray): ByteArray {
        return deriveKey(masterPassword.toCharArray(), salt)
    }

    fun deriveKey(masterPassword: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(masterPassword, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun newVmk(): ByteArray = ByteArray(VMK_BYTES).also(SecureRandom()::nextBytes)

    fun wrapVmk(vmk: ByteArray, wrappingKey: ByteArray): Pair<ByteArray, ByteArray> =
        encryptBytes(vmk, wrappingKey)

    fun unwrapVmk(iv: ByteArray, ciphertext: ByteArray, wrappingKey: ByteArray): ByteArray =
        decryptBytes(iv, ciphertext, wrappingKey)

    fun biometricCipher(): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(BIOMETRIC_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                builder.setUserAuthenticationValidityDurationSeconds(0)
            }
            generator.init(builder.build())
            generator.generateKey()
        }
        val key = (keyStore.getKey(BIOMETRIC_ALIAS, null) as SecretKey)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun biometricDecryptCipher(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (keyStore.getKey(BIOMETRIC_ALIAS, null) as SecretKey)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
    }

    fun wrapVmkWithBiometric(vmk: ByteArray, cipher: Cipher): Pair<ByteArray, ByteArray> =
        cipher.iv to cipher.doFinal(vmk)

    fun unwrapVmkWithBiometric(cipher: Cipher, ciphertext: ByteArray): ByteArray =
        cipher.doFinal(ciphertext)

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
                notes = secret.notes,
                folder = secret.folder,
                tags = secret.tags,
                favorite = secret.favorite,
                totp = secret.totp
            )
        ).toByteArray(Charsets.UTF_8)
        return try {
            encryptBytes(plaintext, key)
        } finally {
            plaintext.fill(0)
        }
    }

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, key: ByteArray): VaultSecret {
        val plaintext = decryptBytes(iv, ciphertext, key)
        return try {
            val payload = json.decodeFromString<VaultPayload>(String(plaintext, Charsets.UTF_8))
            VaultSecret(
                title = payload.title,
                type = payload.type,
                username = payload.username,
                password = payload.password,
                website = payload.website,
                notes = payload.notes,
                folder = payload.folder,
                tags = payload.tags,
                favorite = payload.favorite,
                totp = payload.totp
            )
        } finally {
            plaintext.fill(0)
        }
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun encryptBytes(plaintext: ByteArray, key: ByteArray): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv to cipher.doFinal(plaintext)
    }

    private fun decryptBytes(iv: ByteArray, ciphertext: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
