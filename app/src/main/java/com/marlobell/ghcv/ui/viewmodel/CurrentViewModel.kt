package com.marlobell.ghcv.ui.viewmodel

import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.data.model.BloodPressureMetric
import com.marlobell.ghcv.data.model.VitalStats
import com.marlobell.ghcv.ui.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

data class CurrentHealthData(
    val steps: Long = 0,
    val heartRate: Long? = null,
    val heartRateTimestamp: Instant? = null,
    val todayAvgHeartRate: Long? = null,
    val activeCalories: Double = 0.0,
    val sleepLastNight: Long? = null,
    val sevenDayAvgSteps: Long = 0,
    val sevenDayAvgSleep: Long = 0,
    val sevenDayAvgCalories: Double = 0.0,
    val bloodPressure: VitalStats<BloodPressureMetric> = VitalStats(),
    val bloodGlucose: VitalStats<Double> = VitalStats(),
    val bodyTemperature: VitalStats<Double> = VitalStats(),
    val oxygenSaturation: VitalStats<Double> = VitalStats(),
    val restingHeartRate: VitalStats<Long> = VitalStats(),
    val respiratoryRate: VitalStats<Double> = VitalStats(),
    val lastUpdated: Instant? = null
) {
    val stepsTrend: Int
        get() = if (sevenDayAvgSteps > 0) {
            ((steps - sevenDayAvgSteps).toDouble() / sevenDayAvgSteps * 100).toInt()
        } else 0
    
    val sleepTrend: Int
        get() = if (sleepLastNight != null && sevenDayAvgSleep > 0) {
            ((sleepLastNight - sevenDayAvgSleep).toDouble() / sevenDayAvgSleep * 100).toInt()
        } else 0
    
    val caloriesTrend: Int
        get() = if (sevenDayAvgCalories > 0) {
            ((activeCalories - sevenDayAvgCalories) / sevenDayAvgCalories * 100).toInt()
        } else 0
}

class CurrentViewModel(
    private val repository: HealthConnectRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _healthData = MutableStateFlow(CurrentHealthData())
    val healthData: StateFlow<CurrentHealthData> = _healthData.asStateFlow()
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Uninitialized)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    
    private val _hasBackgroundPermission = MutableStateFlow(false)
    val hasBackgroundPermission: StateFlow<Boolean> = _hasBackgroundPermission.asStateFlow()
    
    private var autoRefreshJob: Job? = null
    private var isInForeground = true

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            _hasPermissions.value = healthConnectManager.hasAllPermissions()
            _hasBackgroundPermission.value = healthConnectManager.hasBackgroundReadPermission()
            
            Log.d("CurrentViewModel", "Permissions: all=${_hasPermissions.value}, background=${_hasBackgroundPermission.value}")
            
            if (_hasPermissions.value) {
                loadCurrentData()
                startAutoRefresh()
            }
        }
    }

    fun onAppForegrounded() {
        Log.d("CurrentViewModel", "App foregrounded - resuming auto-refresh")
        isInForeground = true
        startAutoRefresh()
        loadCurrentData() // Refresh immediately when coming back to foreground
    }

    fun onAppBackgrounded() {
        Log.d("CurrentViewModel", "App backgrounded - pausing auto-refresh")
        isInForeground = false
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun startAutoRefresh() {
        if (!isInForeground && !_hasBackgroundPermission.value) {
            Log.d("CurrentViewModel", "Skipping auto-refresh start - app is backgrounded and no background permission")
            return
        }
        
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isInForeground || _hasBackgroundPermission.value) {
                delay(60_000) // 60 seconds
                if (isInForeground || _hasBackgroundPermission.value) {
                    loadCurrentData()
                }
            }
        }
    }

    /**
     * Wraps Health Connect operations with permission checks and comprehensive error handling.
     * Based on Google's official Health Connect sample pattern.
     */
    private suspend fun <T> tryWithPermissionsCheck(block: suspend () -> T): T? {
        return try {
            // Recheck permissions before each operation
            _hasPermissions.value = healthConnectManager.hasAllPermissions()
            if (!_hasPermissions.value) {
                Log.w("CurrentViewModel", "Permissions not granted, skipping operation")
                return null
            }
            block()
        } catch (e: RemoteException) {
            Log.e("CurrentViewModel", "RemoteException accessing Health Connect", e)
            _uiState.value = UiState.Error(e)
            null
        } catch (e: SecurityException) {
            Log.e("CurrentViewModel", "SecurityException - permission denied or rate limited", e)
            _uiState.value = UiState.Error(e)
            null
        } catch (e: IOException) {
            Log.e("CurrentViewModel", "IOException accessing Health Connect", e)
            _uiState.value = UiState.Error(e)
            null
        } catch (e: IllegalStateException) {
            Log.e("CurrentViewModel", "IllegalStateException - invalid state", e)
            _uiState.value = UiState.Error(e)
            null
        } catch (e: Exception) {
            Log.e("CurrentViewModel", "Unexpected error", e)
            _uiState.value = UiState.Error(e)
            null
        }
    }

    fun loadCurrentData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            tryWithPermissionsCheck {
                // Fetch main metrics with individual error handling
                val steps = try {
                    repository.getTodaySteps()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch steps", e)
                    0L
                }
                
                val heartRate = try {
                    repository.getLatestHeartRate()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch heart rate", e)
                    null
                }
                
                // Calculate today's average resting heart rate (or fall back to regular HR average)
                val todayAvgHeartRate = try {
                    val restingHRToday = repository.getTodayRestingHeartRate()
                    if (restingHRToday.isNotEmpty()) {
                        val avg = (restingHRToday.map { it.bpm.toDouble() }.average()).toLong()
                        Log.d("CurrentViewModel", "Resting HR avg from ${restingHRToday.size} samples: $avg bpm")
                        avg
                    } else {
                        // Fallback: use regular heart rate samples
                        Log.d("CurrentViewModel", "No resting HR data, falling back to regular HR samples")
                        val todayHeartRates = repository.getHeartRateForDate(LocalDate.now())
                        if (todayHeartRates.isNotEmpty()) {
                            (todayHeartRates.map { it.bpm.toDouble() }.average()).toLong()
                        } else null
                    }
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch today's average heart rate", e)
                    null
                }
                
                val calories = try {
                    repository.getTodayActiveCalories()
                } catch (e: Exception) {
                    0.0
                }
                
                // Calculate 7-day average steps
                val sevenDayAvgSteps = try {
                    val past7Days = (1..7).map { daysAgo ->
                        val date = LocalDate.now().minusDays(daysAgo.toLong())
                        try {
                            repository.getStepsForDate(date)
                        } catch (e: Exception) {
                            0L
                        }
                    }
                    val total = past7Days.sum()
                    if (total > 0) total / 7 else 0L
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day average steps", e)
                    0L
                }
                
                // 7-day average sleep
                val sevenDayAvgSleep = try {
                    val past7Days = (1..7).map { daysAgo ->
                        val date = LocalDate.now().minusDays(daysAgo.toLong())
                        try {
                            repository.getSleepForDate(date)?.durationMinutes ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                    }
                    val total = past7Days.sum()
                    if (total > 0) total / 7 else 0L
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day average sleep", e)
                    0L
                }
                
                // 7-day average active calories
                val sevenDayAvgCalories = try {
                    val past7Days = (1..7).map { daysAgo ->
                        val date = LocalDate.now().minusDays(daysAgo.toLong())
                        try {
                            repository.getActiveCaloriesForDate(date)
                        } catch (e: Exception) {
                            0.0
                        }
                    }
                    val total = past7Days.sum()
                    if (total > 0) total / 7 else 0.0
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day average calories", e)
                    0.0
                }
                
                // Sleep from last night (yesterday's date)
                val yesterday = LocalDate.now().minusDays(1)
                val sleepData = try {
                    repository.getSleepForDate(yesterday)
                } catch (e: Exception) {
                    null
                }

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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Blood pressure not available (permission/no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch blood pressure", e)
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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Blood glucose not available (permission/no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch blood glucose", e)
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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Body temperature not available (permission/no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch body temperature", e)
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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Oxygen saturation not available (permission/no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch oxygen saturation", e)
                    VitalStats()
                }
                
                // Note: These vitals may trigger rate limiting if queried too frequently
                // Using try-catch with proper exception handling
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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Resting HR unavailable (likely no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.d("CurrentViewModel", "Resting HR query failed (possibly rate limited): ${e.message}")
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
                } catch (e: SecurityException) {
                    Log.d("CurrentViewModel", "Respiratory rate unavailable (likely no data)")
                    VitalStats()
                } catch (e: Exception) {
                    Log.d("CurrentViewModel", "Respiratory rate query failed (possibly rate limited): ${e.message}")
                    VitalStats()
                }

                _healthData.value = CurrentHealthData(
                    steps = steps,
                    heartRate = heartRate?.bpm,
                    heartRateTimestamp = heartRate?.timestamp,
                    todayAvgHeartRate = todayAvgHeartRate,
                    activeCalories = calories,
                    sleepLastNight = sleepData?.durationMinutes,
                    sevenDayAvgSteps = sevenDayAvgSteps,
                    sevenDayAvgSleep = sevenDayAvgSleep,
                    sevenDayAvgCalories = sevenDayAvgCalories,
                    bloodPressure = bloodPressureStats,
                    bloodGlucose = bloodGlucoseStats,
                    bodyTemperature = bodyTemperatureStats,
                    oxygenSaturation = oxygenSaturationStats,
                    restingHeartRate = restingHeartRateStats,
                    respiratoryRate = respiratoryRateStats,
                    lastUpdated = Instant.now()
                )
                
                _uiState.value = UiState.Done
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
