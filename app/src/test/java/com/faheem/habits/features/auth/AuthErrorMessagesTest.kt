package com.faheem.habits.features.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMessagesTest {
    @Test
    fun mapsCommonSupabaseAuthenticationFailures() {
        assertEquals(
            "The email or password is incorrect. Check your details and try again.",
            userFacingAuthError(IllegalStateException("Invalid login credentials"))
        )
        assertEquals(
            "Please confirm your email using the link we sent, then sign in.",
            userFacingAuthError(IllegalStateException("Email not confirmed"))
        )
        assertEquals(
            "An account already exists for this email. Try signing in instead.",
            userFacingAuthError(IllegalStateException("User already registered"))
        )
        assertEquals(
            "Too many attempts. Please wait a little and try again.",
            userFacingAuthError(IllegalStateException("Rate limit exceeded"))
        )
    }

    @Test
    fun doesNotExposeUnknownBackendDetails() {
        assertEquals(
            "We couldn't complete that request. Check your connection and try again.",
            userFacingAuthError(IllegalStateException("internal database detail: secret"))
        )
    }
}
