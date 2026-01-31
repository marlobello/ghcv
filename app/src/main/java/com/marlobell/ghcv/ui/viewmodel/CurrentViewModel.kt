package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CurrentHealthData(
    val steps: Long = 0,
    val heartRate: Long? = null,
    val activeCalories: Double = 0.0,
    val isLoading: Boolean = true,
    val error: String? = null
)

class CurrentViewModel(
    private val repository: HealthConnectRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrentHealthData())
    val uiState: StateFlow<CurrentHealthData> = _uiState.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            _hasPermissions.value = healthConnectManager.hasAllPermissions()
            if (_hasPermissions.value) {
                loadCurrentData()
            }
        }
    }

    fun loadCurrentData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val steps = repository.getTodaySteps()
                val heartRate = repository.getLatestHeartRate()
                val calories = repository.getTodayActiveCalories()

                _uiState.value = CurrentHealthData(
                    steps = steps,
                    heartRate = heartRate?.bpm,
                    activeCalories = calories,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun refresh() {
        loadCurrentData()
    }
}
