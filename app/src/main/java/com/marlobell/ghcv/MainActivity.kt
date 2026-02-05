package com.marlobell.ghcv

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.ui.navigation.Screen
import com.marlobell.ghcv.ui.navigation.bottomNavItems
import com.marlobell.ghcv.ui.screens.CurrentScreen
import com.marlobell.ghcv.ui.screens.HistoricalScreen
import com.marlobell.ghcv.ui.screens.TrendsScreen
import com.marlobell.ghcv.ui.theme.GhcvTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var healthConnectManager: HealthConnectManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("GHCV", "MainActivity onCreate")
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
    var healthConnectAvailable by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var checkingPermissions by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        Log.d("GHCV", "Checking Health Connect availability")
        healthConnectAvailable = healthConnectManager.checkAvailability()
        Log.d("GHCV", "Health Connect available: $healthConnectAvailable")
        if (healthConnectAvailable) {
            // Try to register with Health Connect first
            Log.d("GHCV", "Attempting to register with Health Connect...")
            healthConnectManager.triggerHealthConnectRegistration()
            
            // Then check permissions
            permissionsGranted = healthConnectManager.hasAllPermissions()
            Log.d("GHCV", "Permissions granted: $permissionsGranted")
        }
        checkingPermissions = false
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthConnectManager.createPermissionRequestContract()
    ) { granted ->
        Log.d("GHCV", "Permission launcher result: $granted")
        // After permission request, recheck if all permissions are granted
        checkingPermissions = true
    }
    
    val manualLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("GHCV", "Manual launcher result code: ${result.resultCode}")
        checkingPermissions = true
    }
    
    // Recheck permissions after permission launcher completes
    LaunchedEffect(checkingPermissions) {
        if (checkingPermissions && healthConnectAvailable) {
            Log.d("GHCV", "Rechecking permissions...")
            permissionsGranted = healthConnectManager.hasAllPermissions()
            Log.d("GHCV", "Permissions now: $permissionsGranted")
            checkingPermissions = false
        }
    }
    
    when {
        !healthConnectAvailable -> {
            Log.d("GHCV", "Showing unavailable screen")
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                HealthConnectUnavailableScreen(Modifier.padding(innerPadding))
            }
        }
        !permissionsGranted -> {
            Log.d("GHCV", "Showing permission request screen")
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                PermissionRequestScreen(
                    onRequestPermissions = {
                        Log.d("GHCV", "Grant Permissions button clicked!")
                        try {
                            permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                            Log.d("GHCV", "Permission launcher launched successfully")
                        } catch (e: Exception) {
                            Log.e("GHCV", "Error launching permission request", e)
                        }
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
        else -> {
            Log.d("GHCV", "Showing main navigation")
            MainNavigationScreen(healthConnectManager)
        }
    }
}

@Composable
fun MainNavigationScreen(healthConnectManager: HealthConnectManager) {
    val navController = rememberNavController()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Current.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Current.route) {
                CurrentScreen(healthConnectManager)
            }
            composable(Screen.Historical.route) {
                HistoricalScreen(healthConnectManager)
            }
            composable(Screen.Trends.route) {
                TrendsScreen(healthConnectManager)
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
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}
