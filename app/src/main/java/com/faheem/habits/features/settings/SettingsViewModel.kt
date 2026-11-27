package com.faheem.habits.features.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faheem.habits.core.settings.AppearancePreferences
import com.faheem.habits.core.settings.AppearanceValues
import com.faheem.habits.core.settings.MomentumSettingsRepository
import com.faheem.habits.core.settings.OnboardingProgress
import com.faheem.habits.core.settings.OnboardingProgressStore
import com.faheem.habits.core.settings.OnboardingTask
import com.faheem.habits.core.settings.VisibilityRecovery
import com.faheem.habits.core.ui.AppModule
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: MomentumSettingsRepository,
    private val onboardingStore: OnboardingProgressStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val moduleNames = AppModule.entries.filterNot { it == AppModule.Settings }.map { it.name }

    val recoverableModuleNames: List<String> get() = moduleNames

    fun hiddenModuleNames(visibility: Map<String, Boolean>): List<String> =
        VisibilityRecovery.hiddenModules(visibility, moduleNames)

    val moduleVisibility: StateFlow<Map<String, Boolean>> = repository.moduleVisibility(moduleNames).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        moduleNames.associateWith { true }
    )

    val permissionSetupSeen: StateFlow<Boolean> = repository.permissionSetupSeen.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val accountChoiceSeen: StateFlow<Boolean> = repository.accountChoiceSeen.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val onboarding: StateFlow<OnboardingProgress> = onboardingStore.progress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OnboardingProgress.Initial
    )

    fun markPermissionSetupSeen() {
        viewModelScope.launch { repository.setPermissionSetupSeen(true) }
    }

    fun markAccountChoiceSeen() {
        viewModelScope.launch { repository.setAccountChoiceSeen(true) }
    }

    fun completeOnboardingTask(task: OnboardingTask) {
        viewModelScope.launch { onboardingStore.markDone(task, System.currentTimeMillis()) }
    }

    fun dismissOnboardingTask(task: OnboardingTask) {
        viewModelScope.launch { onboardingStore.dismiss(task, System.currentTimeMillis()) }
    }

    fun snoozeOnboardingTask(task: OnboardingTask, untilMillis: Long) {
        viewModelScope.launch { onboardingStore.snooze(task, untilMillis) }
    }

    fun finishOnboarding() {
        viewModelScope.launch { onboardingStore.finish() }
    }

    fun revisitOnboarding() {
        viewModelScope.launch { onboardingStore.revisit() }
    }

    fun setModuleVisible(moduleName: String, visible: Boolean) {
        viewModelScope.launch { repository.setModuleVisible(moduleName, visible) }
    }

    fun showAllModules() {
        viewModelScope.launch { repository.showAllModules(moduleNames) }
    }

    fun setAppearance(values: AppearanceValues) {
        viewModelScope.launch {
            repository.setAppearance(values)
            AppearancePreferences.apply(context, values)
        }
    }

    suspend fun syncAppearance() {
        AppearancePreferences.synchronize(context, repository)
    }

    suspend fun clearForProjectWipe() {
        repository.clearForProjectWipe()
    }
}
