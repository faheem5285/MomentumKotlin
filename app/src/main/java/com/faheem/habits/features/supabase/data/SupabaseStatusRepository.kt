package com.faheem.habits.features.supabase.data

import com.faheem.habits.core.data.SyncState
import com.faheem.habits.core.data.SyncStateDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Read-only access to the local sync status shown by the Supabase screen.
 * Keeps Room details out of the UI layer.
 */
class SupabaseStatusRepository @Inject constructor(
    private val dao: SyncStateDao
) {
    val syncStates: Flow<List<SyncState>> = dao.observeAll()
}
