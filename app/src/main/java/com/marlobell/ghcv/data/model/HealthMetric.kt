package com.marlobell.ghcv.data.model

import java.time.Instant

sealed class HealthMetric {
    abstract val timestamp: Instant
    abstract val value: Double
    abstract val unit: String
}

data class HeartRateMetric(
    override val timestamp: Instant,
    val bpm: Long
) : HealthMetric() {
    override val value: Double = bpm.toDouble()
    override val unit: String = "bpm"
}

data class SleepMetric(
    override val timestamp: Instant,
    val durationMinutes: Long,
    val stages: List<SleepStage> = emptyList()
) : HealthMetric() {
    override val value: Double = durationMinutes.toDouble()
    override val unit: String = "minutes"
}

data class SleepStage(
    val stage: String,
    val startTime: Instant,
    val endTime: Instant
)

data class BloodPressureMetric(
    override val timestamp: Instant,
    val systolic: Double,
    val diastolic: Double
) : HealthMetric() {
    override val value: Double = systolic
    override val unit: String = "mmHg"
}

data class WeightMetric(
    override val timestamp: Instant,
    val kilograms: Double
) : HealthMetric() {
    override val value: Double = kilograms
    override val unit: String = "kg"
}

data class ExerciseSessionMetric(
    override val timestamp: Instant,
    val endTime: Instant,
    val exerciseType: String,
    val durationMinutes: Long,
    val distance: Double? = null,
    val caloriesBurned: Double? = null
) : HealthMetric() {
    override val value: Double = durationMinutes.toDouble()
    override val unit: String = "minutes"
}

data class BodyTemperatureMetric(
    override val timestamp: Instant,
    val celsius: Double
) : HealthMetric() {
    override val value: Double = celsius
    override val unit: String = "°C"
}

data class OxygenSaturationMetric(
    override val timestamp: Instant,
    val percentage: Double
) : HealthMetric() {
    override val value: Double = percentage
    override val unit: String = "%"
}

data class BloodGlucoseMetric(
    override val timestamp: Instant,
    val mgDl: Double
) : HealthMetric() {
    override val value: Double = mgDl
    override val unit: String = "mg/dL"
}

data class RestingHeartRateMetric(
    override val timestamp: Instant,
    val bpm: Long
) : HealthMetric() {
    override val value: Double = bpm.toDouble()
    override val unit: String = "bpm"
}

data class RespiratoryRateMetric(
    override val timestamp: Instant,
    val breathsPerMinute: Double
) : HealthMetric() {
    override val value: Double = breathsPerMinute
    override val unit: String = "breaths/min"
}

data class VitalStats<T>(
    val latest: T? = null,
    val latestTimestamp: Instant? = null,
    val dailyAvg: Double? = null,
    val dailyMin: Double? = null,
    val dailyMax: Double? = null,
    val readingCount: Int = 0
) {
    val hasData: Boolean get() = latest != null
}
