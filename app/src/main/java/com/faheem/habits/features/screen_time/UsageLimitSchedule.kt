package com.faheem.habits.features.screen_time

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal const val USAGE_LIMIT_WORK_NAME = "usage-limit-monitor"
internal const val USAGE_LIMIT_POLL_MINUTES = 15L

/** Polls only while an alarm is configured and Usage access can supply the measurement. */
internal object UsageLimitSchedule {
    fun refresh(context: Context) {
        val alarmMinutes = context.getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE)
            .getInt(PREF_SCREEN_ALARM, 0)
        val workManager = WorkManager.getInstance(context)
        if (!shouldScheduleUsageLimitWork(hasUsageAccess(context), alarmMinutes)) {
            workManager.cancelUniqueWork(USAGE_LIMIT_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UsageLimitWorker>(
            USAGE_LIMIT_POLL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            USAGE_LIMIT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
