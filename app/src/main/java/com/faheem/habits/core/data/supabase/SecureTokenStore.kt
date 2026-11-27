package com.faheem.habits.core.data.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only the Supabase refresh token, encrypted with an Android Keystore AES key.
 * The key is non-exportable and the preference file contains ciphertext only.
 *
 * v2 binds the ciphertext to an application-specific AAD and keeps a version
 * prefix so legacy v1 tokens can be decrypted once and re-encrypted safely.
 */
object SecureTokenStore {
    private const val PREFS = "momentum_auth_session"
    private const val TOKEN_KEY = "encrypted_refresh_token"
    private const val KEY_ALIAS_V1 = "momentum_supabase_refresh_v1"
    private const val KEY_ALIAS_V2 = "momentum_supabase_refresh_v2"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun saveRefreshToken(context: Context, token: String) {
        require(token.isNotBlank()) { "Refresh token is empty." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(KEY_ALIAS_V2))
        cipher.updateAAD(SecureTokenEnvelope.associatedData())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoded = SecureTokenEnvelope.encodeV2(cipher.iv, encrypted)
        val saved = context.applicationContext.getSharedPreferences(PREFS, 0).edit()
            .putString(TOKEN_KEY, encoded)
            .commit()
        check(saved) { "Could not securely save the sign-in session." }
    }

    @Synchronized
    fun readRefreshToken(context: Context): String? {
        val encoded = context.applicationContext.getSharedPreferences(PREFS, 0).getString(TOKEN_KEY, null)
            ?: return null
        val envelope = SecureTokenEnvelope.decode(encoded) ?: run {
            clear(context)
            return null
        }
        return try {
            val keyAlias = if (envelope.version == 2) KEY_ALIAS_V2 else KEY_ALIAS_V1
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(keyAlias), GCMParameterSpec(TAG_LENGTH_BITS, envelope.iv))
            if (envelope.version == 2) cipher.updateAAD(SecureTokenEnvelope.associatedData())
            val token = String(cipher.doFinal(envelope.cipherText), Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
            if (token == null) {
                clear(context)
                null
            } else {
                if (envelope.version == 1) runCatching { saveRefreshToken(context, token) }
                token
            }
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    @Synchronized
    fun clear(context: Context) {
        check(context.applicationContext.getSharedPreferences(PREFS, 0).edit().remove(TOKEN_KEY).commit()) {
            "Could not clear the saved sign-in session."
        }
    }

    @Synchronized
    fun clearForAppReset(context: Context) {
        clear(context)
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        listOf(KEY_ALIAS_V1, KEY_ALIAS_V2).forEach { alias ->
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
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
