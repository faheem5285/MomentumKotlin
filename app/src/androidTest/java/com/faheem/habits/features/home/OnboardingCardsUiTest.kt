package com.faheem.habits.features.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.faheem.habits.core.settings.OnboardingProgress
import com.faheem.habits.core.settings.OnboardingSignals
import com.faheem.habits.core.settings.OnboardingTask
import com.faheem.habits.core.settings.finish
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.MomentumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Device-side card behavior; compiled here, not executed without an emulator/device. */
@RunWith(AndroidJUnit4::class)
class OnboardingCardsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = 1_700_000_000_000L

    @Test
    fun showsOnlyTwoPrioritizedCards() {
        render(OnboardingProgress.Initial)
        rule.onNodeWithText("Welcome to Zylo").assertIsDisplayed()
        rule.onNodeWithText("Create your first habit").assertIsDisplayed()
        rule.onNodeWithText("Add your first task").assertIsNotDisplayed()
    }

    @Test
    fun primaryActionCompletesTaskAndOpensModule() {
        val done = mutableListOf<OnboardingTask>()
        val opened = mutableListOf<AppModule>()
        render(OnboardingProgress.Initial, onDone = done::add, onOpenModule = opened::add)
        rule.onNodeWithText("Explore your dashboard").performClick()
        assertEquals(listOf(OnboardingTask.WELCOME), done)
        assertEquals(listOf(AppModule.Settings), opened)
    }

    @Test
    fun secondaryActionsReportCorrectTask() {
        val snoozed = mutableListOf<OnboardingTask>()
        val dismissed = mutableListOf<OnboardingTask>()
        render(OnboardingProgress.Initial, onSnooze = snoozed::add, onDismiss = dismissed::add)
        rule.onAllNodesWithText("Remind me later")[0].performClick()
        rule.onAllNodesWithText("Don't show again")[0].performClick()
        assertEquals(listOf(OnboardingTask.WELCOME), snoozed)
        assertEquals(listOf(OnboardingTask.WELCOME), dismissed)
    }

    @Test
    fun signedOutCloudCardUsesExactCopy() {
        render(
            OnboardingProgress.Initial.copy(
                phase = com.faheem.habits.core.settings.OnboardingPhase.PENDING,
                tasks = mapOf(
                    OnboardingTask.WELCOME.id to com.faheem.habits.core.settings.OnboardingTaskState(done = true),
                    OnboardingTask.FIRST_HABIT.id to com.faheem.habits.core.settings.OnboardingTaskState(done = true),
                    OnboardingTask.FIRST_TASK.id to com.faheem.habits.core.settings.OnboardingTaskState(done = true),
                    OnboardingTask.FIRST_EXPENSE.id to com.faheem.habits.core.settings.OnboardingTaskState(done = true)
                )
            )
        )
        rule.onNodeWithText("Local data is still available on this device").assertIsDisplayed()
        rule.onNodeWithText(
            "No cloud backup — local data may not be recoverable if device lost/app data erased"
        ).assertIsDisplayed()
        rule.onNodeWithText(
            "Sync unavailable — latest changes may not yet be synchronized"
        ).assertIsDisplayed()
    }

    @Test
    fun doneProgressRendersNoCards() {
        render(OnboardingProgress.Initial.finish())
        rule.onNodeWithText("Welcome to Zylo").assertIsNotDisplayed()
    }

    private fun render(
        progress: OnboardingProgress,
        onDone: (OnboardingTask) -> Unit = {},
        onOpenModule: (AppModule) -> Unit = {},
        onSnooze: (OnboardingTask) -> Unit = {},
        onDismiss: (OnboardingTask) -> Unit = {},
        onFinish: () -> Unit = {},
        onOpenPermissions: () -> Unit = {}
    ) {
        rule.setContent {
            MomentumTheme(reduceEffects = true) {
                OnboardingCards(
                    progress = progress,
                    signals = OnboardingSignals(signedIn = false),
                    onOpenModule = onOpenModule,
                    onOpenPermissions = onOpenPermissions,
                    onDone = onDone,
                    onDismiss = onDismiss,
                    onSnooze = onSnooze,
                    onFinish = onFinish,
                    nowMillis = now
                )
            }
        }
    }
}
