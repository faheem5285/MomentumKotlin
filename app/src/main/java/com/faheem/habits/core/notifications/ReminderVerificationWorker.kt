package com.faheem.habits.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderVerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        MomentumReminderScheduler.createChannels(applicationContext)
        MomentumReminderScheduler.rescheduleAllNow(applicationContext)
        Result.success()
    }.getOrElse { Result.retry() }
}
