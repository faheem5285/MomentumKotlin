package com.faheem.habits.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.features.settings.data.DatabaseHealthRepository
import kotlinx.coroutines.flow.flow
import com.faheem.habits.core.ui.AppModule
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MomentumSettingsRepository,
    private val databaseHealthRepository: DatabaseHealthRepository
) : ViewModel() {
    private val moduleNames = AppModule.entries.filterNot { it == AppModule.Settings }.map { it.name }

    val moduleVisibility: StateFlow<Map<String, Boolean>> = repository.moduleVisibility(moduleNames).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        moduleNames.associateWith { true }
    )

    val databaseVersion: StateFlow<Int> = flow {
        emit(databaseHealthRepository.databaseVersion())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    suspend fun checkDatabaseIntegrity(): String = databaseHealthRepository.checkIntegrity()

    val permissionSetupSeen: StateFlow<Boolean> = repository.permissionSetupSeen.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    fun markPermissionSetupSeen() {
        viewModelScope.launch { repository.setPermissionSetupSeen(true) }
    }

    fun setModuleVisible(moduleName: String, visible: Boolean) {
        viewModelScope.launch { repository.setModuleVisible(moduleName, visible) }
    }

    fun showAllModules() {
        viewModelScope.launch { repository.showAllModules(moduleNames) }
    }

    suspend fun clearForProjectWipe() {
        repository.clearForProjectWipe()
    }
}
