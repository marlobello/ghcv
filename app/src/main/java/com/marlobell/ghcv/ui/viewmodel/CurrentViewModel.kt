package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.data.model.*
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
    val bloodPressure: VitalStats<BloodPressureMetric> = VitalStats(),
    val bloodGlucose: VitalStats<Double> = VitalStats(),
    val bodyTemperature: VitalStats<Double> = VitalStats(),
    val oxygenSaturation: VitalStats<Double> = VitalStats(),
    val restingHeartRate: VitalStats<Long> = VitalStats(),
    val respiratoryRate: VitalStats<Double> = VitalStats(),
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
                
                val yesterday = LocalDate.now().minusDays(1)
                val yesterdaySteps = repository.getStepsForDate(yesterday)
                
                val sleepData = repository.getSleepForDate(LocalDate.now())

                // Fetch all vitals with individual error handling
                val bloodPressureStats = try {
                    val bpLatest = repository.getLatestBloodPressure()
                    val bpToday = repository.getTodayBloodPressure()
                    VitalStats(
                        latest = bpLatest,
                        latestTimestamp = bpLatest?.timestamp,
                        dailyAvg = if (bpToday.isNotEmpty()) bpToday.map { it.systolic }.average() else null,
                        dailyMin = bpToday.minOfOrNull { it.systolic },
                        dailyMax = bpToday.maxOfOrNull { it.systolic },
                        readingCount = bpToday.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }
                
                val bloodGlucoseStats = try {
                    val glucoseLatest = repository.getLatestBloodGlucose()
                    val glucoseToday = repository.getTodayBloodGlucose()
                    VitalStats(
                        latest = glucoseLatest?.mgDl,
                        latestTimestamp = glucoseLatest?.timestamp,
                        dailyAvg = if (glucoseToday.isNotEmpty()) glucoseToday.map { it.mgDl }.average() else null,
                        dailyMin = glucoseToday.minOfOrNull { it.mgDl },
                        dailyMax = glucoseToday.maxOfOrNull { it.mgDl },
                        readingCount = glucoseToday.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }
                
                val bodyTemperatureStats = try {
                    val tempLatest = repository.getLatestBodyTemperature()
                    val tempToday = repository.getTodayBodyTemperature()
                    VitalStats(
                        latest = tempLatest?.celsius,
                        latestTimestamp = tempLatest?.timestamp,
                        dailyAvg = if (tempToday.isNotEmpty()) tempToday.map { it.celsius }.average() else null,
                        dailyMin = tempToday.minOfOrNull { it.celsius },
                        dailyMax = tempToday.maxOfOrNull { it.celsius },
                        readingCount = tempToday.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }
                
                val oxygenSaturationStats = try {
                    val spo2Latest = repository.getLatestOxygenSaturation()
                    val spo2Today = repository.getTodayOxygenSaturation()
                    VitalStats(
                        latest = spo2Latest?.percentage,
                        latestTimestamp = spo2Latest?.timestamp,
                        dailyAvg = if (spo2Today.isNotEmpty()) spo2Today.map { it.percentage }.average() else null,
                        dailyMin = spo2Today.minOfOrNull { it.percentage },
                        dailyMax = spo2Today.maxOfOrNull { it.percentage },
                        readingCount = spo2Today.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }
                
                val restingHeartRateStats = try {
                    val restingHRLatest = repository.getLatestRestingHeartRate()
                    val restingHRToday = repository.getTodayRestingHeartRate()
                    VitalStats(
                        latest = restingHRLatest?.bpm,
                        latestTimestamp = restingHRLatest?.timestamp,
                        dailyAvg = if (restingHRToday.isNotEmpty()) restingHRToday.map { it.bpm.toDouble() }.average() else null,
                        dailyMin = restingHRToday.minOfOrNull { it.bpm.toDouble() },
                        dailyMax = restingHRToday.maxOfOrNull { it.bpm.toDouble() },
                        readingCount = restingHRToday.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }
                
                val respiratoryRateStats = try {
                    val respRateLatest = repository.getLatestRespiratoryRate()
                    val respRateToday = repository.getTodayRespiratoryRate()
                    VitalStats(
                        latest = respRateLatest?.breathsPerMinute,
                        latestTimestamp = respRateLatest?.timestamp,
                        dailyAvg = if (respRateToday.isNotEmpty()) respRateToday.map { it.breathsPerMinute }.average() else null,
                        dailyMin = respRateToday.minOfOrNull { it.breathsPerMinute },
                        dailyMax = respRateToday.maxOfOrNull { it.breathsPerMinute },
                        readingCount = respRateToday.size
                    )
                } catch (e: Exception) {
                    VitalStats()
                }

                _uiState.value = CurrentHealthData(
                    steps = steps,
                    heartRate = heartRate?.bpm,
                    heartRateTimestamp = heartRate?.timestamp,
                    activeCalories = calories,
                    sleepLastNight = sleepData?.durationMinutes,
                    yesterdaySteps = yesterdaySteps,
                    bloodPressure = bloodPressureStats,
                    bloodGlucose = bloodGlucoseStats,
                    bodyTemperature = bodyTemperatureStats,
                    oxygenSaturation = oxygenSaturationStats,
                    restingHeartRate = restingHeartRateStats,
                    respiratoryRate = respiratoryRateStats,
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
