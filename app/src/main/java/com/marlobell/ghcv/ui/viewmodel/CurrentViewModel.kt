package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class CurrentHealthData(
    val steps: Long = 0,
    val heartRate: Long? = null,
    val heartRateTimestamp: Instant? = null,
    val activeCalories: Double = 0.0,
    val sleepLastNight: Long? = null,
    val yesterdaySteps: Long = 0,
    val lastUpdated: Instant? = null,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val stepsTrend: Int
        get() = if (yesterdaySteps > 0) {
            ((steps - yesterdaySteps).toDouble() / yesterdaySteps * 100).toInt()
        } else 0
}

class CurrentViewModel(
    private val repository: HealthConnectRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrentHealthData())
    val uiState: StateFlow<CurrentHealthData> = _uiState.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    
    private var autoRefreshJob: Job? = null

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            _hasPermissions.value = healthConnectManager.hasAllPermissions()
            if (_hasPermissions.value) {
                loadCurrentData()
                startAutoRefresh()
            }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(60_000) // 60 seconds
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
                
                // Get yesterday's steps for comparison
                val yesterday = LocalDate.now().minusDays(1)
                val yesterdaySteps = repository.getStepsForDate(yesterday)
                
                // Get sleep from last night
                val sleepData = repository.getSleepForDate(LocalDate.now())

                _uiState.value = CurrentHealthData(
                    steps = steps,
                    heartRate = heartRate?.bpm,
                    heartRateTimestamp = heartRate?.timestamp,
                    activeCalories = calories,
                    sleepLastNight = sleepData?.durationMinutes,
                    yesterdaySteps = yesterdaySteps,
                    lastUpdated = Instant.now(),
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
    
    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
