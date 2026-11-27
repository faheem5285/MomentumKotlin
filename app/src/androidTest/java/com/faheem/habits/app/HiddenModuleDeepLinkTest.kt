package com.faheem.habits.app

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faheem.habits.core.ui.AppModule
import com.faheem.habits.core.ui.MomentumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side guard checks for module destinations, including deep-link sources.
 * These tests are compiled but not executed in this workspace (no device/emulator run).
 */
@RunWith(AndroidJUnit4::class)
class HiddenModuleDeepLinkTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun hiddenModuleDestinationFallsBackHome() {
        val module = MomentumRoutes.parseModule("habits") ?: error("habits must be a known module")
        render(
            destination = MomentumDestination.Module(module),
            isModuleVisible = { false },
            entrySource = ModuleEntrySource.DEEP_LINK
        )
        rule.onNodeWithText("Your day,").assertIsDisplayed()
    }

    @Test
    fun hiddenNotificationTargetFallsBackHome() {
        render(
            destination = MomentumDestination.Module(AppModule.Vault),
            isModuleVisible = { false },
            entrySource = ModuleEntrySource.NOTIFICATION
        )
        rule.onNodeWithText("Your day,").assertIsDisplayed()
    }

    private fun render(
        destination: MomentumDestination,
        isModuleVisible: (AppModule) -> Boolean,
        entrySource: ModuleEntrySource
    ) {
        rule.activity.runOnUiThread {
            rule.activity.setContentView(
                ComposeView(rule.activity).apply {
                    setContent {
                        MomentumTheme {
                            MomentumNavigationHost(
                                destination = destination,
                                onOpenModule = {},
                                onBack = {},
                                isModuleVisible = isModuleVisible,
                                entrySource = entrySource
                            )
                        }
                    }
                }
            )
        }
        rule.waitForIdle()
    }
}
