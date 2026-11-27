package com.faheem.habits.core.notifications

import android.app.NotificationManager
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.faheem.habits.core.ui.MomentumTheme
import com.faheem.habits.features.battery.BATTERY_LISTENER_COPY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side M10 checks. These are compiled but not executed without a device/emulator. */
@RunWith(AndroidJUnit4::class)
class M10DeviceFeaturesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun usageLimitChannelIsCreatedThroughTheSharedManager() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        ZyloNotifications.ensureChannels(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(ZyloNotifications.CHANNEL_USAGE_LIMITS)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    }

    @Test
    fun quietHoursAndRestartLimitationsAreShown() {
        composeRule.setContent {
            MomentumTheme(reduceEffects = true) {
                Text(QUIET_HOURS_COPY)
                Text(BATTERY_LISTENER_COPY)
                Text(monitorRestartCopy("Network monitoring"))
            }
        }

        composeRule.onNodeWithText(QUIET_HOURS_COPY).assertIsDisplayed()
        composeRule.onNodeWithText(BATTERY_LISTENER_COPY).assertIsDisplayed()
        composeRule.onNodeWithText(monitorRestartCopy("Network monitoring")).assertIsDisplayed()
    }
}
