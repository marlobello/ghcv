package com.marlobell.ghcv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.viewmodel.CurrentViewModel
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            
            uiState.lastUpdated?.let { timestamp ->
                val minutesAgo = Duration.between(timestamp, Instant.now()).toMinutes()
                Text(
                    text = if (minutesAgo < 1) "Just now" else "$minutesAgo min ago",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.refresh() }) {
                    Text("Retry")
                }
            }
        } else {
            HealthMetricCard(
                title = "Steps",
                value = "${uiState.steps}",
                icon = Icons.Default.DirectionsWalk,
                trend = uiState.stepsTrend,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            uiState.heartRate?.let { hr ->
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val timeStr = uiState.heartRateTimestamp?.atZone(ZoneId.systemDefault())
                    ?.format(timeFormatter) ?: ""
                
                HealthMetricCard(
                    title = "Heart Rate",
                    value = "$hr",
                    unit = "bpm",
                    icon = Icons.Default.Favorite,
                    subtitle = if (timeStr.isNotEmpty()) "Measured at $timeStr" else null
                )
            }
            
            uiState.sleepLastNight?.let { sleepMinutes ->
                val hours = sleepMinutes / 60
                val minutes = sleepMinutes % 60
                
                HealthMetricCard(
                    title = "Sleep Last Night",
                    value = "${hours}h ${minutes}m",
                    icon = Icons.Default.Bedtime,
                    subtitle = "Total sleep duration"
                )
            }
            
            HealthMetricCard(
                title = "Active Calories",
                value = String.format("%.0f", uiState.activeCalories),
                unit = "kcal",
                icon = Icons.Default.LocalFireDepartment
            )
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
                                    Icons.Default.TrendingUp 
                                else 
                                    Icons.Default.TrendingDown,
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
