package com.faheem.habits.core.data.supabase

import java.util.Base64

internal const val SECURE_TOKEN_IV_LENGTH = 12

/** Versioned storage envelope for the encrypted refresh token. */
internal data class TokenEnvelope(
    val version: Int,
    val iv: ByteArray,
    val cipherText: ByteArray
)

internal object SecureTokenEnvelope {
    private const val V2_PREFIX = "v2:"
    private const val AAD = "momentum-supabase-refresh-v2"

    fun encodeV2(iv: ByteArray, cipherText: ByteArray): String =
        V2_PREFIX + Base64.getEncoder().encodeToString(iv + cipherText)

    fun decode(encoded: String): TokenEnvelope? {
        val version: Int
        val packed: ByteArray
        if (encoded.startsWith(V2_PREFIX)) {
            version = 2
            packed = runCatching {
                Base64.getDecoder().decode(encoded.removePrefix(V2_PREFIX))
            }.getOrNull() ?: return null
        } else {
            version = 1
            packed = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        }
        if (packed.size <= SECURE_TOKEN_IV_LENGTH) return null
        return TokenEnvelope(
            version = version,
            iv = packed.copyOfRange(0, SECURE_TOKEN_IV_LENGTH),
            cipherText = packed.copyOfRange(SECURE_TOKEN_IV_LENGTH, packed.size)
        )
    }

    fun associatedData(): ByteArray = AAD.toByteArray(Charsets.UTF_8)
}
