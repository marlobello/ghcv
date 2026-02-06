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

/**
 * Represents a comparison value for a health metric.
 * Provides contextual information about how current values compare to historical averages.
 */
data class MetricComparison(
    val label: String,           // e.g., "7-day avg" or "Resting HR"
    val value: String,            // e.g., "8,245"
    val unit: String,             // e.g., "steps", "bpm", "kcal"
    val difference: String? = null,      // e.g., "+1,234" or "-234"
    val percentage: String? = null,      // e.g., "+12%" or "-5%"
    val isPositive: Boolean? = null      // true = better/green, false = worse/red, null = neutral/gray
)

