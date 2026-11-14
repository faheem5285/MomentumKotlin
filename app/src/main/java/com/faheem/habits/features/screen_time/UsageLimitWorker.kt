package com.faheem.habits.features.screen_time

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UsageLimitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        checkScreenTimeAlarm(applicationContext)
        return Result.success()
    }
}
