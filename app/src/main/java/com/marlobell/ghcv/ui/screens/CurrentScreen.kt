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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.data.model.*
import com.marlobell.ghcv.ui.components.CollapsibleSection
import com.marlobell.ghcv.ui.components.SmallMetricCard
import com.marlobell.ghcv.ui.model.MetricCardIds
import com.marlobell.ghcv.ui.navigation.Screen
import com.marlobell.ghcv.ui.theme.*
import com.marlobell.ghcv.ui.viewmodel.CurrentViewModel
import com.marlobell.ghcv.util.DataSourceFormatter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CurrentScreen(
    healthConnectManager: HealthConnectManager,
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val changesTokenStorage = remember {
        com.marlobell.ghcv.data.ChangesTokenStorage(context)
    }
    
    val viewModel: CurrentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CurrentViewModel(repository, healthConnectManager, changesTokenStorage) as T
            }
        }
    )
    
    val healthData by viewModel.healthData.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Pause auto-refresh when the screen is not visible; resume and refresh on return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_PAUSE  -> viewModel.onAppBackgrounded()
                else                      -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    
    // State for collapsible sections
    var permissionSectionExpanded by remember { mutableStateOf(false) }
    var noDataSectionExpanded by remember { mutableStateOf(false) }
    
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
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                healthData.lastUpdated?.let { timestamp ->
                    val minutesAgo = Duration.between(timestamp, Instant.now()).toMinutes()
                    Text(
                        text = if (minutesAgo < 1) "Just now" else "$minutesAgo min ago",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh data"
                    )
                }
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
                // SECTION 1: Active Data (no header) - Show metrics with data
                if (healthData.steps > 0) {
                    HealthMetricCard(
                        title = "Steps",
                        value = "${healthData.steps}",
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        subtitle = healthData.stepsSource?.let { "from ${DataSourceFormatter.format(it)}" },
                        comparison = healthData.stepsComparison,
                        containerColor = ActivityColors.containerColor(),
                        contentColor = ActivityColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.STEPS
                                )
                            )
                        }
                    )
                }
            
                healthData.heartRate?.let { hr ->
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    val timeStr = healthData.heartRateTimestamp?.atZone(ZoneId.systemDefault())
                        ?.format(timeFormatter) ?: ""
                    
                    val subtitle = buildString {
                        if (timeStr.isNotEmpty()) append("at $timeStr")
                        healthData.heartRateSource?.let {
                            if (isNotEmpty()) append(" • ")
                            append("from ${DataSourceFormatter.format(it)}")
                        }
                    }.takeIf { it.isNotEmpty() }
                    
                    HealthMetricCard(
                        title = "Heart Rate",
                        value = "$hr",
                        unit = "bpm",
                        icon = Icons.Filled.Favorite,
                        subtitle = subtitle,
                        comparison = healthData.heartRateComparison,
                        containerColor = CardiovascularColors.containerColor(),
                        contentColor = CardiovascularColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.HEART_RATE
                                )
                            )
                        }
                    )
                }
                
                healthData.sleepLastNight?.let { sleepMinutes ->
                    val hours = sleepMinutes / 60
                    val minutes = sleepMinutes % 60
                    
                    val subtitle = healthData.sleepSource?.let { 
                        "from ${DataSourceFormatter.format(it)}" 
                    }
                    
                    HealthMetricCard(
                        title = "Sleep Last Night",
                        value = "${hours}h ${minutes}m",
                        icon = Icons.Filled.Bedtime,
                        subtitle = subtitle,
                        comparison = healthData.sleepComparison,
                        containerColor = SleepColors.containerColor(),
                        contentColor = SleepColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.SLEEP
                                )
                            )
                        }
                    )
                }
                
                if (healthData.activeCalories > 0) {
                    HealthMetricCard(
                        title = "Active Calories",
                        value = String.format(Locale.US, "%.0f", healthData.activeCalories),
                        unit = "kcal",
                        icon = Icons.Filled.LocalFireDepartment,
                        subtitle = healthData.activeCaloriesSource?.let { "from ${DataSourceFormatter.format(it)}" },
                        comparison = healthData.caloriesComparison,
                        containerColor = ActivityColors.containerColor(),
                        contentColor = ActivityColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.ACTIVE_CALORIES
                                )
                            )
                        }
                    )
                }
                
                // Vitals with data
                if (healthData.bloodPressure.hasData) {
                    healthData.bloodPressure.latest?.let { bp ->
                        VitalsMetricCard(
                            title = "Blood Pressure",
                            value = "${bp.systolic.toInt()}/${bp.diastolic.toInt()}",
                            unit = "mmHg",
                            icon = Icons.Filled.Favorite,
                            timestamp = healthData.bloodPressure.latestTimestamp,
                            source = healthData.bloodPressureSource,
                            dailyStats = if (healthData.bloodPressure.readingCount > 1) {
                                "Avg: ${healthData.bloodPressure.dailyAvg?.toInt() ?: "--"} | " +
                                "Min: ${healthData.bloodPressure.dailyMin?.toInt() ?: "--"} | " +
                                "Max: ${healthData.bloodPressure.dailyMax?.toInt() ?: "--"}"
                            } else null,
                            comparison = healthData.bloodPressureSystolicComparison,
                            containerColor = CardiovascularColors.containerColor(),
                            contentColor = CardiovascularColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.BLOOD_PRESSURE
                                    )
                                )
                            }
                        )
                    }
                }
                
                if (healthData.bloodGlucose.hasData) {
                    healthData.bloodGlucose.latest?.let { glucose ->
                        VitalsMetricCard(
                            title = "Blood Glucose",
                            value = String.format(Locale.US, "%.0f", glucose),
                            unit = "mg/dL",
                            icon = Icons.Filled.Bloodtype,
                            timestamp = healthData.bloodGlucose.latestTimestamp,
                            source = healthData.bloodGlucoseSource,
                            dailyStats = if (healthData.bloodGlucose.readingCount > 1) {
                                "Avg: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyAvg ?: 0.0)} | " +
                                "Min: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyMin ?: 0.0)} | " +
                                "Max: ${String.format(Locale.US, "%.0f", healthData.bloodGlucose.dailyMax ?: 0.0)}"
                            } else null,
                            comparison = healthData.bloodGlucoseComparison,
                            containerColor = MetabolicColors.containerColor(),
                            contentColor = MetabolicColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.BLOOD_GLUCOSE
                                    )
                                )
                            }
                        )
                    }
                }
                
                if (healthData.bodyTemperature.hasData) {
                    healthData.bodyTemperature.latest?.let { temp ->
                        VitalsMetricCard(
                            title = "Body Temperature",
                            value = String.format(Locale.US, "%.1f", temp),
                            unit = "°C",
                            icon = Icons.Filled.Thermostat,
                            timestamp = healthData.bodyTemperature.latestTimestamp,
                            source = healthData.bodyTemperatureSource,
                            dailyStats = if (healthData.bodyTemperature.readingCount > 1) {
                                "Avg: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyAvg ?: 0.0)} | " +
                                "Min: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyMin ?: 0.0)} | " +
                                "Max: ${String.format(Locale.US, "%.1f", healthData.bodyTemperature.dailyMax ?: 0.0)}"
                            } else null,
                            comparison = healthData.bodyTemperatureComparison,
                            containerColor = MetabolicColors.containerColor(),
                            contentColor = MetabolicColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.BODY_TEMPERATURE
                                    )
                                )
                            }
                        )
                    }
                }
                
                if (healthData.oxygenSaturation.hasData) {
                    healthData.oxygenSaturation.latest?.let { spo2 ->
                        VitalsMetricCard(
                            title = "Oxygen Saturation",
                            value = String.format(Locale.US, "%.0f", spo2),
                            unit = "%",
                            icon = Icons.Filled.Air,
                            timestamp = healthData.oxygenSaturation.latestTimestamp,
                            source = healthData.oxygenSaturationSource,
                            dailyStats = if (healthData.oxygenSaturation.readingCount > 1) {
                                "Avg: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyAvg ?: 0.0)} | " +
                                "Min: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyMin ?: 0.0)} | " +
                                "Max: ${String.format(Locale.US, "%.0f", healthData.oxygenSaturation.dailyMax ?: 0.0)}"
                            } else null,
                            comparison = healthData.oxygenSaturationComparison,
                            containerColor = RespiratoryColors.containerColor(),
                            contentColor = RespiratoryColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.OXYGEN_SATURATION
                                    )
                                )
                            }
                        )
                    }
                }
                
                if (healthData.restingHeartRate.hasData) {
                    healthData.restingHeartRate.latest?.let { rhr ->
                        VitalsMetricCard(
                            title = "Resting Heart Rate",
                            value = "$rhr",
                            unit = "bpm",
                            icon = Icons.Filled.MonitorHeart,
                            timestamp = healthData.restingHeartRate.latestTimestamp,
                            source = healthData.restingHeartRateSource,
                            dailyStats = if (healthData.restingHeartRate.readingCount > 1) {
                                "Avg: ${healthData.restingHeartRate.dailyAvg?.toInt() ?: "--"} | " +
                                "Min: ${healthData.restingHeartRate.dailyMin?.toInt() ?: "--"} | " +
                                "Max: ${healthData.restingHeartRate.dailyMax?.toInt() ?: "--"}"
                            } else null,
                            comparison = healthData.restingHeartRateComparison,
                            containerColor = CardiovascularColors.containerColor(),
                            contentColor = CardiovascularColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.RESTING_HEART_RATE
                                    )
                                )
                            }
                        )
                    }
                }
                
                if (healthData.respiratoryRate.hasData) {
                    healthData.respiratoryRate.latest?.let { rr ->
                        VitalsMetricCard(
                            title = "Respiratory Rate",
                            value = String.format(Locale.US, "%.0f", rr),
                            unit = "breaths/min",
                            icon = Icons.Filled.Air,
                            timestamp = healthData.respiratoryRate.latestTimestamp,
                            source = healthData.respiratoryRateSource,
                            dailyStats = if (healthData.respiratoryRate.readingCount > 1) {
                                "Avg: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyAvg ?: 0.0)} | " +
                                "Min: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyMin ?: 0.0)} | " +
                                "Max: ${String.format(Locale.US, "%.0f", healthData.respiratoryRate.dailyMax ?: 0.0)}"
                            } else null,
                            comparison = healthData.respiratoryRateComparison,
                            containerColor = RespiratoryColors.containerColor(),
                            contentColor = RespiratoryColors.contentColor(),
                            onClick = {
                                navController.navigate(
                                    Screen.Historical.createRoute(
                                        date = LocalDate.now().toString(),
                                        expandCard = MetricCardIds.RESPIRATORY_RATE
                                    )
                                )
                            }
                        )
                    }
                }
                
                // Distance Card
                if (healthData.distance > 0) {
                    HealthMetricCard(
                        title = "Distance",
                        value = String.format(Locale.US, "%.2f", healthData.distance / 1000),
                        unit = "km",
                        icon = Icons.Filled.Place,
                        containerColor = ActivityColors.containerColor(),
                        contentColor = ActivityColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.DISTANCE
                                )
                            )
                        }
                    )
                }
                
                // Exercise Sessions Card
                if (healthData.exerciseSessions > 0) {
                    HealthMetricCard(
                        title = "Exercise Sessions",
                        value = "${healthData.exerciseSessions}",
                        unit = if (healthData.exerciseSessions == 1) "session" else "sessions",
                        icon = Icons.Filled.FitnessCenter,
                        containerColor = ActivityColors.containerColor(),
                        contentColor = ActivityColors.contentColor(),
                        onClick = {
                            navController.navigate(
                                Screen.Historical.createRoute(
                                    date = LocalDate.now().toString(),
                                    expandCard = MetricCardIds.EXERCISE
                                )
                            )
                        }
                    )
                }
                
                // SECTION 2: Permission not granted (collapsible)
                if (healthData.metricsNoPermission.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CollapsibleSection(
                        title = "Permission not granted",
                        itemCount = healthData.metricsNoPermission.size,
                        isExpanded = permissionSectionExpanded,
                        onToggleExpanded = { permissionSectionExpanded = !permissionSectionExpanded }
                    ) {
                        healthData.metricsNoPermission.forEach { metric ->
                            SmallMetricCard(
                                icon = metric.icon,
                                title = metric.displayName,
                                message = "Tap to grant permission",
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = {
                                    healthConnectManager.openHealthConnectSettings(context)
                                }
                            )
                        }
                    }
                }
                
                // SECTION 3: No records found (collapsible)
                if (healthData.metricsNoData.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CollapsibleSection(
                        title = "No records found",
                        itemCount = healthData.metricsNoData.size,
                        isExpanded = noDataSectionExpanded,
                        onToggleExpanded = { noDataSectionExpanded = !noDataSectionExpanded }
                    ) {
                        healthData.metricsNoData.forEach { metric ->
                            SmallMetricCard(
                                icon = metric.icon,
                                title = metric.displayName,
                                message = "No data available",
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                onClick = null
                            )
                        }
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
    comparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        onClick = onClick ?: {}
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
            
            // Show comparison if available, otherwise show trend
            comparison?.let { comp ->
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = comp.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${comp.value} ${comp.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    comp.difference?.let { diff ->
                        val diffColor = when (comp.isPositive) {
                            true -> MaterialTheme.colorScheme.tertiary
                            false -> MaterialTheme.colorScheme.error
                            null -> contentColor.copy(alpha = 0.6f)
                        }
                        Text(
                            text = "${diff} (${comp.percentage})",
                            style = MaterialTheme.typography.bodySmall,
                            color = diffColor
                        )
                    }
                }
            } ?: trend?.let { trendValue ->
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
    source: String? = null,
    dailyStats: String? = null,
    comparison: com.marlobell.ghcv.ui.model.MetricComparison? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        onClick = onClick ?: {}
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
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
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
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                timestamp?.let {
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    val timeStr = it.atZone(ZoneId.systemDefault()).format(timeFormatter)
                    
                    val subtitle = buildString {
                        append("at $timeStr")
                        source?.let {
                            append(" • from ${DataSourceFormatter.format(it)}")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                } ?: source?.let {
                    // If no timestamp but have source, show just the source
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "from ${DataSourceFormatter.format(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
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
            
            // Show comparison if available
            comparison?.let { comp ->
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = comp.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${comp.value} ${comp.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    comp.difference?.let { diff ->
                        val diffColor = when (comp.isPositive) {
                            true -> MaterialTheme.colorScheme.tertiary
                            false -> MaterialTheme.colorScheme.error
                            null -> contentColor.copy(alpha = 0.6f)
                        }
                        Text(
                            text = "${diff} (${comp.percentage})",
                            style = MaterialTheme.typography.bodySmall,
                            color = diffColor
                        )
                    }
                }
            }
        }
    }
}