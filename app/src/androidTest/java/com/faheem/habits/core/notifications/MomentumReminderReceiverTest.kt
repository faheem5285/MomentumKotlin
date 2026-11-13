package com.faheem.habits.core.notifications

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumReminderReceiverTest {
    @Test
    fun systemScheduleChangeRestoresReminderAlarms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MomentumReminderScheduler.onSystemScheduleChanged(context)
    }
}
