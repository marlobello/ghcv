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
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.viewmodel.TrendPeriod
import com.marlobell.ghcv.ui.viewmodel.TrendsViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TrendsScreen(
    healthConnectManager: HealthConnectManager,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: TrendsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TrendsViewModel(repository) as T
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
        Text(
            text = "Trends",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Period selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrendPeriod.entries.forEach { period ->
                FilterChip(
                    selected = uiState.period == period,
                    onClick = { viewModel.changePeriod(period) },
                    label = { Text(period.label) }
                )
            }
        }
        
        // Metric selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.selectedMetric == "steps",
                onClick = { viewModel.selectMetric("steps") },
                label = { Text("Steps") },
                leadingIcon = { 
                    @Suppress("DEPRECATION")
                    Icon(Icons.Filled.DirectionsWalk, null, Modifier.size(18.dp)) 
                }
            )
            FilterChip(
                selected = uiState.selectedMetric == "heartrate",
                onClick = { viewModel.selectMetric("heartrate") },
                label = { Text("Heart Rate") },
                leadingIcon = { Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp)) }
            )
            FilterChip(
                selected = uiState.selectedMetric == "sleep",
                onClick = { viewModel.selectMetric("sleep") },
                label = { Text("Sleep") },
                leadingIcon = { Icon(Icons.Filled.Bedtime, null, Modifier.size(18.dp)) }
            )
        }
        
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Text(
                text = "Error: ${uiState.error}",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            when (uiState.selectedMetric) {
                "steps" -> {
                    if (uiState.stepsTrend.isNotEmpty()) {
                        StepsTrendCard(
                            data = uiState.stepsTrend,
                            avgSteps = uiState.avgSteps,
                            totalSteps = uiState.totalSteps,
                            maxSteps = uiState.maxSteps
                        )
                    } else {
                        EmptyStateCard("No steps data available for this period")
                    }
                }
                "heartrate" -> {
                    if (uiState.heartRateTrend.isNotEmpty()) {
                        HeartRateTrendCard(
                            data = uiState.heartRateTrend,
                            avgHeartRate = uiState.avgHeartRate,
                            minHeartRate = uiState.minHeartRate,
                            maxHeartRate = uiState.maxHeartRate
                        )
                    } else {
                        EmptyStateCard("No heart rate data available for this period")
                    }
                }
                "sleep" -> {
                    if (uiState.sleepTrend.isNotEmpty()) {
                        SleepTrendCard(
                            data = uiState.sleepTrend,
                            avgSleep = uiState.avgSleep,
                            totalSleep = uiState.totalSleep
                        )
                    } else {
                        EmptyStateCard("No sleep data available for this period")
                    }
                }
            }
        }
    }
}

@Composable
fun StepsTrendCard(
    data: List<Pair<LocalDate, Long>>,
    avgSteps: Long,
    totalSteps: Long,
    maxSteps: Long
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Steps Trend",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            StepsChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrendStatItem("Average", "$avgSteps")
                TrendStatItem("Total", "$totalSteps")
                TrendStatItem("Peak", "$maxSteps")
            }
        }
    }
}

@Composable
fun HeartRateTrendCard(
    data: List<Pair<LocalDate, Double>>,
    avgHeartRate: Double,
    minHeartRate: Double,
    maxHeartRate: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Heart Rate Trend",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            HeartRateTrendChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrendStatItem("Min", "${minHeartRate.toInt()} bpm")
                TrendStatItem("Avg", "${avgHeartRate.toInt()} bpm")
                TrendStatItem("Max", "${maxHeartRate.toInt()} bpm")
            }
        }
    }
}

@Composable
fun SleepTrendCard(
    data: List<Pair<LocalDate, Long>>,
    avgSleep: Long,
    totalSleep: Long
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sleep Trend",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            SleepChart(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val avgHours = avgSleep / 60
            val avgMinutes = avgSleep % 60
            val totalHours = totalSleep / 60
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TrendStatItem("Avg/Night", "${avgHours}h ${avgMinutes}m")
                TrendStatItem("Total", "${totalHours}h")
            }
        }
    }
}

@Composable
fun StepsChart(
    data: List<Pair<LocalDate, Long>>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(data) {
        val xValues = data.indices.map { it.toFloat() }
        val yValues = data.map { it.second.toFloat() }
        
        modelProducer.runTransaction {
            columnSeries { series(xValues, yValues) }
        }
    }
    
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis()
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

@Composable
fun HeartRateTrendChart(
    data: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(data) {
        val xValues = data.indices.map { it.toFloat() }
        val yValues = data.map { it.second.toFloat() }
        
        modelProducer.runTransaction {
            lineSeries { series(xValues, yValues) }
        }
    }
    
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis()
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

@Composable
fun SleepChart(
    data: List<Pair<LocalDate, Long>>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(data) {
        val xValues = data.indices.map { it.toFloat() }
        val yValues = data.map { (it.second / 60).toFloat() } // Convert to hours
        
        modelProducer.runTransaction {
            columnSeries { series(xValues, yValues) }
        }
    }
    
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis()
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

@Composable
fun TrendStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
