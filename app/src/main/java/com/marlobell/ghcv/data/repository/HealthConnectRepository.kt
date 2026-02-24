package com.marlobell.ghcv.data.repository

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.marlobell.ghcv.data.exceptions.HealthConnectException
import com.marlobell.ghcv.data.model.BloodGlucoseMetric
import com.marlobell.ghcv.data.model.BloodPressureMetric
import com.marlobell.ghcv.data.model.BodyTemperatureMetric
import com.marlobell.ghcv.data.model.ExerciseSessionMetric
import com.marlobell.ghcv.data.model.HeartRateMetric
import com.marlobell.ghcv.data.model.OxygenSaturationMetric
import com.marlobell.ghcv.data.model.RespiratoryRateMetric
import com.marlobell.ghcv.data.model.RestingHeartRateMetric
import com.marlobell.ghcv.data.model.SleepMetric
import com.marlobell.ghcv.data.model.SleepStage

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectRepository(
    private val healthConnectClient: HealthConnectClient
) {

    companion object {
        // Preferred data sources in priority order
        private val PREFERRED_SOURCES = listOf(
            "com.ouraring.oura",              // Oura Ring
            "com.google.android.apps.fitness", // Google Fit
            "android"                          // Android system
        )
    }

    /**
     * Helper function to create TimeRangeFilter for today (start of day to now).
     */
    private fun getTimeRangeForToday(): TimeRangeFilter {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val now = Instant.now()
        return TimeRangeFilter.between(startOfDay, now)
    }

    /**
     * Helper function to create TimeRangeFilter for a specific date (full 24 hours).
     * For today's date, uses current time as end time instead of midnight tomorrow.
     */
    private fun getTimeRangeForDate(date: LocalDate): TimeRangeFilter {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = if (date == LocalDate.now()) {
            Instant.now()
        } else {
            startOfDay.plus(1, ChronoUnit.DAYS)
        }
        return TimeRangeFilter.between(startOfDay, endOfDay)
    }

    /**
     * Gets today's total steps and data source using AggregateRequest for efficient server-side calculation.
     * Uses data origin filtering to prioritize wearable devices.
     *
     * @return Pair of (total steps, source package name) for today
     * @throws HealthConnectException.PermissionDeniedException if permissions are not granted
     * @throws HealthConnectException.NetworkException if communication with Health Connect fails
     */
    suspend fun getTodaySteps(): Pair<Long, String?> {
        val timeRange = getTimeRangeForToday()

        return try {
            // Read all step records to see what we have
            val stepsRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeRange
                )
            )
            
            // Log all sources and their step counts
            val stepsBySource = mutableMapOf<String, Long>()
            stepsRecords.records.forEach { record ->
                val source = record.metadata.dataOrigin.packageName
                stepsBySource[source] = (stepsBySource[source] ?: 0) + record.count
            }
            
            stepsBySource.forEach { (source, steps) ->
                android.util.Log.d("HealthConnect", "Found steps from $source: $steps")
            }
            
            // Try preferred sources first
            for (source in PREFERRED_SOURCES) {
                val steps = stepsBySource[source]
                if (steps != null && steps > 0) {
                    android.util.Log.d("HealthConnect", "Using steps from preferred source $source: $steps")
                    return Pair(steps, source)
                }
            }
            
            // Fallback: use total from all sources, return first source or null
            val totalSteps = stepsBySource.values.sum()
            val firstSource = stepsBySource.keys.firstOrNull()
            android.util.Log.d("HealthConnect", "No preferred source found, using total from all sources: $totalSteps (source: $firstSource)")
            Pair(totalSteps, firstSource)
        } catch (e: SecurityException) {
            android.util.Log.e("HealthConnect", "Permission denied accessing steps", e)
            throw HealthConnectException.PermissionDeniedException(
                message = "Permission denied to read step data"
            )
        } catch (e: IOException) {
            android.util.Log.e("HealthConnect", "Network error fetching steps", e)
            throw HealthConnectException.NetworkException(
                message = "Failed to communicate with Health Connect",
                cause = e
            )
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching steps", e)
            throw HealthConnectException.UnknownException(
                message = "Unexpected error fetching step data: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun getStepsForDate(date: LocalDate): Long {
        val timeRange = getTimeRangeForDate(date)

        return try {
            // Read all step records to see what we have
            val stepsRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeRange
                )
            )
            
            // Sum steps by source
            val stepsBySource = mutableMapOf<String, Long>()
            stepsRecords.records.forEach { record ->
                val source = record.metadata.dataOrigin.packageName
                stepsBySource[source] = (stepsBySource[source] ?: 0) + record.count
            }
            
            // Try preferred sources first
            for (source in PREFERRED_SOURCES) {
                val steps = stepsBySource[source]
                if (steps != null && steps > 0) {
                    return steps
                }
            }
            
            // Fallback: use total from all sources
            stepsBySource.values.sum()
        } catch (e: SecurityException) {
            throw HealthConnectException.PermissionDeniedException(
                message = "Permission denied to read step data"
            )
        } catch (e: IOException) {
            throw HealthConnectException.NetworkException(
                message = "Failed to communicate with Health Connect",
                cause = e
            )
        } catch (e: Exception) {
            throw HealthConnectException.UnknownException(
                message = "Unexpected error fetching step data for $date: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun getLatestHeartRate(): Pair<HeartRateMetric?, String?> {
        val endTime = Instant.now()
        // Extended time range: look back 7 days to ensure we find data
        val startTime = LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) {
                return Pair(null, null)
            }
            
            val latest = response.records.maxByOrNull { it.startTime }
            val metric = latest?.samples?.lastOrNull()?.let { sample ->
                HeartRateMetric(
                    timestamp = sample.time,
                    bpm = sample.beatsPerMinute
                )
            }
            val source = latest?.metadata?.dataOrigin?.packageName
            Pair(metric, source)
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error fetching latest heart rate", e)
            Pair(null, null)
        }
    }

    suspend fun getHeartRateForDate(date: LocalDate): List<HeartRateMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
        )

        android.util.Log.d("HealthConnect", "Found ${response.records.size} heart rate records")
        
        val metrics = response.records.flatMap { record ->
            record.samples.map { sample ->
                HeartRateMetric(
                    timestamp = sample.time,
                    bpm = sample.beatsPerMinute
                )
            }
        }
        
        return metrics
    }

    suspend fun getAverageHeartRateForDate(date: LocalDate): Double? {
        val timeRange = getTimeRangeForDate(date)
        
        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = timeRange
            )
            
            val response = healthConnectClient.aggregate(aggregateRequest)
            response[HeartRateRecord.BPM_AVG]?.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSleepForDate(date: LocalDate): Pair<SleepMetric?, String?> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val sleepSession = response.records.firstOrNull() ?: return Pair(null, null)
        val source = sleepSession.metadata.dataOrigin.packageName
        
        // Exclude awake stages from total sleep duration
        val durationMinutes = if (sleepSession.stages.isNotEmpty()) {
            sleepSession.stages
                .filter { it.stage != SleepSessionRecord.STAGE_TYPE_AWAKE }
                .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
        } else {
            ChronoUnit.MINUTES.between(sleepSession.startTime, sleepSession.endTime)
        }

        val stages = sleepSession.stages.map { stage ->
            SleepStage(
                stage = stage.stage.toString(),
                startTime = stage.startTime,
                endTime = stage.endTime
            )
        }

        val metric = SleepMetric(
            timestamp = sleepSession.startTime,
            durationMinutes = durationMinutes,
            stages = stages
        )
        return Pair(metric, source)
    }

    suspend fun getAverageSleepDuration(startTime: Instant, endTime: Instant): Long {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val sessions = response.records
            if (sessions.isEmpty()) return 0L

            // Exclude awake stages from each session's duration
            val totalMinutes = sessions.sumOf { session ->
                if (session.stages.isNotEmpty()) {
                    session.stages
                        .filter { it.stage != SleepSessionRecord.STAGE_TYPE_AWAKE }
                        .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                } else {
                    ChronoUnit.MINUTES.between(session.startTime, session.endTime)
                }
            }

            totalMinutes / sessions.size
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error calculating average sleep duration", e)
            0L
        }
    }

    suspend fun getTodayActiveCalories(): Pair<Double, String?> {
        val timeRange = getTimeRangeForToday()

        return try {
            // Read records to get source information
            val records = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = timeRange
                )
            )
            
            if (records.records.isEmpty()) {
                return Pair(0.0, null)
            }
            
            // Sum calories and get first source
            val totalCalories = records.records.sumOf { it.energy.inKilocalories }
            val source = records.records.firstOrNull()?.metadata?.dataOrigin?.packageName
            
            Pair(totalCalories, source)
        } catch (e: SecurityException) {
            throw HealthConnectException.PermissionDeniedException(
                message = "Permission denied to read active calories data"
            )
        } catch (e: IOException) {
            throw HealthConnectException.NetworkException(
                message = "Failed to communicate with Health Connect",
                cause = e
            )
        } catch (e: Exception) {
            throw HealthConnectException.UnknownException(
                message = "Unexpected error fetching active calories: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun getActiveCaloriesForDate(date: LocalDate): Double {
        val timeRange = getTimeRangeForDate(date)

        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = timeRange
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: SecurityException) {
            throw HealthConnectException.PermissionDeniedException(
                message = "Permission denied to read active calories data"
            )
        } catch (e: IOException) {
            throw HealthConnectException.NetworkException(
                message = "Failed to communicate with Health Connect",
                cause = e
            )
        } catch (e: Exception) {
            throw HealthConnectException.UnknownException(
                message = "Unexpected error fetching active calories for $date: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun getBloodPressureForDate(date: LocalDate): List<BloodPressureMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            BloodPressureMetric(
                timestamp = it.time,
                systolic = it.systolic.inMillimetersOfMercury,
                diastolic = it.diastolic.inMillimetersOfMercury
            )
        }
    }

    suspend fun getDistanceForDate(date: LocalDate): Double {
        val timeRange = getTimeRangeForDate(date)

        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = timeRange
            )
            val result = healthConnectClient.aggregate(aggregateRequest)
            result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error aggregating distance for date: $date", e)
            0.0
        }
    }

    suspend fun getExerciseSessionsForDate(date: LocalDate): List<ExerciseSessionMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            val durationMinutes = ChronoUnit.MINUTES.between(it.startTime, it.endTime)
            ExerciseSessionMetric(
                timestamp = it.startTime,
                endTime = it.endTime,
                exerciseType = it.exerciseType.toString(),
                durationMinutes = durationMinutes
            )
        }
    }

    suspend fun getStepsTrend(days: Int): List<Pair<LocalDate, Long>> {
        val result = mutableListOf<Pair<LocalDate, Long>>()
        val today = LocalDate.now()

        for (i in 1..days) {
            val date = today.minusDays(i.toLong())
            val steps = getStepsForDate(date)
            result.add(date to steps)
        }

        return result.reversed()
    }

    suspend fun getLatestBloodPressure(): BloodPressureMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            BloodPressureMetric(
                timestamp = it.time,
                systolic = it.systolic.inMillimetersOfMercury,
                diastolic = it.diastolic.inMillimetersOfMercury
            )
        }
    }

    suspend fun getTodayBloodPressure(): Pair<List<BloodPressureMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            BloodPressureMetric(
                timestamp = it.time,
                systolic = it.systolic.inMillimetersOfMercury,
                diastolic = it.diastolic.inMillimetersOfMercury
            )
        }
        
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        return Pair(metrics, source)
    }

    suspend fun getLatestBloodGlucose(): BloodGlucoseMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            BloodGlucoseMetric(
                timestamp = it.time,
                mgDl = it.level.inMilligramsPerDeciliter
            )
        }
    }

    suspend fun getTodayBloodGlucose(): Pair<List<BloodGlucoseMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            BloodGlucoseMetric(
                timestamp = it.time,
                mgDl = it.level.inMilligramsPerDeciliter
            )
        }
        
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        return Pair(metrics, source)
    }

    suspend fun getLatestBodyTemperature(): BodyTemperatureMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            BodyTemperatureMetric(
                timestamp = it.time,
                celsius = it.temperature.inCelsius
            )
        }
    }

    suspend fun getTodayBodyTemperature(): Pair<List<BodyTemperatureMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            BodyTemperatureMetric(
                timestamp = it.time,
                celsius = it.temperature.inCelsius
            )
        }
        
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        return Pair(metrics, source)
    }

    suspend fun getLatestOxygenSaturation(): OxygenSaturationMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            OxygenSaturationMetric(
                timestamp = it.time,
                percentage = it.percentage.value
            )
        }
    }

    suspend fun getTodayOxygenSaturation(): Pair<List<OxygenSaturationMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            OxygenSaturationMetric(
                timestamp = it.time,
                percentage = it.percentage.value
            )
        }
        
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        return Pair(metrics, source)
    }

    /**
     * Gets 7-day average blood pressure by reading records and calculating manually.
     * Note: Aggregate metrics for blood pressure appear to be unavailable in current Health Connect version.
     *
     * @return Pair of average systolic and diastolic values, or null if no data
     */
    suspend fun getSevenDayAverageBloodPressure(): Pair<Double, Double>? {
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
                )
            )

            if (response.records.isEmpty()) {
                return null
            }

            val systolicAvg = response.records.map { it.systolic.inMillimetersOfMercury }.average()
            val diastolicAvg = response.records.map { it.diastolic.inMillimetersOfMercury }.average()

            Pair(systolicAvg, diastolicAvg)
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error fetching 7-day avg blood pressure", e)
            null
        }
    }

    /**
     * Gets 7-day average blood glucose by reading records and calculating manually.
     *
     * @return Average blood glucose in mg/dL, or null if no data
     */
    suspend fun getSevenDayAverageBloodGlucose(): Double? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) {
                null
            } else {
                val sum = response.records.sumOf { it.level.inMilligramsPerDeciliter }
                sum / response.records.size
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching 7-day avg blood glucose", e)
            null
        }
    }

    /**
     * Gets 7-day average body temperature by reading records and calculating manually.
     *
     * @return Average body temperature in Celsius, or null if no data
     */
    suspend fun getSevenDayAverageBodyTemperature(): Double? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyTemperatureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) {
                null
            } else {
                val sum = response.records.sumOf { it.temperature.inCelsius }
                sum / response.records.size
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching 7-day avg body temperature", e)
            null
        }
    }

    /**
     * Gets 7-day average oxygen saturation by reading records and calculating manually.
     *
     * @return Average oxygen saturation percentage, or null if no data
     */
    suspend fun getSevenDayAverageOxygenSaturation(): Double? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) {
                null
            } else {
                val sum = response.records.sumOf { it.percentage.value }
                sum / response.records.size
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching 7-day avg oxygen saturation", e)
            null
        }
    }

    /**
     * Gets 7-day average resting heart rate using AggregateRequest.
     *
     * @return Average resting heart rate in bpm, or null if no data
     */
    suspend fun getSevenDayAverageRestingHeartRate(): Long? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            
            val response = healthConnectClient.aggregate(aggregateRequest)
            response[RestingHeartRateRecord.BPM_AVG]
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching 7-day avg resting heart rate", e)
            null
        }
    }

    /**
     * Gets 7-day average respiratory rate by reading records and calculating manually.
     *
     * @return Average respiratory rate in breaths per minute, or null if no data
     */
    suspend fun getSevenDayAverageRespiratoryRate(): Double? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            if (response.records.isEmpty()) {
                null
            } else {
                val sum = response.records.sumOf { it.rate }
                sum / response.records.size
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching 7-day avg respiratory rate", e)
            null
        }
    }

    suspend fun getLatestRestingHeartRate(): Pair<RestingHeartRateMetric?, String?> {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        val metric = latest?.let {
            RestingHeartRateMetric(
                timestamp = it.time,
                bpm = it.beatsPerMinute
            )
        }
        val source = latest?.metadata?.dataOrigin?.packageName
        
        return Pair(metric, source)
    }

    suspend fun getTodayRestingHeartRate(): Pair<List<RestingHeartRateMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            RestingHeartRateMetric(
                timestamp = it.time,
                bpm = it.beatsPerMinute
            )
        }
        
        // Get source from first record if available
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        
        return Pair(metrics, source)
    }

    suspend fun getLatestRespiratoryRate(): RespiratoryRateMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            RespiratoryRateMetric(
                timestamp = it.time,
                breathsPerMinute = it.rate
            )
        }
    }

    suspend fun getTodayRespiratoryRate(): Pair<List<RespiratoryRateMetric>, String?> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = timeRange
            )
        )

        val metrics = response.records.map {
            RespiratoryRateMetric(
                timestamp = it.time,
                breathsPerMinute = it.rate
            )
        }
        
        val source = response.records.firstOrNull()?.metadata?.dataOrigin?.packageName
        return Pair(metrics, source)
    }

    suspend fun getBloodGlucoseForDate(date: LocalDate): List<BloodGlucoseMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            BloodGlucoseMetric(
                timestamp = it.time,
                mgDl = it.level.inMilligramsPerDeciliter
            )
        }
    }

    suspend fun getBodyTemperatureForDate(date: LocalDate): List<BodyTemperatureMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            BodyTemperatureMetric(
                timestamp = it.time,
                celsius = it.temperature.inCelsius
            )
        }
    }

    suspend fun getOxygenSaturationForDate(date: LocalDate): List<OxygenSaturationMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            OxygenSaturationMetric(
                timestamp = it.time,
                percentage = it.percentage.value
            )
        }
    }

    suspend fun getRestingHeartRateForDate(date: LocalDate): List<RestingHeartRateMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            RestingHeartRateMetric(
                timestamp = it.time,
                bpm = it.beatsPerMinute
            )
        }
    }

    suspend fun getRespiratoryRateForDate(date: LocalDate): List<RespiratoryRateMetric> {
        val timeRange = getTimeRangeForDate(date)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            RespiratoryRateMetric(
                timestamp = it.time,
                breathsPerMinute = it.rate
            )
        }
    }

    suspend fun getTodayDistance(): Double {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = timeRange
            )
        )

        return response[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
    }

    suspend fun getTodayExerciseSessions(): List<ExerciseSessionMetric> {
        val timeRange = getTimeRangeForToday()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeRange
            )
        )

        return response.records.map {
            val durationMinutes = java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            ExerciseSessionMetric(
                timestamp = it.startTime,
                endTime = it.endTime,
                exerciseType = it.exerciseType.toString(),
                durationMinutes = durationMinutes
            )
        }
    }
}
