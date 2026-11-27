package com.faheem.habits.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side semantics checks for the shared components introduced in M3. */
@RunWith(AndroidJUnit4::class)
class DesignSystemUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressBarExposesDescriptionAndRange() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true, glassIntensity = 0.88f) {
                ProgressBar(fraction = 0.4f, color = Color.Magenta, track = Color.DarkGray)
            }
        }
        composeRule.onNodeWithContentDescription("Progress")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun chipExposesLabelAndTabRole() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true, glassIntensity = 1.12f) {
                MomentumChip(selected = true, onClick = {}, label = "Today")
            }
        }
        composeRule.onNodeWithContentDescription("Today")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    }

    @Test
    fun completionToggleExposesCheckboxState() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                CompletionToggle(checked = true, color = Color.Magenta, onClick = {}, label = "Mark complete")
            }
        }
        composeRule.onNodeWithContentDescription("Mark complete")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Completed"))
    }

    @Test
    fun sharedErrorStateStillOffersRetry() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                ListErrorState(message = "Could not load", onRetry = {})
            }
        }
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun heroCaptionRendersUnderReducedMotion() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true, glassIntensity = 0.88f) {
                HeroCaption("Your level")
            }
        }
        composeRule.onNodeWithText("Your level").assertIsDisplayed()
    }

    @Test
    fun loadingPrimaryButtonIsDisabledAndShowsItsLabel() {
        var clicks = 0
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                PrimaryButton("Opening...", onClick = { clicks++ }, loading = true)
            }
        }

        composeRule.onNodeWithText("Opening...")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, clicks)
    }
}
