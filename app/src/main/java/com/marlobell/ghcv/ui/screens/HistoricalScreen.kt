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
import com.marlobell.ghcv.ui.components.SleepStageChart
import com.marlobell.ghcv.ui.model.MetricCardIds
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
    modifier: Modifier = Modifier,
    initialDate: String? = null,
    initialExpandedCard: String? = null
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: HistoricalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HistoricalViewModel(repository, initialExpandedCard) as T
            }
        }
    )
    
    // Handle initial date if provided
    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            try {
                val date = LocalDate.parse(initialDate)
                viewModel.loadDataForDate(date)
            } catch (e: Exception) {
                // Invalid date format, ignore and use default
            }
        }
    }
    
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
                isExpanded = uiState.expandedSections.contains(MetricCardIds.STEPS),
                onToggle = { viewModel.toggleSection(MetricCardIds.STEPS) },
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
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.HEART_RATE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.HEART_RATE) }
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
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.SLEEP),
                    onToggle = { viewModel.toggleSection(MetricCardIds.SLEEP) }
                ) {
                    Column {
                        Text(
                            text = "Sleep Timeline",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (sleep.stages.isNotEmpty()) {
                            SleepStageChart(
                                stages = sleep.stages,
                                modifier = Modifier.fillMaxWidth()
                            )
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
            
            // Active Calories Card
            if (uiState.activeCalories > 0) {
                ExpandableMetricCard(
                    title = "Active Calories",
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "${String.format(Locale.US, "%.0f", uiState.activeCalories)} kcal",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.ACTIVE_CALORIES),
                    onToggle = { viewModel.toggleSection(MetricCardIds.ACTIVE_CALORIES) }
                ) {
                    Text(
                        text = "Total active calories burned throughout the day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Blood Pressure Card
            if (uiState.bloodPressureData.isNotEmpty()) {
                val avgSystolic = uiState.bloodPressureData.map { it.systolic }.average()
                val avgDiastolic = uiState.bloodPressureData.map { it.diastolic }.average()
                
                ExpandableMetricCard(
                    title = "Blood Pressure",
                    icon = Icons.Filled.MonitorHeart,
                    value = "${String.format(Locale.US, "%.0f", avgSystolic)}/${String.format(Locale.US, "%.0f", avgDiastolic)} mmHg",
                    subtitle = "${uiState.bloodPressureData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.BLOOD_PRESSURE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.BLOOD_PRESSURE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.bloodPressureData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", reading.systolic)}/${String.format(Locale.US, "%.0f", reading.diastolic)} mmHg",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Blood Glucose Card
            if (uiState.bloodGlucoseData.isNotEmpty()) {
                val avgGlucose = uiState.bloodGlucoseData.map { it.mgDl }.average()
                
                ExpandableMetricCard(
                    title = "Blood Glucose",
                    icon = Icons.Filled.Bloodtype,
                    value = "${String.format(Locale.US, "%.0f", avgGlucose)} mg/dL",
                    subtitle = "${uiState.bloodGlucoseData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.BLOOD_GLUCOSE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.BLOOD_GLUCOSE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.bloodGlucoseData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.0f", reading.mgDl)} mg/dL",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Body Temperature Card
            if (uiState.bodyTemperatureData.isNotEmpty()) {
                val avgTemp = uiState.bodyTemperatureData.map { it.celsius }.average()
                
                ExpandableMetricCard(
                    title = "Body Temperature",
                    icon = Icons.Filled.Thermostat,
                    value = "${String.format(Locale.US, "%.1f", avgTemp)}°C",
                    subtitle = "${uiState.bodyTemperatureData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.BODY_TEMPERATURE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.BODY_TEMPERATURE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.bodyTemperatureData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", reading.celsius)}°C",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Oxygen Saturation Card
            if (uiState.oxygenSaturationData.isNotEmpty()) {
                val avgOxygen = uiState.oxygenSaturationData.map { it.percentage }.average()
                
                ExpandableMetricCard(
                    title = "Oxygen Saturation",
                    icon = Icons.Filled.Air,
                    value = "${String.format(Locale.US, "%.1f", avgOxygen)}%",
                    subtitle = "${uiState.oxygenSaturationData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.OXYGEN_SATURATION),
                    onToggle = { viewModel.toggleSection(MetricCardIds.OXYGEN_SATURATION) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.oxygenSaturationData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", reading.percentage)}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Resting Heart Rate Card
            if (uiState.restingHeartRateData.isNotEmpty()) {
                val avgRhr = uiState.restingHeartRateData.map { it.bpm.toDouble() }.average()
                
                ExpandableMetricCard(
                    title = "Resting Heart Rate",
                    icon = Icons.Filled.Favorite,
                    value = "${String.format(Locale.US, "%.0f", avgRhr)} bpm",
                    subtitle = "${uiState.restingHeartRateData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.RESTING_HEART_RATE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.RESTING_HEART_RATE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.restingHeartRateData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${reading.bpm} bpm",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Respiratory Rate Card
            if (uiState.respiratoryRateData.isNotEmpty()) {
                val avgRespRate = uiState.respiratoryRateData.map { it.breathsPerMinute }.average()
                
                ExpandableMetricCard(
                    title = "Respiratory Rate",
                    icon = Icons.Filled.Air,
                    value = "${String.format(Locale.US, "%.1f", avgRespRate)} breaths/min",
                    subtitle = "${uiState.respiratoryRateData.size} readings",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.RESPIRATORY_RATE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.RESPIRATORY_RATE) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Readings",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        uiState.respiratoryRateData.sortedByDescending { it.timestamp }.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", reading.breathsPerMinute)} breaths/min",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = reading.timestamp
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Distance Card
            if (uiState.distance > 0) {
                ExpandableMetricCard(
                    title = "Distance",
                    icon = Icons.Filled.Place,
                    value = "${String.format(Locale.US, "%.2f", uiState.distance / 1000)} km",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.DISTANCE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.DISTANCE) }
                ) {
                    Text(
                        text = "Total distance traveled throughout the day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Exercise Sessions Card
            if (uiState.exerciseSessions > 0) {
                ExpandableMetricCard(
                    title = "Exercise Sessions",
                    icon = Icons.Filled.FitnessCenter,
                    value = "${uiState.exerciseSessions}",
                    subtitle = "sessions completed",
                    isExpanded = uiState.expandedSections.contains(MetricCardIds.EXERCISE),
                    onToggle = { viewModel.toggleSection(MetricCardIds.EXERCISE) }
                ) {
                    Text(
                        text = "Total exercise sessions recorded for the day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        // Convert UTC epoch millis to LocalDate using epoch day to avoid timezone issues
                        val selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC"))
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
