package com.faheem.habits.core.data.supabase

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTokenEnvelopeTest {
    @Test
    fun v2RoundTripPreservesIvAndCipherText() {
        val iv = ByteArray(SECURE_TOKEN_IV_LENGTH) { (it + 1).toByte() }
        val cipherText = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = SecureTokenEnvelope.encodeV2(iv, cipherText)
        val decoded = SecureTokenEnvelope.decode(encoded)
        assertNotNull(decoded)
        assertEquals(2, decoded!!.version)
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(cipherText, decoded.cipherText)
        assertTrue(String(SecureTokenEnvelope.associatedData()).isNotBlank())
    }

    @Test
    fun legacyBase64IsDecodedAsVersionOne() {
        val packed = ByteArray(SECURE_TOKEN_IV_LENGTH + 2) { it.toByte() }
        val legacy = java.util.Base64.getEncoder().encodeToString(packed)
        val decoded = SecureTokenEnvelope.decode(legacy)
        assertNotNull(decoded)
        assertEquals(1, decoded!!.version)
        assertEquals(packed.size - SECURE_TOKEN_IV_LENGTH, decoded.cipherText.size)
    }

    @Test
    fun malformedEnvelopeIsRejected() {
        assertNull(SecureTokenEnvelope.decode("not base64"))
        assertNull(SecureTokenEnvelope.decode("v2:AAAA"))
        assertNull(SecureTokenEnvelope.decode(""))
    }
}
