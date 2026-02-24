package com.marlobell.ghcv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.model.SleepMetric
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.viewmodel.TrendPeriod
import com.marlobell.ghcv.ui.viewmodel.TrendsViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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
    data: List<Pair<LocalDate, SleepMetric?>>,
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

            SleepStagesChart(
                data = data,
                modifier = Modifier.fillMaxWidth()
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

    val dateFormatter = rememberDateFormatter(data.size)
    val labelSpacing = rememberLabelSpacing(data.size)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, value, _ ->
                    data.getOrNull(value.toInt())?.first?.format(dateFormatter) ?: ""
                },
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing })
            )
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

    val dateFormatter = rememberDateFormatter(data.size)
    val labelSpacing = rememberLabelSpacing(data.size)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = CartesianValueFormatter { _, value, _ ->
                    data.getOrNull(value.toInt())?.first?.format(dateFormatter) ?: ""
                },
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing })
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier
    )
}

@Composable
fun SleepStagesChart(
    data: List<Pair<LocalDate, SleepMetric?>>,
    modifier: Modifier = Modifier
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        if (data.isEmpty()) return@LaunchedEffect

        val xValues = data.indices.map { it.toFloat() }

        // Minutes per stage per night — falls back to total duration when no stage data
        fun stageMinutes(index: Int, vararg stageCodes: String): Float {
            val metric = data[index].second ?: return 0f
            return if (metric.stages.isEmpty()) 0f
            else metric.stages
                .filter { it.stage in stageCodes }
                .sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
                .toFloat()
        }
        fun unknownMinutes(index: Int): Float {
            val metric = data[index].second ?: return 0f
            return if (metric.stages.isEmpty()) (metric.durationMinutes).toFloat() else 0f
        }

        val awake   = xValues.map { stageMinutes(it.toInt(), "1") }
        val light   = xValues.map { stageMinutes(it.toInt(), "4", "2", "3") }
        val deep    = xValues.map { stageMinutes(it.toInt(), "5") }
        val rem     = xValues.map { stageMinutes(it.toInt(), "6") }
        val unknown = xValues.map { unknownMinutes(it.toInt()) }

        modelProducer.runTransaction {
            columnSeries {
                series(xValues, awake)
                series(xValues, light)
                series(xValues, deep)
                series(xValues, rem)
                series(xValues, unknown)
            }
        }
    }

    val columnThickness = 16.dp
    val awakeCol   = rememberLineComponent(fill = Fill(Color(0xFFE57373)), thickness = columnThickness)
    val lightCol   = rememberLineComponent(fill = Fill(Color(0xFF64B5F6)), thickness = columnThickness)
    val deepCol    = rememberLineComponent(fill = Fill(Color(0xFF1565C0)), thickness = columnThickness)
    val remCol     = rememberLineComponent(fill = Fill(Color(0xFF9575CD)), thickness = columnThickness)
    val unknownCol = rememberLineComponent(fill = Fill(Color(0xFFB0BEC5)), thickness = columnThickness)

    val dateFormatter = rememberDateFormatter(data.size)
    val labelSpacing  = rememberLabelSpacing(data.size)

    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        awakeCol, lightCol, deepCol, remCol, unknownCol
                    ),
                    mergeMode = { ColumnCartesianLayer.MergeMode.Stacked }
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        data.getOrNull(value.toInt())?.first?.format(dateFormatter) ?: ""
                    },
                    itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing })
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            SleepStageLegendItem(Color(0xFFE57373), "Awake")
            SleepStageLegendItem(Color(0xFF64B5F6), "Light")
            SleepStageLegendItem(Color(0xFF1565C0), "Deep")
            SleepStageLegendItem(Color(0xFF9575CD), "REM")
        }
    }
}

@Composable
private fun SleepStageLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun rememberDateFormatter(dataSize: Int): DateTimeFormatter {
    return remember(dataSize) {
        if (dataSize <= 14) DateTimeFormatter.ofPattern("EEE")
        else DateTimeFormatter.ofPattern("MMM d")
    }
}

private fun rememberLabelSpacing(dataSize: Int): Int {
    return when {
        dataSize <= 14 -> 1
        dataSize <= 31 -> 5
        else -> 14
    }
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
