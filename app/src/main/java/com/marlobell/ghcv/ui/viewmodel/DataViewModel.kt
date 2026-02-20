package com.marlobell.ghcv.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

data class DataUiState(
    val isLoading: Boolean = false,
    val jsonData: String = ""
)

class DataViewModel(
    private val repository: HealthConnectRepository,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()
    
    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()
    
    fun checkPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasPermissions.value = healthConnectManager.hasAllPermissions()
        }
    }
    
    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val endOfDay = Instant.now()
                
                val dumpJson = JSONObject()
                dumpJson.put("timestamp", Instant.now().toString())
                dumpJson.put("date", LocalDate.now().toString())
                dumpJson.put("timeRange", JSONObject().apply {
                    put("start", startOfDay.toString())
                    put("end", endOfDay.toString())
                })
                
                val healthConnectClient = healthConnectManager.getClient()
                
                // Steps
                try {
                    val stepsRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("steps", JSONArray().apply {
                        stepsRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("count", record.count)
                                put("startTime", record.startTime.toString())
                                put("endTime", record.endTime.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading steps", e)
                }
                
                // Heart Rate
                try {
                    val hrRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = HeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("heartRate", JSONArray().apply {
                        hrRecords.records.forEach { record ->
                            record.samples.forEach { sample ->
                                put(JSONObject().apply {
                                    put("bpm", sample.beatsPerMinute)
                                    put("time", sample.time.toString())
                                    put("source", record.metadata.dataOrigin.packageName)
                                })
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading heart rate", e)
                }
                
                // Active Calories
                try {
                    val caloriesRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = ActiveCaloriesBurnedRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("activeCalories", JSONArray().apply {
                        caloriesRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("calories", record.energy.inKilocalories)
                                put("startTime", record.startTime.toString())
                                put("endTime", record.endTime.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading calories", e)
                }
                
                // Sleep
                try {
                    val yesterday = LocalDate.now().minusDays(1)
                    val sleepStart = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val sleepEnd = LocalDate.now().atTime(14, 0).atZone(ZoneId.systemDefault()).toInstant()
                    
                    val sleepRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sleepStart, sleepEnd)
                        )
                    )
                    dumpJson.put("sleep", JSONArray().apply {
                        sleepRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("startTime", record.startTime.toString())
                                put("endTime", record.endTime.toString())
                                put("durationMinutes", java.time.Duration.between(record.startTime, record.endTime).toMinutes())
                                put("title", record.title ?: "")
                                put("notes", record.notes ?: "")
                                put("source", record.metadata.dataOrigin.packageName)
                                put("stages", JSONArray().apply {
                                    record.stages.forEach { stage ->
                                        put(JSONObject().apply {
                                            put("stage", stage.stage.toString())
                                            put("startTime", stage.startTime.toString())
                                            put("endTime", stage.endTime.toString())
                                        })
                                    }
                                })
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading sleep", e)
                }
                
                // Blood Pressure
                try {
                    val bpRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = BloodPressureRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("bloodPressure", JSONArray().apply {
                        bpRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("systolic", record.systolic.inMillimetersOfMercury)
                                put("diastolic", record.diastolic.inMillimetersOfMercury)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading blood pressure", e)
                }
                
                // Resting Heart Rate
                try {
                    val restingHRRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = RestingHeartRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("restingHeartRate", JSONArray().apply {
                        restingHRRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("bpm", record.beatsPerMinute)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading resting heart rate", e)
                }
                
                // Oxygen Saturation
                try {
                    val o2Records = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = OxygenSaturationRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("oxygenSaturation", JSONArray().apply {
                        o2Records.records.forEach { record ->
                            put(JSONObject().apply {
                                put("percentage", record.percentage.value)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading oxygen saturation", e)
                }
                
                // Body Temperature
                try {
                    val tempRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = BodyTemperatureRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("bodyTemperature", JSONArray().apply {
                        tempRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("celsius", record.temperature.inCelsius)
                                put("fahrenheit", record.temperature.inFahrenheit)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading body temperature", e)
                }
                
                // Respiratory Rate
                try {
                    val respRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = RespiratoryRateRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("respiratoryRate", JSONArray().apply {
                        respRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("rate", record.rate)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading respiratory rate", e)
                }
                
                // Blood Glucose
                try {
                    val glucoseRecords = healthConnectClient.readRecords(
                        ReadRecordsRequest(
                            recordType = BloodGlucoseRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    dumpJson.put("bloodGlucose", JSONArray().apply {
                        glucoseRecords.records.forEach { record ->
                            put(JSONObject().apply {
                                put("level", record.level.inMillimolesPerLiter)
                                put("time", record.time.toString())
                                put("source", record.metadata.dataOrigin.packageName)
                            })
                        }
                    })
                } catch (e: Exception) {
                    Log.w("DataViewModel", "Error reading blood glucose", e)
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    jsonData = dumpJson.toString(2)
                )
                
            } catch (e: Exception) {
                Log.e("DataViewModel", "Error during data load", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    jsonData = "Error loading data: ${e.message}"
                )
            }
        }
    }
}
