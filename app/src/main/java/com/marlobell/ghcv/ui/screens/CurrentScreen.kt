package com.marlobell.ghcv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.data.model.*
import com.marlobell.ghcv.ui.viewmodel.CurrentViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CurrentScreen(
    healthConnectManager: HealthConnectManager,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: CurrentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CurrentViewModel(repository, healthConnectManager) as T
            }
        }
    )
    
    val healthData by viewModel.healthData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Health",
                style = MaterialTheme.typography.headlineMedium
            )
            
            healthData.lastUpdated?.let { timestamp ->
                val minutesAgo = Duration.between(timestamp, Instant.now()).toMinutes()
                Text(
                    text = if (minutesAgo < 1) "Just now" else "$minutesAgo min ago",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        when (uiState) {
            is com.marlobell.ghcv.ui.UiState.Loading, is com.marlobell.ghcv.ui.UiState.Uninitialized -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                HealthMetricCard(
                title = "Steps",
                value = "${healthData.steps}",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                trend = healthData.stepsTrend,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            healthData.heartRate?.let { hr ->
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val timeStr = healthData.heartRateTimestamp?.atZone(ZoneId.systemDefault())
                    ?.format(timeFormatter) ?: ""
                
                HealthMetricCard(
                    title = "Heart Rate",
                    value = "$hr",
                    unit = "bpm",
                    icon = Icons.Filled.Favorite,
                    subtitle = if (timeStr.isNotEmpty()) "Measured at $timeStr" else null
                )
            }
            
            healthData.sleepLastNight?.let { sleepMinutes ->
                val hours = sleepMinutes / 60
                val minutes = sleepMinutes % 60
                
                HealthMetricCard(
                    title = "Sleep Last Night",
                    value = "${hours}h ${minutes}m",
                    icon = Icons.Filled.Bedtime,
                    subtitle = "Total sleep duration"
                )
            }
            
            HealthMetricCard(
                title = "Active Calories",
                value = String.format(Locale.US, "%.0f", healthData.activeCalories),
                unit = "kcal",
                icon = Icons.Filled.LocalFireDepartment
            )
            
            // Vitals Section
            if (healthData.bloodPressure.hasData || healthData.bloodGlucose.hasData || 
                healthData.bodyTemperature.hasData || healthData.oxygenSaturation.hasData ||
                healthData.restingHeartRate.hasData || healthData.respiratoryRate.hasData) {
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vitals",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Blood Pressure
            if (healthData.bloodPressure.hasData) {
                healthData.bloodPressure.latest?.let { bp ->
                    VitalsMetricCard(
                        title = "Blood Pressure",
                        value = "${bp.systolic.toInt()}/${bp.diastolic.toInt()}",
                        unit = "mmHg",
                        icon = Icons.Filled.Favorite,
                        timestamp = healthData.bloodPressure.latestTimestamp,
                        dailyStats = if (healthData.bloodPressure.readingCount > 1) {
                            "Avg: ${healthData.bloodPressure.dailyAvg?.toInt() ?: "--"} | " +
                            "Min: ${healthData.bloodPressure.dailyMin?.toInt() ?: "--"} | " +
                            "Max: ${healthData.bloodPressure.dailyMax?.toInt() ?: "--"}"
                        } else null
                    )
                }
            }
            
            // Blood Glucose
            if (healthData.bloodGlucose.hasData) {
                healthData.bloodGlucose.latest?.let { glucose ->
                    VitalsMetricCard(
                        title = "Blood Glucose",
                        value = String.format(Locale.US, "%.0f", glucose),
                        unit = "mg/dL",
                        icon = Icons.Filled.Bloodtype,
                        timestamp = healthData.bloodGlucose.latestTimestamp,
                        dailyStats = if (healthData.bloodGlucose.readingCount > 1) {
                            "Avg: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyAvg ?: 0.0)} | " +
                            "Min: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyMin ?: 0.0)} | " +
                            "Max: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyMax ?: 0.0)}"
                        } else null
                    )
                }
            }
            
            // Body Temperature
            if (healthData.bodyTemperature.hasData) {
                healthData.bodyTemperature.latest?.let { temp ->
                    VitalsMetricCard(
                        title = "Body Temperature",
                        value = String.format(Locale.US, "%.1f", temp),
                        unit = "°C",
                        icon = Icons.Filled.Thermostat,
                        timestamp = healthData.bodyTemperature.latestTimestamp,
                        dailyStats = if (healthData.bodyTemperature.readingCount > 1) {
                            "Avg: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyAvg ?: 0.0)} | " +
                            "Min: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyMin ?: 0.0)} | " +
                            "Max: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyMax ?: 0.0)}"
                        } else null
                    )
                }
            }
            
            // Oxygen Saturation
            if (healthData.oxygenSaturation.hasData) {
                healthData.oxygenSaturation.latest?.let { spo2 ->
                    VitalsMetricCard(
                        title = "Oxygen Saturation",
                        value = String.format(Locale.US, "%.0f", spo2),
                        unit = "%",
                        icon = Icons.Filled.Air,
                        timestamp = healthData.oxygenSaturation.latestTimestamp,
                        dailyStats = if (healthData.oxygenSaturation.readingCount > 1) {
                            "Avg: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyAvg ?: 0.0)} | " +
                            "Min: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyMin ?: 0.0)} | " +
                            "Max: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyMax ?: 0.0)}"
                        } else null
                    )
                }
            }
            
            // Resting Heart Rate
            if (healthData.restingHeartRate.hasData) {
                healthData.restingHeartRate.latest?.let { rhr ->
                    VitalsMetricCard(
                        title = "Resting Heart Rate",
                        value = "$rhr",
                        unit = "bpm",
                        icon = Icons.Filled.MonitorHeart,
                        timestamp = healthData.restingHeartRate.latestTimestamp,
                        dailyStats = if (healthData.restingHeartRate.readingCount > 1) {
                            "Avg: ${healthData.restingHeartRate.dailyAvg?.toInt() ?: "--"} | " +
                            "Min: ${healthData.restingHeartRate.dailyMin?.toInt() ?: "--"} | " +
                            "Max: ${healthData.restingHeartRate.dailyMax?.toInt() ?: "--"}"
                        } else null
                    )
                }
            }
            
            // Respiratory Rate
            if (healthData.respiratoryRate.hasData) {
                healthData.respiratoryRate.latest?.let { rr ->
                    VitalsMetricCard(
                        title = "Respiratory Rate",
                        value = String.format(Locale.US, "%.0f", rr),
                        unit = "breaths/min",
                        icon = Icons.Filled.Air,
                        timestamp = healthData.respiratoryRate.latestTimestamp,
                        dailyStats = if (healthData.respiratoryRate.readingCount > 1) {
                            "Avg: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyAvg ?: 0.0)} | " +
                            "Min: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyMin ?: 0.0)} | " +
                            "Max: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyMax ?: 0.0)}"
                        } else null
                    )
                }
            }
            }
        }
    }
}

@Composable
fun HealthMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    trend: Int? = null,
    subtitle: String? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.displaySmall,
                        color = contentColor
                    )
                    unit?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                
                subtitle?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            
            trend?.let { trendValue ->
                if (trendValue != 0) {
                    Surface(
                        color = if (trendValue > 0) 
                            MaterialTheme.colorScheme.tertiaryContainer
                        else 
                            MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (trendValue > 0)
                                    Icons.AutoMirrored.Filled.TrendingUp
                                else
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (trendValue > 0)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "${kotlin.math.abs(trendValue)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (trendValue > 0)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VitalsMetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    timestamp: Instant?,
    modifier: Modifier = Modifier,
    dailyStats: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            timestamp?.let {
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val timeStr = it.atZone(ZoneId.systemDefault()).format(timeFormatter)
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Measured at $timeStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            
            dailyStats?.let { stats ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Today: $stats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}