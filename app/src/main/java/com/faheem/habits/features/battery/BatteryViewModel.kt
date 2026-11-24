package com.faheem.habits.features.battery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class BatteryViewModel @Inject constructor(
    repository: BatteryRepository
) : ViewModel() {
    val uiState: StateFlow<BatteryDashboardSnapshot> = repository.observeDashboard().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BatteryDashboardSnapshot(
            reading = BatteryReading(0, false, "Reading battery…"),
            diagnostic = BatteryDiagnostic("Reading battery status", "Android battery data is loading.", 0L),
            sessions = emptyList(),
            levels = emptyList(),
            isLoading = true
        )
    )
}
