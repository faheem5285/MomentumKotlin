package com.faheem.habits.features.supabase

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.faheem.habits.core.data.AppDatabase
import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.supabase.SupabaseAuthApi
import com.faheem.habits.core.data.sync.SyncHistoryEvent
import com.faheem.habits.core.data.sync.SyncHistoryStore
import com.faheem.habits.core.data.sync.SyncModule
import com.faheem.habits.core.data.sync.SyncModuleSettings
import com.faheem.habits.core.data.sync.SupabaseSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CloudSyncSnapshot(
    val signedIn: Boolean,
    val accountEmail: String?,
    val syncStates: List<SyncState>,
    val workState: WorkInfo.State?,
    val workerError: String?,
    val moduleEnabled: Map<SyncModule, Boolean>,
    val history: List<SyncHistoryEvent>
)

@Singleton
class CloudSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val database = AppDatabase.get(context.applicationContext)
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun observe(): Flow<CloudSyncSnapshot> = combine(
        database.syncStateDao().observeAll(),
        workManager.getWorkInfosForUniqueWorkFlow("momentum-sync"),
        SyncModuleSettings.observe(context),
        SyncHistoryStore.observe(context)
    ) { states, workInfos, modules, history ->
        val state = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.state
            ?: workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }?.state
            ?: workInfos.lastOrNull()?.state
        val workerError = workInfos.lastOrNull { it.state == WorkInfo.State.FAILED }
            ?.takeIf { state == WorkInfo.State.FAILED }
            ?.outputData?.getString("error")
        val account = context.getSharedPreferences("account_profile", Context.MODE_PRIVATE)
        val id = SupabaseAuthApi.currentUserId(context)
        CloudSyncSnapshot(
            signedIn = !id.isNullOrBlank(),
            accountEmail = account.getString("email", null),
            syncStates = states,
            workState = state,
            workerError = workerError,
            moduleEnabled = modules,
            history = history
        )
    }

    fun setModuleEnabled(module: SyncModule, enabled: Boolean) =
        SyncModuleSettings.setEnabled(context, module, enabled)

    fun enqueueSync() = SupabaseSyncScheduler.enqueue(context)
}
