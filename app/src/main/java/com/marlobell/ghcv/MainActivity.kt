package com.marlobell.ghcv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.theme.GhcvTheme
import com.marlobell.ghcv.ui.viewmodel.CurrentViewModel

class MainActivity : ComponentActivity() {
    
    private lateinit var healthConnectManager: HealthConnectManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        healthConnectManager = HealthConnectManager(this)
        
        enableEdgeToEdge()
        setContent {
            GhcvTheme {
                HealthConnectApp(healthConnectManager)
            }
        }
    }
}

@Composable
fun HealthConnectApp(healthConnectManager: HealthConnectManager) {
    val healthConnectAvailable = remember { mutableStateOf(false) }
    val permissionsGranted = remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        healthConnectAvailable.value = healthConnectManager.checkAvailability()
        if (healthConnectAvailable.value) {
            permissionsGranted.value = healthConnectManager.hasAllPermissions()
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.createPermissionRequestContract()
    ) { granted ->
        permissionsGranted.value = granted.containsAll(HealthConnectManager.PERMISSIONS)
    }
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when {
            !healthConnectAvailable.value -> {
                HealthConnectUnavailableScreen(Modifier.padding(innerPadding))
            }
            !permissionsGranted.value -> {
                PermissionRequestScreen(
                    onRequestPermissions = {
                        permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                CurrentHealthScreen(
                    healthConnectManager = healthConnectManager,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun HealthConnectUnavailableScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Health Connect Not Available",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app requires Health Connect which is not available on this device.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app needs permission to read your health data from Health Connect.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}

@Composable
fun CurrentHealthScreen(
    healthConnectManager: HealthConnectManager,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: CurrentViewModel = viewModel(
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Current Health Data",
            style = MaterialTheme.typography.headlineMedium
        )
        
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.error != null) {
            Text(
                text = "Error: ${uiState.error}",
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = { viewModel.refresh() }) {
                Text("Retry")
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Steps Today",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${uiState.steps}",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
            
            uiState.heartRate?.let { hr ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Latest Heart Rate",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$hr bpm",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }
            }
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Calories",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${String.format("%.0f", uiState.activeCalories)} kcal",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }
            
            Button(
                onClick = { viewModel.refresh() },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Refresh")
            }
        }
    }
}
