package com.marlobell.ghcv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.viewmodel.HistoricalViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottomAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStartAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalScreen(
    healthConnectManager: HealthConnectManager,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: HistoricalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HistoricalViewModel(repository) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Historical Data",
            style = MaterialTheme.typography.headlineMedium
        )
        
        // Date selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Selected Date",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = uiState.date.format(DateTimeFormatter.ofPattern("MMMM dd, yyyy")),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = "Select date"
                )
            }
        }
        
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
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
            // Steps Card
            ExpandableMetricCard(
                title = "Steps",
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                value = "${uiState.steps}",
                isExpanded = uiState.expandedSections.contains("steps"),
                onToggle = { viewModel.toggleSection("steps") },
                comparisonText = if (uiState.previousDaySteps > 0) {
                    val diff = uiState.steps - uiState.previousDaySteps
                    val sign = if (diff >= 0) "+" else ""
                    "$sign$diff vs yesterday"
                } else null
            ) {
                Text(
                    text = "Daily total step count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Heart Rate Card
            if (uiState.heartRateData.isNotEmpty()) {
                ExpandableMetricCard(
                    title = "Heart Rate",
                    icon = Icons.Filled.Favorite,
                    value = "${String.format(Locale.US, "%.1f", uiState.averageHeartRate)} bpm",
                    subtitle = "${uiState.minHeartRate}-${uiState.maxHeartRate} bpm range",
                    isExpanded = uiState.expandedSections.contains("heartrate"),
                    onToggle = { viewModel.toggleSection("heartrate") }
                ) {
                    Column {
                        Text(
                            text = "Heart Rate Throughout Day",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HeartRateChart(
                            heartRateData = uiState.heartRateData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("Min", "${uiState.minHeartRate}")
                            StatItem("Avg", String.format(Locale.US, "%.0f", uiState.averageHeartRate))
                            StatItem("Max", "${uiState.maxHeartRate}")
                        }
                    }
                }
            }
            
            // Sleep Card
            uiState.sleepData?.let { sleep ->
                val hours = sleep.durationMinutes / 60
                val minutes = sleep.durationMinutes % 60
                
                ExpandableMetricCard(
                    title = "Sleep",
                    icon = Icons.Filled.Bedtime,
                    value = "${hours}h ${minutes}m",
                    isExpanded = uiState.expandedSections.contains("sleep"),
                    onToggle = { viewModel.toggleSection("sleep") }
                ) {
                    Column {
                        Text(
                            text = "Sleep Stages",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (sleep.stages.isNotEmpty()) {
                            sleep.stages.forEach { stage ->
                                val stageDuration = java.time.Duration.between(
                                    stage.startTime,
                                    stage.endTime
                                ).toMinutes()
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stage.stage.replace("_", " ").lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${stageDuration}min",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No stage data available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Distance Card (if available)
            if (uiState.distance > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Distance",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", uiState.distance / 1000)} km",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }
            
            // Exercise Sessions Card (if available)
            if (uiState.exerciseSessions > 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Exercise Sessions",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${uiState.exerciseSessions}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.loadDataForDate(selectedDate)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun ExpandableMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    comparisonText: String? = null,
    isExpanded: Boolean = false,
    onToggle: () -> Unit,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        comparisonText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    expandedContent()
                }
            }
        }
    }
}

@Composable
fun HeartRateChart(
    heartRateData: List<com.marlobell.ghcv.data.model.HeartRateMetric>,
    modifier: Modifier = Modifier
) {
    if (heartRateData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No heart rate data available")
        }
        return
    }
    
    val modelProducer = remember { CartesianChartModelProducer() }
    
    LaunchedEffect(heartRateData) {
        val sortedData = heartRateData.sortedBy { it.timestamp }
        val hourOfDay = sortedData.map { 
            it.timestamp.atZone(ZoneId.systemDefault()).hour.toFloat()
        }
        val bpmValues = sortedData.map { it.bpm.toFloat() }
        
        modelProducer.runTransaction {
            lineSeries { series(hourOfDay, bpmValues) }
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
fun StatItem(label: String, value: String) {
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
