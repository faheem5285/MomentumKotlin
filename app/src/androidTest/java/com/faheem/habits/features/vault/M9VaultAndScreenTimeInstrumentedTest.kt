package com.faheem.habits.features.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.features.screen_time.PermissionInfoCard
import com.faheem.habits.features.screen_time.USAGE_ACCESS_REVOCATION_COPY
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side M9 UI checks. These are compiled but not executed without a device/emulator. */
@RunWith(AndroidJUnit4::class)
class M9VaultAndScreenTimeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cloudKeyThreatModelIsVisibleInVault() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                VaultThreatModelCard()
            }
        }

        composeRule.onNodeWithText(VAULT_CLOUD_KEY_THREAT_MODEL).assertIsDisplayed()
    }

    @Test
    fun totpIsMaskedWithSeparateRevealAndCopyActions() {
        var reveals = 0
        var copies = 0
        val secret = "JBSWY3DPEHPK3PXP"
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                SensitiveVaultValue(
                    label = "TOTP secret",
                    value = secret,
                    revealed = false,
                    onToggleReveal = { reveals++ },
                    onCopy = { copies++ }
                )
            }
        }

        composeRule.onNodeWithText(MASKED_SENSITIVE_VALUE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Show or hide TOTP secret").performClick()
        composeRule.onNodeWithContentDescription("Copy TOTP secret").performClick()
        assertEquals(1, reveals)
        assertEquals(1, copies)
    }

    @Test
    fun usageAccessRevocationExplanationIsDisplayed() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                PermissionInfoCard(
                    title = "Unlock app usage",
                    description = USAGE_ACCESS_REVOCATION_COPY,
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithText(USAGE_ACCESS_REVOCATION_COPY).assertIsDisplayed()
    }
}
