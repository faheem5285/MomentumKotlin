package com.faheem.habits.core.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException

class SupabaseSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            val result = SupabaseSyncRepository(applicationContext).sync()
            if (result.projectWipeInProgress) {
                failureResult(IllegalStateException("Project cleanup is in progress; cloud sync is paused."))
            } else {
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.retry()
        } catch (_: HttpRequestTimeoutException) {
            Result.retry()
        } catch (failure: com.faheem.habits.core.data.supabase.SupabaseRequestException) {
            if (isRetryableSyncStatus(failure.statusCode)) Result.retry() else failureResult(failure)
        } catch (cause: Exception) {
            failureResult(cause)
        }

    private fun failureResult(cause: Exception): Result =
        Result.failure(Data.Builder().putString("error", cause.message.orEmpty()).build())
}
