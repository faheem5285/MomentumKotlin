package com.faheem.habits.core.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpErrorSanitizerTest {
    @Test
    fun httpErrorNeverContainsRawBody() {
        val body = """{"msg":"bad token eyJhbGciOi.seed.secret for user@example.com", "token":"abcdefghijklmnopqrstuvwxyz0123456789"}"""
        val message = HttpErrorSanitizer.forHttp(401, body)
        assertFalse(message.contains("eyJhbGciOi"))
        assertFalse(message.contains("user@example.com"))
        assertFalse(message.contains("abcdefghijklmnop"))
        assertTrue(message.contains("sign in", ignoreCase = true))
    }

    @Test
    fun onlyAllowListedSupabaseCodesArePreserved() {
        val message = HttpErrorSanitizer.forHttp(404, """{"code":"PGRST205","secret":"do-not-leak"}""")
        assertTrue(message.contains("PGRST205"))
        assertFalse(message.contains("do-not-leak"))
    }

    @Test
    fun displaySanitizerRedactsCommonSecretsAndIdentifiers() {
        val raw = "Failed for user@example.com with Bearer eyJhbGciOi.secret.value id=123e4567-e89b-12d3-a456-426614174000 https://example.com/private"
        val safe = HttpErrorSanitizer.forDisplay(raw)
        assertFalse(safe.contains("user@example.com"))
        assertFalse(safe.contains("eyJhbGciOi"))
        assertFalse(safe.contains("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(safe.contains("https://example.com/private"))
        assertTrue(safe.contains("[redacted]"))
        assertTrue(safe.contains("[email]"))
        assertTrue(safe.contains("[id]"))
        assertTrue(safe.contains("[url]"))
    }

    @Test
    fun blankMessageUsesGenericText() {
        assertEquals("The cloud request could not be completed.", HttpErrorSanitizer.forDisplay(null))
    }
}
