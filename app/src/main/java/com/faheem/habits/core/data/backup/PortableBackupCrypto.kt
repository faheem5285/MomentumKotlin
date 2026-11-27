package com.faheem.habits.core.data.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, authenticated encryption for exported backups. The passphrase is
 * never stored by the app; only the derived AES key encrypts the JSON payload.
 */
internal object PortableBackupCrypto {
    private val MAGIC = "MOMENTUM-ENC".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256
    const val DEFAULT_ITERATIONS = 120_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 1_000_000

    private val headerBytes = MAGIC.size + 1 + 4 + SALT_BYTES + IV_BYTES

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= headerBytes && bytes.copyOf(MAGIC.size).contentEquals(MAGIC)

    fun encrypt(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "Enter a backup password." }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Invalid backup password settings." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = header(salt, iv, iterations)
        val key = deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } catch (_: Exception) {
            throw IllegalStateException("Backup encryption could not be completed.")
        } finally {
            destroyKey(key)
        }
    }

    fun decrypt(packed: ByteArray, passphrase: CharArray): ByteArray {
        require(isEncrypted(packed)) { "This is not an encrypted Momentum backup." }
        require(passphrase.isNotEmpty()) { "Enter the backup password." }
        val version = packed[MAGIC.size].toInt()
        require(version == VERSION) { "This backup password format is not supported." }
        val iterations = ByteBuffer.wrap(packed, MAGIC.size + 1, 4).int
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "This backup password format is not supported." }
        val salt = packed.copyOfRange(MAGIC.size + 5, MAGIC.size + 5 + SALT_BYTES)
        val iv = packed.copyOfRange(MAGIC.size + 5 + SALT_BYTES, headerBytes)
        val header = packed.copyOfRange(0, headerBytes)
        val key = deriveKey(passphrase, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            return cipher.doFinal(packed, headerBytes, packed.size - headerBytes)
        } catch (_: Exception) {
            throw IllegalArgumentException("The backup password is incorrect or the backup is damaged.")
        } finally {
            destroyKey(key)
        }
    }

    /** Best-effort cleanup; some JVM providers refuse to destroy derived keys. */
    private fun destroyKey(key: SecretKeySpec) {
        runCatching { key.destroy() }
    }

    private fun header(salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray =
        MAGIC + byteArrayOf(VERSION.toByte()) +
            ByteBuffer.allocate(4).putInt(iterations).array() + salt + iv

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return try {
                SecretKeySpec(derived, "AES")
            } finally {
                derived.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
