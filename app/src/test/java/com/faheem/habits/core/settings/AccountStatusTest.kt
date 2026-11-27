package com.faheem.habits.core.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountStatusTest {
    @Test
    fun signedInStatusDoesNotClaimLocalOnly() {
        assertEquals(AccountStatus.CONNECTED, AccountStatus.subtext(signedIn = true))
    }

    @Test
    fun signedOutStatusKeepsLocalRecordsAvailable() {
        assertEquals(AccountStatus.LOCAL_ONLY, AccountStatus.subtext(signedIn = false))
        assertTrueLocalPhrase(AccountStatus.subtext(signedIn = false))
    }

    @Test
    fun attentionAndOfflineHaveAccuratePriority() {
        assertEquals(
            AccountStatus.NEEDS_ATTENTION,
            AccountStatus.subtext(signedIn = true, requiresAccountAttention = true, offlineNotice = true)
        )
        assertEquals(
            AccountStatus.OFFLINE,
            AccountStatus.subtext(signedIn = false, offlineNotice = true)
        )
    }

    private fun assertTrueLocalPhrase(value: String) {
        assertEquals(true, value.contains("Local data is still available on this device"))
    }
}
