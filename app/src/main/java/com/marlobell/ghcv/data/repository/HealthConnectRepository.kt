package com.marlobell.ghcv.data.repository

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectRepository(
    private val healthConnectClient: HealthConnectClient
) {

    suspend fun getTodaySteps(): Long {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        return try {
            // Read raw records
            val recordsResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            
            // Group by data source and pick the highest count (prioritize wearables)
            val sourceGroups = recordsResponse.records.groupBy { it.metadata.dataOrigin.packageName }
            
            val sourceTotals = sourceGroups.map { (source, records) ->
                source to records.sumOf { it.count }
            }.toMap()
            
            // Priority: Oura Ring > Google Fit > Android system
            val preferredSources = listOf("com.ouraring.oura", "com.google.android.apps.fitness", "android")
            val selectedSource = preferredSources.firstOrNull { it in sourceTotals.keys }
            
            if (selectedSource != null) {
                sourceTotals[selectedSource] ?: 0L
            } else {
                sourceTotals.values.maxOrNull() ?: 0L
            }
        } catch (e: Exception) {
            android.util.Log.e("HealthConnect", "Error fetching steps", e)
            0L
        }
    }

    suspend fun getStepsForDate(date: LocalDate): Long {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        return try {
            val recordsResponse = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            
            // Group by source and pick highest (prioritize wearables)
            val sourceTotals = recordsResponse.records
                .groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { (_, records) -> records.sumOf { it.count } }
            
            val preferredSources = listOf("com.ouraring.oura", "com.google.android.apps.fitness", "android")
            val selectedSource = preferredSources.firstOrNull { it in sourceTotals.keys }
            
            if (selectedSource != null) {
                sourceTotals[selectedSource] ?: 0L
            } else {
                sourceTotals.values.maxOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    suspend fun getLatestHeartRate(): HeartRateMetric? {
        val endTime = Instant.now()
        val startTime = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.startTime }
        return latest?.samples?.lastOrNull()?.let { sample ->
            HeartRateMetric(
                timestamp = sample.time,
                bpm = sample.beatsPerMinute
            )
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
        val heartRates = getHeartRateForDate(date)
        return if (heartRates.isNotEmpty()) {
            heartRates.map { it.bpm.toDouble() }.average()
        } else null
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

    suspend fun getTodayActiveCalories(): Double {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = Instant.now()

        return try {
            val response = healthConnectClient.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getActiveCaloriesForDate(date: LocalDate): Double {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        return try {
            val response = healthConnectClient.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        } catch (e: Exception) {
            0.0
        }
    }

    suspend fun getLatestWeight(): WeightMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(30, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        val latest = response.records.maxByOrNull { it.time }
        return latest?.let {
            WeightMetric(
                timestamp = it.time,
                kilograms = it.weight.inKilograms
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

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.sumOf { it.distance.inMeters }
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

    suspend fun getWeightTrend(days: Int): List<WeightMetric> {
        val endTime = Instant.now()
        val startTime = endTime.minus(days.toLong(), ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        return response.records.map {
            WeightMetric(
                timestamp = it.time,
                kilograms = it.weight.inKilograms
            )
        }.sortedBy { it.timestamp }
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
