package com.marlobell.ghcv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Color scheme for health metric cards, organized by category.
 * Each category has container and content colors that work with Material3 theme.
 */

/**
 * Activity metrics: Steps, Distance, Active Calories, Exercise Sessions
 * Blue color scheme representing movement and energy
 */
object ActivityColors {
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.primaryContainer
    
    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onPrimaryContainer
}

/**
 * Cardiovascular/vitals metrics: Heart Rate, Resting Heart Rate, Blood Pressure
 * Red/pink color scheme representing heart and blood
 */
object CardiovascularColors {
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.errorContainer
    
    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onErrorContainer
}

/**
 * Respiratory metrics: Respiratory Rate, Oxygen Saturation
 * Teal/cyan color scheme representing breathing and air
 */
object RespiratoryColors {
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.tertiaryContainer
    
    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onTertiaryContainer
}

/**
 * Metabolic metrics: Blood Glucose, Body Temperature
 * Orange color scheme representing metabolism and energy conversion
 */
object MetabolicColors {
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.secondaryContainer
    
    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onSecondaryContainer
}

/**
 * Sleep metrics: Sleep duration and stages
 * Purple color scheme representing rest and recovery
 */
object SleepColors {
    // Using a custom purple-ish tone from tertiaryContainer variant
    // In most Material3 themes, this provides a distinct purple/violet appearance
    @Composable
    fun containerColor() = MaterialTheme.colorScheme.tertiaryContainer
    
    @Composable
    fun contentColor() = MaterialTheme.colorScheme.onTertiaryContainer
}

/**
 * Helper object to get colors for specific metric types
 */
object MetricColorScheme {
    enum class Category {
        ACTIVITY,
        CARDIOVASCULAR,
        RESPIRATORY,
        METABOLIC,
        SLEEP
    }
    
    @Composable
    fun containerColor(category: Category): Color = when (category) {
        Category.ACTIVITY -> ActivityColors.containerColor()
        Category.CARDIOVASCULAR -> CardiovascularColors.containerColor()
        Category.RESPIRATORY -> RespiratoryColors.containerColor()
        Category.METABOLIC -> MetabolicColors.containerColor()
        Category.SLEEP -> SleepColors.containerColor()
    }
    
    @Composable
    fun contentColor(category: Category): Color = when (category) {
        Category.ACTIVITY -> ActivityColors.contentColor()
        Category.CARDIOVASCULAR -> CardiovascularColors.contentColor()
        Category.RESPIRATORY -> RespiratoryColors.contentColor()
        Category.METABOLIC -> MetabolicColors.contentColor()
        Category.SLEEP -> SleepColors.contentColor()
    }
}
