package com.faheem.habits.core.data.supabase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionPolicyTest {
    @Test
    fun requiresNonBlankTokenAndMoreThanOneMinuteOfLifetime() {
        val now = 1_000_000L
        assertFalse(AuthSessionPolicy.hasFreshAccessToken(null, now + 120_000L, now))
        assertFalse(AuthSessionPolicy.hasFreshAccessToken("", now + 120_000L, now))
        assertFalse(AuthSessionPolicy.hasFreshAccessToken("token", now + 60_000L, now))
        assertFalse(AuthSessionPolicy.hasFreshAccessToken("token", now + 59_999L, now))
        assertTrue(AuthSessionPolicy.hasFreshAccessToken("token", now + 60_001L, now))
        assertTrue(AuthSessionPolicy.hasFreshAccessToken("token", now + 300_000L, now))
    }

    @Test
    fun onlyDefinitiveAuthenticationRejectionsClearSavedSession() {
        assertTrue(AuthSessionPolicy.shouldClearSession(400))
        assertTrue(AuthSessionPolicy.shouldClearSession(401))
        assertTrue(AuthSessionPolicy.shouldClearSession(403))
        assertFalse(AuthSessionPolicy.shouldClearSession(0))
        assertFalse(AuthSessionPolicy.shouldClearSession(408))
        assertFalse(AuthSessionPolicy.shouldClearSession(429))
        assertFalse(AuthSessionPolicy.shouldClearSession(500))
        assertFalse(AuthSessionPolicy.shouldClearSession(503))
    }
}
