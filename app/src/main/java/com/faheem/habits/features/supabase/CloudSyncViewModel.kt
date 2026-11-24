package com.faheem.habits.features.supabase

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.data.sync.SyncHistoryEvent
import com.faheem.habits.core.data.sync.SyncModule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Immutable
data class CloudSyncUiState(
    val signedIn: Boolean = false,
    val accountEmail: String? = null,
    val syncStates: List<com.faheem.habits.core.data.SyncState> = emptyList(),
    val workState: androidx.work.WorkInfo.State? = null,
    val workerError: String? = null,
    val moduleEnabled: Map<SyncModule, Boolean> = SyncModule.entries.associateWith { true },
    val history: List<SyncHistoryEvent> = emptyList()
)

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val repository: CloudSyncRepository
) : ViewModel() {
    val uiState: StateFlow<CloudSyncUiState> = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CloudSyncUiState()
    )

    fun setModuleEnabled(module: SyncModule, enabled: Boolean) {
        repository.setModuleEnabled(module, enabled)
        if (enabled) repository.enqueueSync()
    }

    fun syncNow() = repository.enqueueSync()
}
