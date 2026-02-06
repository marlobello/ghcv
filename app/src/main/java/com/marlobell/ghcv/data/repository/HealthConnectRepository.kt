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
import com.marlobell.ghcv.data.model.WeightMetric
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
     * Gets today's total steps using AggregateRequest for efficient server-side calculation.
     * Uses data origin filtering to prioritize wearable devices.
     *
     * @return Total steps for today
     * @throws HealthConnectException.PermissionDeniedException if permissions are not granted
     * @throws HealthConnectException.NetworkException if communication with Health Connect fails
     */
    suspend fun getTodaySteps(): Long {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        return try {
            // Try preferred sources first
            for (source in PREFERRED_SOURCES) {
                val steps = getStepsWithDataOriginFilter(startOfDay, endOfDay, source)
                if (steps > 0) {
                    android.util.Log.d("HealthConnect", "Using steps from $source: $steps")
                    return steps
                }
            }
            
            // Fallback: aggregate without filter (use any available source)
            android.util.Log.d("HealthConnect", "No preferred source found, using any available source")
            getStepsWithoutFilter(startOfDay, endOfDay)
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

    /**
     * Helper function to get steps with data origin filter.
     */
    private suspend fun getStepsWithDataOriginFilter(
        start: Instant,
        end: Instant,
        packageName: String
    ): Long {
        return try {
            val dataOriginFilter = setOf(
                androidx.health.connect.client.records.metadata.DataOrigin(packageName)
            )
            
            val aggregateRequest = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                dataOriginFilter = dataOriginFilter
            )
            
            val response = healthConnectClient.aggregate(aggregateRequest)
            response[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Helper function to get steps without data origin filter.
     */
    private suspend fun getStepsWithoutFilter(start: Instant, end: Instant): Long {
        val aggregateRequest = AggregateRequest(
            metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)
        )
        
        val response = healthConnectClient.aggregate(aggregateRequest)
        return response[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun getStepsForDate(date: LocalDate): Long {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        return try {
            // Try preferred sources first
            for (source in PREFERRED_SOURCES) {
                val steps = getStepsWithDataOriginFilter(startOfDay, endOfDay, source)
                if (steps > 0) {
                    return steps
                }
            }
            
            // Fallback: aggregate without filter
            getStepsWithoutFilter(startOfDay, endOfDay)
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

    suspend fun getLatestHeartRate(): HeartRateMetric? {
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
                return null
            }
            
            val latest = response.records.maxByOrNull { it.startTime }
            latest?.samples?.lastOrNull()?.let { sample ->
                HeartRateMetric(
                    timestamp = sample.time,
                    bpm = sample.beatsPerMinute
                )
            }
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error fetching latest heart rate", e)
            null
        }
    }

    suspend fun getHeartRateForDate(date: LocalDate): List<HeartRateMetric> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.flatMap { record ->
            record.samples.map { sample ->
                HeartRateMetric(
                    timestamp = sample.time,
                    bpm = sample.beatsPerMinute
                )
            }
        }
    }

    suspend fun getAverageHeartRateForDate(date: LocalDate): Double? {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)
        
        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
            
            val response = healthConnectClient.aggregate(aggregateRequest)
            response[HeartRateRecord.BPM_AVG]?.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSleepForDate(date: LocalDate): SleepMetric? {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        val sleepSession = response.records.firstOrNull() ?: return null
        
        val durationMinutes = ChronoUnit.MINUTES.between(
            sleepSession.startTime,
            sleepSession.endTime
        )

        val stages = sleepSession.stages.map { stage ->
            SleepStage(
                stage = stage.stage.toString(),
                startTime = stage.startTime,
                endTime = stage.endTime
            )
        }

        return SleepMetric(
            timestamp = sleepSession.startTime,
            durationMinutes = durationMinutes,
            stages = stages
        )
    }

    suspend fun getAverageSleepDuration(startTime: Instant, endTime: Instant): Long {
        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val result = healthConnectClient.aggregate(aggregateRequest)
            val totalDuration = result[SleepSessionRecord.SLEEP_DURATION_TOTAL] ?: return 0L
            
            // Calculate days between for average
            val daysBetween = ChronoUnit.DAYS.between(startTime, endTime)
            if (daysBetween > 0) {
                ChronoUnit.MINUTES.between(Instant.EPOCH, Instant.EPOCH.plus(totalDuration)) / daysBetween
            } else {
                0L
            }
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error aggregating sleep duration", e)
            0L
        }
    }

    suspend fun getTodayActiveCalories(): Double {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
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
                message = "Unexpected error fetching active calories: ${e.message}",
                cause = e
            )
        }
    }

    suspend fun getActiveCaloriesForDate(date: LocalDate): Double {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
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
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
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
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
            val result = healthConnectClient.aggregate(aggregateRequest)
            result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error aggregating distance for date: $date", e)
            0.0
        }
    }

    suspend fun getExerciseSessionsForDate(date: LocalDate): List<ExerciseSessionMetric> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
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

        for (i in 0 until days) {
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

    suspend fun getTodayBloodPressure(): List<BloodPressureMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
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

    suspend fun getTodayBloodGlucose(): List<BloodGlucoseMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.map {
            BloodGlucoseMetric(
                timestamp = it.time,
                mgDl = it.level.inMilligramsPerDeciliter
            )
        }
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

    suspend fun getTodayBodyTemperature(): List<BodyTemperatureMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.map {
            BodyTemperatureMetric(
                timestamp = it.time,
                celsius = it.temperature.inCelsius
            )
        }
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

    suspend fun getTodayOxygenSaturation(): List<OxygenSaturationMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.map {
            OxygenSaturationMetric(
                timestamp = it.time,
                percentage = it.percentage.value
            )
        }
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
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
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
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = BodyTemperatureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
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
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
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
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val aggregateRequest = AggregateRequest(
                metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
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
        val endOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val startOfPeriod = endOfDay.minus(7, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfPeriod, endOfDay)
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

    suspend fun getLatestRestingHeartRate(): RestingHeartRateMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(7, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            RestingHeartRateMetric(
                timestamp = it.time,
                bpm = it.beatsPerMinute
            )
        }
    }

    suspend fun getTodayRestingHeartRate(): List<RestingHeartRateMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.map {
            RestingHeartRateMetric(
                timestamp = it.time,
                bpm = it.beatsPerMinute
            )
        }
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

    suspend fun getTodayRespiratoryRate(): List<RespiratoryRateMetric> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.map {
            RespiratoryRateMetric(
                timestamp = it.time,
                breathsPerMinute = it.rate
            )
        }
    }
}
