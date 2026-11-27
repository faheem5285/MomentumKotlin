package com.faheem.habits.features.screen_time

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UsageLimitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alarmMinutes = applicationContext
            .getSharedPreferences(SCREEN_TIME_PREFS, Context.MODE_PRIVATE)
            .getInt(PREF_SCREEN_ALARM, 0)
        if (!shouldScheduleUsageLimitWork(hasUsageAccess(applicationContext), alarmMinutes)) {
            return Result.success()
        }
        checkScreenTimeAlarm(applicationContext)
        return Result.success()
    }
}
