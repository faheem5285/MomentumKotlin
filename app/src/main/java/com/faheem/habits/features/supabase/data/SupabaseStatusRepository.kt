package com.faheem.habits.features.supabase.data

import com.faheem.habits.core.data.SyncState
import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.faheem.habits.core.data.SyncStateDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Read-only access to the local sync status shown by the Supabase screen.
 * Keeps Room details out of the UI layer.
 */
class SupabaseStatusRepository @Inject constructor(
    private val dao: SyncStateDao,
    @ApplicationContext context: Context
) {
    val syncStates: Flow<List<SyncState>> = dao.observeAll()
    val syncWorkInfos: Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("momentum-sync")
}
