package com.faheem.habits.features.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryLinkParserTest {
    private val now = 1_700_000_000_000L

    @Test
    fun coldLinkWithRecoveryTypeAndExpiryIsAccepted() {
        val link = "momentum://auth/reset#access_token=eyJhbGciOi.token&type=recovery&expires_in=3600"
        val result = RecoveryLinkParser.parse(link, now)
        assertTrue(result is RecoveryLinkResult.Valid)
        val valid = result as RecoveryLinkResult.Valid
        assertEquals("eyJhbGciOi.token", valid.accessToken)
        assertEquals(now + 3_600_000L, valid.expiresAtMillis)
    }

    @Test
    fun warmLinkWithQueryParametersIsAccepted() {
        val link = "momentum://auth/reset?type=recovery&expires_at=${now / 1000 + 60}&access_token=token123"
        assertTrue(RecoveryLinkParser.parse(link, now) is RecoveryLinkResult.Valid)
    }

    @Test
    fun wrongTypeProviderErrorAndMissingTokenAreRejected() {
        val wrongType = RecoveryLinkParser.parse(
            "momentum://auth/reset#access_token=t&type=signup&expires_in=60", now
        )
        assertTrue(wrongType is RecoveryLinkResult.Invalid)
        assertTrue((wrongType as RecoveryLinkResult.Invalid).message.contains("not a password reset"))

        val providerError = RecoveryLinkParser.parse(
            "momentum://auth/reset#error=access_denied&type=recovery&expires_in=60", now
        )
        assertTrue(providerError is RecoveryLinkResult.Invalid)
        assertFalse((providerError as RecoveryLinkResult.Invalid).message.contains("access_denied"))

        val missingToken = RecoveryLinkParser.parse(
            "momentum://auth/reset#type=recovery&expires_in=60", now
        )
        assertTrue(missingToken is RecoveryLinkResult.Invalid)
    }

    @Test
    fun expiredAndMalformedLinksAreRejectedWithoutEchoingTokens() {
        val expired = RecoveryLinkParser.parse(
            "momentum://auth/reset#access_token=secret.token&type=recovery&expires_in=-1", now
        )
        assertTrue(expired is RecoveryLinkResult.Invalid)
        assertTrue((expired as RecoveryLinkResult.Invalid).message.contains("expired"))
        assertFalse(expired.message.contains("secret.token"))

        val otherHost = RecoveryLinkParser.parse(
            "momentum://module/profile#access_token=t&type=recovery&expires_in=60", now
        )
        assertTrue(otherHost is RecoveryLinkResult.Invalid)
        assertEquals(RecoveryLinkResult.Absent, RecoveryLinkParser.parse(null, now))
        assertEquals(RecoveryLinkResult.Absent, RecoveryLinkParser.parse("  ", now))
    }
}
