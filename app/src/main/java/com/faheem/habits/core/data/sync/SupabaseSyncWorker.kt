package com.faheem.habits.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SupabaseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            SupabaseSyncRepository(applicationContext).sync()
            Result.success()
        } catch (_: java.io.IOException) {
            Result.retry()
        } catch (_: IllegalStateException) {
            Result.failure()
        }
}
