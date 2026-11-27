package com.faheem.habits.features.profile

import com.faheem.habits.core.data.supabase.SignUpOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthFlowTest {
    @Test
    fun signUpStateMatrixIsExplicit() {
        val confirmation = signUpMessage(SignUpOutcome.CONFIRMATION_REQUIRED)
        assertTrue(confirmation.canResendConfirmation)
        assertNull(confirmation.error)
        assertTrue(signUpMessage(SignUpOutcome.SESSION_STARTED).notice!!.contains("signed in"))
        assertTrue(signUpMessage(SignUpOutcome.ACCOUNT_EXISTS).error!!.contains("Sign in"))
        assertTrue(signUpMessage(SignUpOutcome.RATE_LIMITED).error!!.contains("Wait"))
        assertTrue(signUpMessage(SignUpOutcome.NETWORK_UNAVAILABLE).error!!.contains("offline"))
        assertTrue(signUpMessage(SignUpOutcome.REJECTED).error!!.contains("could not"))
    }

    @Test
    fun localDataRequiresExplicitLinkingDecision() {
        val firstSignIn = accountLinkPrompt(hasLocalRecords = true)
        assertTrue(firstSignIn.required)
        assertTrue(firstSignIn.message.contains("snapshot"))
        val switching = accountLinkPrompt(hasLocalRecords = true, switchingAccount = true)
        assertTrue(switching.message.contains("switching accounts"))
        assertFalse(accountLinkPrompt(hasLocalRecords = false).required)
    }

    @Test
    fun accountLinkNoticeMatchesWhatActuallyHappens() {
        assertNull(AccountLinkResult(false, false).notice)
        assertEquals(
            AccountLinkCopy.LINKED_LOCAL,
            AccountLinkResult(true, false).notice
        )
        assertEquals(
            AccountLinkCopy.LOADED_ACCOUNT,
            AccountLinkResult(true, true).notice
        )
    }

    @Test
    fun passwordChangeRequiresReauthAndMatchingPasswords() {
        assertEquals("Enter your current password.", passwordChangeError("", "newpassword", "newpassword", true))
        assertEquals(
            "Use at least 8 characters and make both new passwords match.",
            passwordChangeError("current", "short", "short", true)
        )
        assertEquals(
            "Choose a new password that is different from the current one.",
            passwordChangeError("samepass", "samepass", "samepass", true)
        )
        assertNull(passwordChangeError("", "newpassword", "newpassword", false))
    }

    @Test
    fun deletionCopyMatchesLocalRetentionAndRpcRequirement() {
        assertTrue(DeletionCopy.FIRST.contains("Local records stay on this device"))
        assertTrue(DeletionCopy.FINAL.contains("known file uploads may already have been removed"))
        assertTrue(DeletionCopy.SUCCESS.contains("remain on this device"))
    }

    @Test
    fun authErrorsNeverEchoEmailsOrTokens() {
        val secret = "person@example.com token=eyJhbGciOi.secret"
        val message = authErrorMessage(IOException(secret))
        assertFalse(message.contains("person@example.com"))
        assertFalse(message.contains("eyJhbGciOi"))
        assertTrue(message.contains("offline"))
        assertEquals(
            "The current password was not accepted.",
            authErrorMessage(IllegalStateException("The current password was not accepted."))
        )
    }
}
