package com.marlobell.ghcv.ui.viewmodel

import android.os.RemoteException
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.ChangesTokenStorage
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.model.ChangesMessage
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.data.model.BloodPressureMetric
import com.marlobell.ghcv.data.model.VitalStats
import com.marlobell.ghcv.ui.UiState
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.records.*
import com.marlobell.ghcv.ui.model.MetricInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass

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
    // 7-day averages for vitals comparisons
    val sevenDayAvgBloodPressureSystolic: Double? = null,
    val sevenDayAvgBloodPressureDiastolic: Double? = null,
    val sevenDayAvgBloodGlucose: Double? = null,
    val sevenDayAvgBodyTemperature: Double? = null,
    val sevenDayAvgOxygenSaturation: Double? = null,
    val sevenDayAvgRestingHeartRate: Long? = null,
    val sevenDayAvgRespiratoryRate: Double? = null,
    val bloodPressure: VitalStats<BloodPressureMetric> = VitalStats(),
    val bloodGlucose: VitalStats<Double> = VitalStats(),
    val bodyTemperature: VitalStats<Double> = VitalStats(),
    val oxygenSaturation: VitalStats<Double> = VitalStats(),
    val restingHeartRate: VitalStats<Long> = VitalStats(),
    val respiratoryRate: VitalStats<Double> = VitalStats(),
    val lastUpdated: Instant? = null,
    // Categorized metrics for UI sections
    val metricsWithData: List<MetricInfo> = emptyList(),
    val metricsNoPermission: List<MetricInfo> = emptyList(),
    val metricsNoData: List<MetricInfo> = emptyList()
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
    
    // Changes API support
    private lateinit var changesTokenStorage: ChangesTokenStorage
    private var isInitialLoad = true
    
    // Record types we track for changes
    private val trackedRecordTypes: Set<KClass<out Record>> = setOf(
        StepsRecord::class,
        HeartRateRecord::class,
        SleepSessionRecord::class,
        ActiveCaloriesBurnedRecord::class,
        BloodPressureRecord::class,
        BloodGlucoseRecord::class,
        BodyTemperatureRecord::class,
        OxygenSaturationRecord::class,
        RestingHeartRateRecord::class,
        RespiratoryRateRecord::class
    )

    fun initialize(changesTokenStorage: ChangesTokenStorage) {
        this.changesTokenStorage = changesTokenStorage
    }

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
                    // Use differential sync for auto-refresh after initial load
                    if (::changesTokenStorage.isInitialized && !isInitialLoad) {
                        syncChanges()
                    } else {
                        loadCurrentData()
                    }
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

                // Fetch 7-day averages for vitals comparisons
                val sevenDayAvgBP = try {
                    repository.getSevenDayAverageBloodPressure()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg blood pressure", e)
                    null
                }
                
                val sevenDayAvgBloodGlucose = try {
                    repository.getSevenDayAverageBloodGlucose()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg blood glucose", e)
                    null
                }
                
                val sevenDayAvgBodyTemp = try {
                    repository.getSevenDayAverageBodyTemperature()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg body temperature", e)
                    null
                }
                
                val sevenDayAvgSpO2 = try {
                    repository.getSevenDayAverageOxygenSaturation()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg oxygen saturation", e)
                    null
                }
                
                val sevenDayAvgRestingHR = try {
                    repository.getSevenDayAverageRestingHeartRate()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg resting heart rate", e)
                    null
                }
                
                val sevenDayAvgRespRate = try {
                    repository.getSevenDayAverageRespiratoryRate()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch 7-day avg respiratory rate", e)
                    null
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
                    sevenDayAvgBloodPressureSystolic = sevenDayAvgBP?.first,
                    sevenDayAvgBloodPressureDiastolic = sevenDayAvgBP?.second,
                    sevenDayAvgBloodGlucose = sevenDayAvgBloodGlucose,
                    sevenDayAvgBodyTemperature = sevenDayAvgBodyTemp,
                    sevenDayAvgOxygenSaturation = sevenDayAvgSpO2,
                    sevenDayAvgRestingHeartRate = sevenDayAvgRestingHR,
                    sevenDayAvgRespiratoryRate = sevenDayAvgRespRate,
                    bloodPressure = bloodPressureStats,
                    bloodGlucose = bloodGlucoseStats,
                    bodyTemperature = bodyTemperatureStats,
                    oxygenSaturation = oxygenSaturationStats,
                    restingHeartRate = restingHeartRateStats,
                    respiratoryRate = respiratoryRateStats,
                    lastUpdated = Instant.now()
                ).let { data ->
                    // Categorize metrics for UI sections
                    categorizeMetrics(data)
                }
                
                // On initial load, get a changes token for future differential syncs
                if (isInitialLoad && ::changesTokenStorage.isInitialized) {
                    try {
                        val token = healthConnectManager.getChangesToken(trackedRecordTypes)
                        changesTokenStorage.saveToken("current_data", token)
                        Log.d("CurrentViewModel", "Stored initial changes token")
                        isInitialLoad = false
                    } catch (e: Exception) {
                        Log.w("CurrentViewModel", "Failed to get initial changes token", e)
                    }
                }
                
                _uiState.value = UiState.Done
            }
        }
    }

    /**
     * Performs differential sync using the Changes API.
     * Only fetches data that has changed since the last sync.
     * Falls back to full refresh if token is expired or unavailable.
     */
    private suspend fun syncChanges() {
        if (!::changesTokenStorage.isInitialized) {
            Log.w("CurrentViewModel", "ChangesTokenStorage not initialized, falling back to full refresh")
            loadCurrentData()
            return
        }

        val token = changesTokenStorage.getToken("current_data")
        if (token == null) {
            Log.d("CurrentViewModel", "No changes token available, performing full refresh")
            loadCurrentData()
            return
        }

        tryWithPermissionsCheck {
            Log.d("CurrentViewModel", "Starting differential sync with changes token")
            var hasChanges = false
            
            healthConnectManager.getChanges(token)
                .catch { e ->
                    when (e) {
                        is IOException -> {
                            // Token expired - clear it and do full refresh
                            Log.w("CurrentViewModel", "Changes token expired, clearing and doing full refresh")
                            changesTokenStorage.clearToken("current_data")
                            isInitialLoad = true
                            loadCurrentData()
                        }
                        else -> {
                            Log.e("CurrentViewModel", "Error getting changes", e)
                            _uiState.value = UiState.Error(e)
                        }
                    }
                }
                .collect { message ->
                    when (message) {
                        is ChangesMessage.ChangeList -> {
                            Log.d("CurrentViewModel", "Received ${message.changes.size} changes")
                            if (message.changes.isNotEmpty()) {
                                hasChanges = true
                                processChanges(message.changes)
                            }
                        }
                        is ChangesMessage.NoMoreChanges -> {
                            Log.d("CurrentViewModel", "No more changes, storing new token")
                            changesTokenStorage.saveToken("current_data", message.nextChangesToken)
                            
                            if (hasChanges) {
                                // If we got changes, refresh the affected data
                                loadCurrentData()
                            } else {
                                // No changes, just update timestamp
                                _healthData.value = _healthData.value.copy(lastUpdated = Instant.now())
                                Log.d("CurrentViewModel", "No changes detected, data is up to date")
                            }
                        }
                    }
                }
        }
    }

    /**
     * Processes a list of changes from Health Connect.
     * Logs change information for debugging.
     */
    private fun processChanges(changes: List<Change>) {
        Log.d("CurrentViewModel", "Processing ${changes.size} changes from Health Connect")
        
        // Log summary of changes
        changes.forEach { change ->
            Log.d("CurrentViewModel", "  Change detected: ${change::class.simpleName}")
        }
        
        // For now, any changes trigger a full refresh
        // In the future, we could be more granular and only refresh affected metrics
    }

    /**
     * Categorizes metrics into three lists based on availability.
     * Returns updated CurrentHealthData with categorized metrics.
     */
    private suspend fun categorizeMetrics(data: CurrentHealthData): CurrentHealthData {
        val metricsWithData = mutableListOf<com.marlobell.ghcv.ui.model.MetricInfo>()
        val metricsNoPermission = mutableListOf<com.marlobell.ghcv.ui.model.MetricInfo>()
        val metricsNoData = mutableListOf<com.marlobell.ghcv.ui.model.MetricInfo>()
        
        // Check permissions for each metric type
        val permissionStatus = healthConnectManager.getPermissionsStatus(setOf(
            StepsRecord::class,
            HeartRateRecord::class,
            SleepSessionRecord::class,
            ActiveCaloriesBurnedRecord::class,
            BloodPressureRecord::class,
            BloodGlucoseRecord::class,
            BodyTemperatureRecord::class,
            OxygenSaturationRecord::class,
            RestingHeartRateRecord::class,
            RespiratoryRateRecord::class
        ))
        
        // Helper function to get icon for metric type
        fun getIconForMetric(id: String): androidx.compose.ui.graphics.vector.ImageVector {
            return when (id) {
                "steps" -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsWalk
                "heart_rate" -> androidx.compose.material.icons.Icons.Filled.Favorite
                "sleep" -> androidx.compose.material.icons.Icons.Filled.Bedtime
                "active_calories" -> androidx.compose.material.icons.Icons.Filled.LocalFireDepartment
                "blood_pressure" -> androidx.compose.material.icons.Icons.Filled.Favorite
                "blood_glucose" -> androidx.compose.material.icons.Icons.Filled.Bloodtype
                "body_temperature" -> androidx.compose.material.icons.Icons.Filled.Thermostat
                "oxygen_saturation" -> androidx.compose.material.icons.Icons.Filled.Air
                "resting_heart_rate" -> androidx.compose.material.icons.Icons.Filled.MonitorHeart
                "respiratory_rate" -> androidx.compose.material.icons.Icons.Filled.Air
                else -> androidx.compose.material.icons.Icons.Filled.HealthAndSafety
            }
        }
        
        // Categorize Steps
        val hasStepsPermission = permissionStatus[StepsRecord::class] ?: false
        if (hasStepsPermission) {
            if (data.steps > 0) {
                // Has data - will be shown in section 1 (full card already exists)
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "steps",
                    displayName = "Steps",
                    icon = getIconForMetric("steps"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "steps",
                displayName = "Steps",
                icon = getIconForMetric("steps"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Heart Rate
        val hasHeartRatePermission = permissionStatus[HeartRateRecord::class] ?: false
        if (hasHeartRatePermission) {
            if (data.heartRate != null) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "heart_rate",
                    displayName = "Heart Rate",
                    icon = getIconForMetric("heart_rate"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "heart_rate",
                displayName = "Heart Rate",
                icon = getIconForMetric("heart_rate"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Sleep
        val hasSleepPermission = permissionStatus[SleepSessionRecord::class] ?: false
        if (hasSleepPermission) {
            if (data.sleepLastNight != null) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "sleep",
                    displayName = "Sleep",
                    icon = getIconForMetric("sleep"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "sleep",
                displayName = "Sleep",
                icon = getIconForMetric("sleep"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Active Calories
        val hasCaloriesPermission = permissionStatus[ActiveCaloriesBurnedRecord::class] ?: false
        if (hasCaloriesPermission) {
            if (data.activeCalories > 0) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "active_calories",
                    displayName = "Active Calories",
                    icon = getIconForMetric("active_calories"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "active_calories",
                displayName = "Active Calories",
                icon = getIconForMetric("active_calories"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Blood Pressure
        val hasBloodPressurePermission = permissionStatus[BloodPressureRecord::class] ?: false
        if (hasBloodPressurePermission) {
            if (data.bloodPressure.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "blood_pressure",
                    displayName = "Blood Pressure",
                    icon = getIconForMetric("blood_pressure"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "blood_pressure",
                displayName = "Blood Pressure",
                icon = getIconForMetric("blood_pressure"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Blood Glucose
        val hasBloodGlucosePermission = permissionStatus[BloodGlucoseRecord::class] ?: false
        if (hasBloodGlucosePermission) {
            if (data.bloodGlucose.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "blood_glucose",
                    displayName = "Blood Glucose",
                    icon = getIconForMetric("blood_glucose"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "blood_glucose",
                displayName = "Blood Glucose",
                icon = getIconForMetric("blood_glucose"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Body Temperature
        val hasBodyTempPermission = permissionStatus[BodyTemperatureRecord::class] ?: false
        if (hasBodyTempPermission) {
            if (data.bodyTemperature.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "body_temperature",
                    displayName = "Body Temperature",
                    icon = getIconForMetric("body_temperature"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "body_temperature",
                displayName = "Body Temperature",
                icon = getIconForMetric("body_temperature"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Oxygen Saturation
        val hasOxygenSaturationPermission = permissionStatus[OxygenSaturationRecord::class] ?: false
        if (hasOxygenSaturationPermission) {
            if (data.oxygenSaturation.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "oxygen_saturation",
                    displayName = "Oxygen Saturation",
                    icon = getIconForMetric("oxygen_saturation"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "oxygen_saturation",
                displayName = "Oxygen Saturation",
                icon = getIconForMetric("oxygen_saturation"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Resting Heart Rate
        val hasRestingHRPermission = permissionStatus[RestingHeartRateRecord::class] ?: false
        if (hasRestingHRPermission) {
            if (data.restingHeartRate.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "resting_heart_rate",
                    displayName = "Resting Heart Rate",
                    icon = getIconForMetric("resting_heart_rate"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "resting_heart_rate",
                displayName = "Resting Heart Rate",
                icon = getIconForMetric("resting_heart_rate"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        // Categorize Respiratory Rate
        val hasRespiratoryRatePermission = permissionStatus[RespiratoryRateRecord::class] ?: false
        if (hasRespiratoryRatePermission) {
            if (data.respiratoryRate.hasData) {
                // Has data
            } else {
                metricsNoData.add(com.marlobell.ghcv.ui.model.MetricInfo(
                    id = "respiratory_rate",
                    displayName = "Respiratory Rate",
                    icon = getIconForMetric("respiratory_rate"),
                    availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_DATA
                ))
            }
        } else {
            metricsNoPermission.add(com.marlobell.ghcv.ui.model.MetricInfo(
                id = "respiratory_rate",
                displayName = "Respiratory Rate",
                icon = getIconForMetric("respiratory_rate"),
                availability = com.marlobell.ghcv.ui.model.MetricAvailability.NO_PERMISSION
            ))
        }
        
        return data.copy(
            metricsWithData = metricsWithData,
            metricsNoPermission = metricsNoPermission,
            metricsNoData = metricsNoData
        )
    }

    fun refresh() {
        if (::changesTokenStorage.isInitialized && !isInitialLoad) {
            // Use differential sync for subsequent refreshes
            viewModelScope.launch {
                syncChanges()
            }
        } else {
            // First load or token storage not available
            loadCurrentData()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
