package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.model.HeartRateMetric
import com.marlobell.ghcv.data.model.SleepMetric
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HistoricalHealthData(
    val date: LocalDate = LocalDate.now(),
    val steps: Long = 0,
    val heartRateData: List<HeartRateMetric> = emptyList(),
    val averageHeartRate: Double? = null,
    val minHeartRate: Long? = null,
    val maxHeartRate: Long? = null,
    val sleepData: SleepMetric? = null,
    val sleepDurationMinutes: Long? = null,
    val activeCalories: Double = 0.0,
    val distance: Double = 0.0,
    val exerciseSessions: Int = 0,
    val previousDaySteps: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val expandedSections: Set<String> = emptySet()
)

class HistoricalViewModel(
    private val repository: HealthConnectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoricalHealthData())
    val uiState: StateFlow<HistoricalHealthData> = _uiState.asStateFlow()

    init {
        loadDataForDate(LocalDate.now())
    }

    fun loadDataForDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                date = date,
                isLoading = true,
                error = null
            )
            
            try {
                val steps = repository.getStepsForDate(date)
                val heartRateList = repository.getHeartRateForDate(date)
                val avgHeartRate = if (heartRateList.isNotEmpty()) {
                    heartRateList.map { it.bpm.toDouble() }.average()
                } else null
                val minHr = heartRateList.minOfOrNull { it.bpm }
                val maxHr = heartRateList.maxOfOrNull { it.bpm }
                // Sleep is associated with the previous night
                val sleep = repository.getSleepForDate(date.minusDays(1))
                val distance = repository.getDistanceForDate(date)
                val activeCalories = repository.getActiveCaloriesForDate(date)
                val exercises = repository.getExerciseSessionsForDate(date)
                val previousDay = repository.getStepsForDate(date.minusDays(1))

                _uiState.value = HistoricalHealthData(
                    date = date,
                    steps = steps,
                    heartRateData = heartRateList,
                    averageHeartRate = avgHeartRate,
                    minHeartRate = minHr,
                    maxHeartRate = maxHr,
                    sleepData = sleep,
                    sleepDurationMinutes = sleep?.durationMinutes,
                    activeCalories = activeCalories,
                    distance = distance,
                    exerciseSessions = exercises.size,
                    previousDaySteps = previousDay,
                    isLoading = false,
                    expandedSections = _uiState.value.expandedSections
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
    
    fun toggleSection(section: String) {
        val currentExpanded = _uiState.value.expandedSections
        _uiState.value = _uiState.value.copy(
            expandedSections = if (currentExpanded.contains(section)) {
                currentExpanded - section
            } else {
                currentExpanded + section
            }
        )
    }
}
