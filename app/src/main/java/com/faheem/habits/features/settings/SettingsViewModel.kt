package com.faheem.habits.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.settings.MomentumSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MomentumSettingsRepository
) : ViewModel() {
    val permissionSetupSeen: StateFlow<Boolean> = repository.permissionSetupSeen.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    fun markPermissionSetupSeen() {
        viewModelScope.launch { repository.setPermissionSetupSeen(true) }
    }
}
