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
    val stepsSource: String? = null,
    val heartRate: Long? = null,
    val heartRateSource: String? = null,
    val heartRateTimestamp: Instant? = null,
    val todayAvgHeartRate: Long? = null,
    val activeCalories: Double = 0.0,
    val activeCaloriesSource: String? = null,
    val sleepLastNight: Long? = null,
    val sleepSource: String? = null,
    val distance: Double = 0.0,
    val distanceSource: String? = null,
    val exerciseSessions: Int = 0,
    val exerciseSource: String? = null,
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
    val bloodPressureSource: String? = null,
    val bloodGlucose: VitalStats<Double> = VitalStats(),
    val bloodGlucoseSource: String? = null,
    val bodyTemperature: VitalStats<Double> = VitalStats(),
    val bodyTemperatureSource: String? = null,
    val oxygenSaturation: VitalStats<Double> = VitalStats(),
    val oxygenSaturationSource: String? = null,
    val restingHeartRate: VitalStats<Long> = VitalStats(),
    val restingHeartRateSource: String? = null,
    val respiratoryRate: VitalStats<Double> = VitalStats(),
    val respiratoryRateSource: String? = null,
    val lastUpdated: Instant? = null,
    // Categorized metrics for UI sections
    val metricsWithData: List<MetricInfo> = emptyList(),
    val metricsNoPermission: List<MetricInfo> = emptyList(),
    val metricsNoData: List<MetricInfo> = emptyList(),
    // Pre-computed comparisons for UI
    val stepsComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val heartRateComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val sleepComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val caloriesComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val bloodPressureSystolicComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val bloodPressureDiastolicComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val bloodGlucoseComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val bodyTemperatureComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val oxygenSaturationComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val restingHeartRateComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    val respiratoryRateComparison: com.marlobell.ghcv.ui.model.MetricComparison? = null
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
                delay(300_000) // 5 minutes
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
                // Fetch main metrics with individual error handling and data sources
                val (steps, stepsSource) = try {
                    repository.getTodaySteps()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch steps", e)
                    Pair(0L, null)
                }
                
                val (heartRate, heartRateSource) = try {
                    repository.getLatestHeartRate()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch heart rate", e)
                    Pair(null, null)
                }
                
                // Calculate today's average resting heart rate (or fall back to regular HR average)
                val todayAvgHeartRate = try {
                    val (restingHRToday, _) = repository.getTodayRestingHeartRate()
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
                
                val (calories, caloriesSource) = try {
                    repository.getTodayActiveCalories()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch active calories", e)
                    Pair(0.0, null)
                }
                
                val distance = try {
                    repository.getTodayDistance()
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch distance", e)
                    0.0
                }
                
                val exerciseSessions = try {
                    repository.getTodayExerciseSessions().size
                } catch (e: Exception) {
                    Log.w("CurrentViewModel", "Failed to fetch exercise sessions", e)
                    0
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
                    val sevenDaysAgo = LocalDate.now().minusDays(7).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                    val now = Instant.now()
                    repository.getAverageSleepDuration(sevenDaysAgo, now)
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
                val (sleepData, sleepSource) = try {
                    repository.getSleepForDate(yesterday)
                } catch (e: Exception) {
                    Pair(null, null)
                }

                // Fetch all vitals with individual error handling
                val bloodPressureStats = buildVitalStats(
                    latestMetric = repository.getLatestBloodPressure(),
                    todayReadings = repository.getTodayBloodPressure(),
                    valueExtractor = { it.systolic },
                    latestValueExtractor = { it },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Blood pressure"
                )
                
                val bloodGlucoseStats = buildVitalStats(
                    latestMetric = repository.getLatestBloodGlucose(),
                    todayReadings = repository.getTodayBloodGlucose(),
                    valueExtractor = { it.mgDl },
                    latestValueExtractor = { it.mgDl },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Blood glucose"
                )
                
                val bodyTemperatureStats = buildVitalStats(
                    latestMetric = repository.getLatestBodyTemperature(),
                    todayReadings = repository.getTodayBodyTemperature(),
                    valueExtractor = { it.celsius },
                    latestValueExtractor = { it.celsius },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Body temperature"
                )
                
                val oxygenSaturationStats = buildVitalStats(
                    latestMetric = repository.getLatestOxygenSaturation(),
                    todayReadings = repository.getTodayOxygenSaturation(),
                    valueExtractor = { it.percentage },
                    latestValueExtractor = { it.percentage },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Oxygen saturation"
                )
                
                // Note: These vitals may trigger rate limiting if queried too frequently
                // Using try-catch with proper exception handling
                val (restingHeartRateList, restingHeartRateSource) = repository.getTodayRestingHeartRate()
                val restingHeartRateStats = buildVitalStats(
                    latestMetric = repository.getLatestRestingHeartRate(),
                    todayReadings = restingHeartRateList,
                    valueExtractor = { it.bpm.toDouble() },
                    latestValueExtractor = { it.bpm },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Resting heart rate"
                )
                
                val respiratoryRateStats = buildVitalStats(
                    latestMetric = repository.getLatestRespiratoryRate(),
                    todayReadings = repository.getTodayRespiratoryRate(),
                    valueExtractor = { it.breathsPerMinute },
                    latestValueExtractor = { it.breathsPerMinute },
                    latestTimestampExtractor = { it.timestamp },
                    metricName = "Respiratory rate"
                )

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

                // Create comparisons for UI
                val stepsComp = createComparison(
                    current = steps.toDouble(),
                    comparison = sevenDayAvgSteps.toDouble(),
                    label = "7-day avg",
                    unit = "steps",
                    higherIsBetter = true,
                    formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                )
                
                val heartRateComp = heartRate?.bpm?.let { currentHR ->
                    sevenDayAvgRestingHR?.let { avgRestingHR ->
                        createComparison(
                            current = currentHR.toDouble(),
                            comparison = avgRestingHR.toDouble(),
                            label = "7-day avg resting",
                            unit = "bpm",
                            higherIsBetter = false,  // Lower delta from resting is better
                            formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                        )
                    }
                }
                
                val sleepComp = sleepData?.durationMinutes?.let { currentSleep ->
                    createComparison(
                        current = currentSleep.toDouble(),
                        comparison = sevenDayAvgSleep.toDouble(),
                        label = "7-day avg",
                        unit = "min",
                        higherIsBetter = true,
                        formatValue = { minutes ->
                            val hrs = (minutes / 60).toInt()
                            val mins = (minutes % 60).toInt()
                            "${hrs}h ${mins}m"
                        }
                    )
                }
                
                val caloriesComp = createComparison(
                    current = calories,
                    comparison = sevenDayAvgCalories,
                    label = "7-day avg",
                    unit = "kcal",
                    higherIsBetter = true,
                    formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                )
                
                // Vital comparisons
                val bpSystolicComp = bloodPressureStats.latest?.let { bp ->
                    createVitalComparison(
                        current = bp.systolic,
                        sevenDayAvg = sevenDayAvgBP?.first,
                        unit = "mmHg",
                        formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                    )
                }
                
                val bpDiastolicComp = bloodPressureStats.latest?.let { bp ->
                    createVitalComparison(
                        current = bp.diastolic,
                        sevenDayAvg = sevenDayAvgBP?.second,
                        unit = "mmHg",
                        formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                    )
                }
                
                val glucoseComp = bloodGlucoseStats.latest?.let { glucose ->
                    createVitalComparison(
                        current = glucose,
                        sevenDayAvg = sevenDayAvgBloodGlucose,
                        unit = "mg/dL",
                        formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                    )
                }
                
                val bodyTempComp = bodyTemperatureStats.latest?.let { temp ->
                    createVitalComparison(
                        current = temp,
                        sevenDayAvg = sevenDayAvgBodyTemp,
                        unit = "°C"
                    )
                }
                
                val spo2Comp = oxygenSaturationStats.latest?.let { spo2 ->
                    createVitalComparison(
                        current = spo2,
                        sevenDayAvg = sevenDayAvgSpO2,
                        unit = "%",
                        formatValue = { String.format(java.util.Locale.US, "%.1f", it) }
                    )
                }
                
                val restingHRComp = restingHeartRateStats.latest?.let { rhr ->
                    createVitalComparison(
                        current = rhr.toDouble(),
                        sevenDayAvg = sevenDayAvgRestingHR?.toDouble(),
                        unit = "bpm",
                        formatValue = { String.format(java.util.Locale.US, "%.0f", it) }
                    )
                }
                
                val respRateComp = respiratoryRateStats.latest?.let { rr ->
                    createVitalComparison(
                        current = rr,
                        sevenDayAvg = sevenDayAvgRespRate,
                        unit = "br/min"
                    )
                }

                _healthData.value = CurrentHealthData(
                    steps = steps,
                    stepsSource = stepsSource,
                    heartRate = heartRate?.bpm,
                    heartRateSource = heartRateSource,
                    heartRateTimestamp = heartRate?.timestamp,
                    todayAvgHeartRate = todayAvgHeartRate,
                    activeCalories = calories,
                    activeCaloriesSource = caloriesSource,
                    sleepLastNight = sleepData?.durationMinutes,
                    sleepSource = sleepSource,
                    distance = distance,
                    exerciseSessions = exerciseSessions,
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
                    restingHeartRateSource = restingHeartRateSource,
                    respiratoryRate = respiratoryRateStats,
                    lastUpdated = Instant.now(),
                    // Comparisons
                    stepsComparison = stepsComp,
                    heartRateComparison = heartRateComp,
                    sleepComparison = sleepComp,
                    caloriesComparison = caloriesComp,
                    bloodPressureSystolicComparison = bpSystolicComp,
                    bloodPressureDiastolicComparison = bpDiastolicComp,
                    bloodGlucoseComparison = glucoseComp,
                    bodyTemperatureComparison = bodyTempComp,
                    oxygenSaturationComparison = spo2Comp,
                    restingHeartRateComparison = restingHRComp,
                    respiratoryRateComparison = respRateComp
                )
                
                // Categorize metrics for UI sections
                categorizeMetrics(_healthData.value)
                
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
     * Helper function to construct VitalStats with consistent error handling.
     * Extracts the duplicate pattern used for fetching and processing vital statistics.
     *
     * @param latestMetric The latest metric reading object
     * @param todayReadings List of today's metric readings
     * @param valueExtractor Function to extract numeric values from individual readings for statistics
     * @param latestValueExtractor Function to extract the final value from the latest metric
     * @param metricName Name of the metric for logging purposes
     * @return VitalStats object with calculated daily statistics or empty stats on error
     */
    private fun <T, U, V> buildVitalStats(
        latestMetric: T?,
        todayReadings: List<U>,
        valueExtractor: (U) -> Double?,
        latestValueExtractor: (T) -> V?,
        latestTimestampExtractor: (T) -> Instant?,
        metricName: String
    ): VitalStats<V> {
        return try {
            val values = todayReadings.mapNotNull { valueExtractor(it) }
            
            VitalStats(
                latest = latestMetric?.let { latestValueExtractor(it) },
                latestTimestamp = latestMetric?.let { latestTimestampExtractor(it) },
                dailyAvg = if (values.isNotEmpty()) values.average() else null,
                dailyMin = if (values.isNotEmpty()) values.minOrNull() else null,
                dailyMax = if (values.isNotEmpty()) values.maxOrNull() else null,
                readingCount = todayReadings.size
            )
        } catch (e: SecurityException) {
            Log.d("CurrentViewModel", "$metricName not available (permission/no data)")
            VitalStats()
        } catch (e: Exception) {
            Log.w("CurrentViewModel", "Failed to fetch $metricName", e)
            VitalStats()
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
                        is SecurityException -> {
                            // App is in background without background permission
                            // This is expected behavior - silently skip sync until foreground
                            Log.d("CurrentViewModel", "App in background, skipping changes sync")
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

    /**
     * Creates a MetricComparison object for displaying contextual comparisons.
     * 
     * @param current Current value
     * @param comparison Comparison value (e.g., 7-day average)
     * @param label Label for the comparison (e.g., "7-day avg", "Resting HR")
     * @param unit Unit of measurement (e.g., "steps", "bpm")
     * @param higherIsBetter True if higher values are better (steps, calories), false if lower is better (heart rate delta)
     * @param formatValue Optional function to format the comparison value
     */
    fun createComparison(
        current: Double,
        comparison: Double?,
        label: String,
        unit: String,
        higherIsBetter: Boolean = true,
        formatValue: (Double) -> String = { String.format(java.util.Locale.US, "%.0f", it) }
    ): com.marlobell.ghcv.ui.model.MetricComparison? {
        if (comparison == null || comparison == 0.0) return null
        
        val difference = current - comparison
        val percentageValue = (difference / comparison * 100).toInt()
        
        // Determine if positive based on metric type
        val isPositive = when {
            kotlin.math.abs(percentageValue) < 5 -> null  // Neutral if within 5%
            higherIsBetter -> difference > 0  // For steps/calories, higher is better
            else -> difference < 0  // For HR delta, lower is better
        }
        
        return com.marlobell.ghcv.ui.model.MetricComparison(
            label = label,
            value = formatValue(comparison),
            unit = unit,
            difference = if (difference >= 0) "+${formatValue(kotlin.math.abs(difference))}" else "-${formatValue(kotlin.math.abs(difference))}",
            percentage = if (percentageValue >= 0) "+$percentageValue%" else "$percentageValue%",
            isPositive = isPositive
        )
    }

    /**
     * Creates a comparison for vitals using 7-day average.
     * Assumes neutral comparison (no inherent "better" direction).
     */
    fun createVitalComparison(
        current: Double,
        sevenDayAvg: Double?,
        unit: String,
        formatValue: (Double) -> String = { String.format(java.util.Locale.US, "%.1f", it) }
    ): com.marlobell.ghcv.ui.model.MetricComparison? {
        if (sevenDayAvg == null || sevenDayAvg == 0.0) return null
        
        val difference = current - sevenDayAvg
        val percentageValue = (difference / sevenDayAvg * 100).toInt()
        
        // For vitals, large deviations are concerning
        val isPositive = when {
            kotlin.math.abs(percentageValue) < 5 -> null  // Stable is good
            kotlin.math.abs(percentageValue) < 10 -> null  // Small variance is normal
            else -> false  // Large deviation is concerning
        }
        
        return com.marlobell.ghcv.ui.model.MetricComparison(
            label = "7-day avg",
            value = formatValue(sevenDayAvg),
            unit = unit,
            difference = if (difference >= 0) "+${formatValue(kotlin.math.abs(difference))}" else "-${formatValue(kotlin.math.abs(difference))}",
            percentage = if (percentageValue >= 0) "+$percentageValue%" else "$percentageValue%",
            isPositive = isPositive
        )
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
