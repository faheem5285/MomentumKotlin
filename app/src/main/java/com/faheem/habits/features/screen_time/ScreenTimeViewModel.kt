package com.faheem.habits.features.screen_time

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Immutable
data class ScreenTimeUiState(
    val loading: Boolean = true,
    val hasAccess: Boolean = false,
    val apps: List<AppUsage> = emptyList(),
    val dailyMinutes: List<Long> = emptyList(),
    val yesterdayMinutes: Long = 0L,
    val errorMessage: String? = null
)

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val repository: ScreenTimeRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ScreenTimeUiState())
    val uiState: StateFlow<ScreenTimeUiState> = mutableUiState.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh(days: Long) {
        refreshJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(loading = true, errorMessage = null)
        refreshJob = viewModelScope.launch {
            runCatching { repository.load(days) }
                .onSuccess { snapshot ->
                    mutableUiState.value = ScreenTimeUiState(
                        loading = false,
                        hasAccess = snapshot.hasAccess,
                        apps = snapshot.apps,
                        dailyMinutes = snapshot.dailyMinutes,
                        yesterdayMinutes = snapshot.yesterdayMinutes
                    )
                }
                .onFailure { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        loading = false,
                        errorMessage = error.message ?: "Could not load screen time."
                    )
                }
        }
    }
}
