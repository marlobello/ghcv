package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.model.BloodGlucoseMetric
import com.marlobell.ghcv.data.model.BloodPressureMetric
import com.marlobell.ghcv.data.model.BodyTemperatureMetric
import com.marlobell.ghcv.data.model.HeartRateMetric
import com.marlobell.ghcv.data.model.OxygenSaturationMetric
import com.marlobell.ghcv.data.model.RespiratoryRateMetric
import com.marlobell.ghcv.data.model.RestingHeartRateMetric
import com.marlobell.ghcv.data.model.SleepMetric
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
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
    val bloodPressureData: List<BloodPressureMetric> = emptyList(),
    val bloodGlucoseData: List<BloodGlucoseMetric> = emptyList(),
    val bodyTemperatureData: List<BodyTemperatureMetric> = emptyList(),
    val oxygenSaturationData: List<OxygenSaturationMetric> = emptyList(),
    val restingHeartRateData: List<RestingHeartRateMetric> = emptyList(),
    val respiratoryRateData: List<RespiratoryRateMetric> = emptyList(),
    val previousDaySteps: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val expandedSections: Set<String> = emptySet()
)

class HistoricalViewModel(
    private val repository: HealthConnectRepository,
    initialExpandedCard: String? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HistoricalHealthData(
            expandedSections = initialExpandedCard?.let { setOf(it) } ?: emptySet()
        )
    )
    val uiState: StateFlow<HistoricalHealthData> = _uiState.asStateFlow()

    init {
        loadDataForDate(LocalDate.now())
    }

    fun loadDataForDate(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
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
                val (sleep, _) = repository.getSleepForDate(date.minusDays(1))
                val distance = repository.getDistanceForDate(date)
                val activeCalories = repository.getActiveCaloriesForDate(date)
                val exercises = repository.getExerciseSessionsForDate(date)
                val bloodPressure = repository.getBloodPressureForDate(date)
                val bloodGlucose = repository.getBloodGlucoseForDate(date)
                val bodyTemperature = repository.getBodyTemperatureForDate(date)
                val oxygenSaturation = repository.getOxygenSaturationForDate(date)
                val restingHeartRate = repository.getRestingHeartRateForDate(date)
                val respiratoryRate = repository.getRespiratoryRateForDate(date)
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
                    bloodPressureData = bloodPressure,
                    bloodGlucoseData = bloodGlucose,
                    bodyTemperatureData = bodyTemperature,
                    oxygenSaturationData = oxygenSaturation,
                    restingHeartRateData = restingHeartRate,
                    respiratoryRateData = respiratoryRate,
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
