package com.faheem.habits.core.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed encryption for the automatic first-sync backup. This backup
 * contains ordinary app data, never vault secrets, and must not sit on disk as
 * plaintext JSON.
 */
internal object LocalBackupCrypto {
    private const val KEY_ALIAS = "momentum_first_sync_backup_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val VERSION = 1
    private val AAD = "momentum-first-sync-backup-v1".toByteArray(Charsets.UTF_8)

    @Synchronized
    fun encrypt(context: Context, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(plaintext)
        require(cipher.iv.size == IV_BYTES) { "Unexpected backup encryption parameters." }
        return byteArrayOf(VERSION.toByte()) + cipher.iv + encrypted
    }

    /** Used by verification tooling and restore flows; never logs plaintext. */
    @Synchronized
    fun decrypt(context: Context, packed: ByteArray): ByteArray {
        require(packed.size > 1 + IV_BYTES && packed[0].toInt() == VERSION) { "Unsupported first-sync backup format." }
        val iv = packed.copyOfRange(1, 1 + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(packed, 1 + IV_BYTES, packed.size - 1 - IV_BYTES)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
