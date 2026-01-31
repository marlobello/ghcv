package com.marlobell.ghcv.data.repository

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.marlobell.ghcv.data.model.*
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

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.sumOf { it.count }
    }

    suspend fun getStepsForDate(date: LocalDate): Long {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(1, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.sumOf { it.count }
    }

    suspend fun getLatestHeartRate(): HeartRateMetric? {
        val endTime = Instant.now()
        val startTime = endTime.minus(1, ChronoUnit.HOURS)

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

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
            )
        )

        return response.records.sumOf { it.energy.inKilocalories }
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
}
