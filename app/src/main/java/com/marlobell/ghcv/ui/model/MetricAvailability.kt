package com.marlobell.ghcv.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the availability status of a health metric.
 */
enum class MetricAvailability {
    /** Data was successfully retrieved from Health Connect */
    HAS_DATA,
    
    /** GHCV does not have permission to access this data type */
    NO_PERMISSION,
    
    /** GHCV has permission but no data is available */
    NO_DATA
}

/**
 * Information about a health metric type.
 * Used for displaying cards in different states.
 */
data class MetricInfo(
    val id: String,
    val displayName: String,
    val icon: ImageVector,
    val availability: MetricAvailability
)

/**
 * Represents a health metric type for permission tracking.
 */
enum class HealthMetricType(val displayName: String) {
    STEPS("Steps"),
    HEART_RATE("Heart Rate"),
    SLEEP("Sleep"),
    ACTIVE_CALORIES("Active Calories"),
    BLOOD_PRESSURE("Blood Pressure"),
    BLOOD_GLUCOSE("Blood Glucose"),
    BODY_TEMPERATURE("Body Temperature"),
    OXYGEN_SATURATION("Oxygen Saturation"),
    RESTING_HEART_RATE("Resting Heart Rate"),
    RESPIRATORY_RATE("Respiratory Rate")
}
