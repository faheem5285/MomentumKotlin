package com.faheem.habits.core.data.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class SignUpResponseTest {
    private val userId = "11111111-2222-3333-4444-555555555555"

    @Test
    fun confirmationResponseRequiresExplicitState() {
        val parsed = SignUpResponseParser.parse(200, """{"id":"$userId","email":"hidden@example.com"}""")
        assertEquals(SignUpOutcome.CONFIRMATION_REQUIRED, parsed.outcome)
        assertNull(parsed.session)
    }

    @Test
    fun completeSessionIsRecognizedWhenConfirmationIsDisabled() {
        val body = """{"access_token":"a.b.c","refresh_token":"r","expires_in":3600,"user":{"id":"$userId"}}"""
        val parsed = SignUpResponseParser.parse(200, body)
        assertEquals(SignUpOutcome.SESSION_STARTED, parsed.outcome)
        assertNotNull(parsed.session)
        assertEquals(userId, parsed.session?.user?.id)
    }

    @Test
    fun incompleteSessionNeverBecomesSignIn() {
        val parsed = SignUpResponseParser.parse(201, """{"access_token":"a","user":{"id":"$userId"}}""")
        assertEquals(SignUpOutcome.CONFIRMATION_REQUIRED, parsed.outcome)
        assertNull(parsed.session)
    }

    @Test
    fun existingAccountAndFailureCodesAreDistinct() {
        assertEquals(
            SignUpOutcome.ACCOUNT_EXISTS,
            SignUpResponseParser.parse(422, """{"code":"email_exists","msg":"already registered"}""").outcome
        )
        assertEquals(SignUpOutcome.RATE_LIMITED, SignUpResponseParser.parse(429, "").outcome)
        assertEquals(SignUpOutcome.SERVICE_UNAVAILABLE, SignUpResponseParser.parse(503, "").outcome)
        assertEquals(SignUpOutcome.REJECTED, SignUpResponseParser.parse(400, "bad request").outcome)
    }

    @Test
    fun networkFailuresAreSanitized() {
        assertEquals(SignUpOutcome.NETWORK_UNAVAILABLE, SignUpResponseParser.fromFailure(IOException("offline")))
        assertEquals(SignUpOutcome.REJECTED, SignUpResponseParser.fromFailure(IllegalStateException("bad")))
    }
}
