package com.faheem.habits.core.data.supabase

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores only the Supabase refresh token, encrypted with an Android Keystore AES key.
 * The key itself is non-exportable and the preference file contains ciphertext only.
 */
object SecureTokenStore {
    private const val PREFS = "momentum_auth_session"
    private const val TOKEN_KEY = "encrypted_refresh_token"
    private const val KEY_ALIAS = "momentum_supabase_refresh_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun saveRefreshToken(context: Context, token: String) {
        require(token.isNotBlank()) { "Refresh token is empty." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        val packed = cipher.iv + encrypted
        val saved = context.applicationContext.getSharedPreferences(PREFS, 0).edit()
            .putString(TOKEN_KEY, Base64.encodeToString(packed, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not securely save the sign-in session." }
    }

    @Synchronized
    fun readRefreshToken(context: Context): String? {
        val encoded = appContext().getSharedPreferences(PREFS, 0).getString(TOKEN_KEY, null)
            ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_LENGTH) { "Stored session data is invalid." }
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val encrypted = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            clear(context)
            null
        }
    }

    @Synchronized
    fun clear() {
        appContext().getSharedPreferences(PREFS, 0).edit().remove(TOKEN_KEY).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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

    private fun appContext(): android.content.Context =
        com.faheem.habits.app.MomentumApplication.context
}
