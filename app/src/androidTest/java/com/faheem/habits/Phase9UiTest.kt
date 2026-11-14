package com.faheem.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faheem.habits.app.MainActivity
import com.faheem.habits.app.MomentumDestination
import com.faheem.habits.app.MomentumNavigationHost
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.MomentumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9UiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createHabitFlowIsReachable() {
        openModule(AppModule.Habits)
        rule.onNodeWithText("New habit", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Habit name").performTextInput("Read")
        rule.onNodeWithText("Save habit", useUnmergedTree = true).performScrollTo().performClick()
    }

    @Test
    fun completeHabitFlowExposesTodayControls() {
        openModule(AppModule.Habits)
        rule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun addExpenseFlowIsReachable() {
        openModule(AppModule.Money)
        rule.onNodeWithText("Add expense", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Amount").assertIsDisplayed()
    }

    @Test
    fun vaultModuleIsReachable() {
        openModule(AppModule.Vault)
        rule.onNodeWithText("Vault").assertIsDisplayed()
    }

    private fun openModule(module: AppModule) {
        rule.activity.runOnUiThread {
            rule.activity.setContentView(
                ComposeView(rule.activity).apply {
                    setContent {
                        MomentumTheme {
                            MomentumNavigationHost(
                                destination = MomentumDestination.Module(module),
                                onOpenModule = {},
                                onBack = {}
                            )
                        }
                    }
                }
            )
        }
    }
}
