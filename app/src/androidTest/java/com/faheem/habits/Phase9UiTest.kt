package com.faheem.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faheem.habits.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9UiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createHabitFlowIsReachable() {
        openModule("Habits")
        rule.onNodeWithText("New habit").performClick()
        rule.onNodeWithText("Habit name").performTextInput("Read")
        rule.onNodeWithTag("button:Save habit").performClick()
    }

    @Test
    fun completeHabitFlowExposesTodayControls() {
        openModule("Habits")
        rule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun addExpenseFlowIsReachable() {
        openModule("Money")
        rule.onNodeWithText("Add expense").performClick()
        rule.onNodeWithText("Amount").assertIsDisplayed()
    }

    @Test
    fun unlockVaultFlowExposesPasswordControls() {
        openModule("Vault")
        rule.onNodeWithText("Master password").assertIsDisplayed()
        rule.onNodeWithTag("button:Create vault").assertIsDisplayed()
    }

    private fun openModule(label: String) {
        rule.onNodeWithText(label, useUnmergedTree = true).performClick()
    }
}
