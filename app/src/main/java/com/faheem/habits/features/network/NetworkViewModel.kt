package com.faheem.habits.features.network

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class NetworkUiState(
    val connection: NetworkConnectionState = NetworkConnectionState(),
    val testing: Boolean = false,
    val testResult: NetworkTestResult? = null
)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val repository: NetworkRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(NetworkUiState())
    val uiState: StateFlow<NetworkUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeConnection().collect { connection ->
                mutableUiState.value = mutableUiState.value.copy(connection = connection)
            }
        }
    }

    fun testConnection() {
        if (mutableUiState.value.testing) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(testing = true, testResult = null)
            val result = runCatching { repository.testConnection() }
                .getOrElse { NetworkTestResult(false, "The connection test could not be completed.") }
            mutableUiState.value = mutableUiState.value.copy(testing = false, testResult = result)
        }
    }
}
