package com.faheem.habits.core.notifications

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentumReminderReceiverTest {
    @Test
    fun bootCompletedStartsReminderRescheduling() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        MomentumReminderReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
