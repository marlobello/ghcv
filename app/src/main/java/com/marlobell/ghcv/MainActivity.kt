package com.marlobell.ghcv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.ui.navigation.Screen
import com.marlobell.ghcv.ui.navigation.bottomNavItems
import com.marlobell.ghcv.ui.screens.CurrentScreen
import com.marlobell.ghcv.ui.screens.DataScreen
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
    // Observe the availability state from HealthConnectManager
    val healthConnectAvailability by remember { healthConnectManager.availability }
    val healthConnectAvailable = healthConnectAvailability == HealthConnectClient.SDK_AVAILABLE
    
    var permissionsGranted by remember { mutableStateOf(false) }
    var checkingPermissions by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        Log.d("GHCV", "Checking Health Connect availability")
        healthConnectManager.checkAvailability()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationScreen(healthConnectManager: HealthConnectManager) {
    val navController = rememberNavController()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("GHCV") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Health Connect Permissions") },
                            onClick = {
                                showMenu = false
                                healthConnectManager.openHealthConnectSettings(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Show HC Data") },
                            onClick = {
                                showMenu = false
                                navController.navigate(Screen.Data.route)
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                bottomNavItems.forEach { screen ->
                    // Check if current destination matches this screen
                    // For routes with parameters, the destination.route will be the pattern
                    val isSelected = when {
                        // For Historical screen, check if destination route starts with "historical"
                        screen is Screen.Historical -> currentDestination?.route?.startsWith("historical") == true
                        // For other screens, exact match
                        else -> currentDestination?.route == screen.route
                    }
                    
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            // Use baseRoute for navigation to avoid issues with parameterized routes
                            val targetRoute = if (screen is Screen.Historical) {
                                screen.baseRoute  // Navigate to "historical" without parameters
                            } else {
                                screen.route
                            }
                            
                            Log.d("Navigation", "Bottom nav clicked: ${screen.title}, navigating to: $targetRoute, current route: ${currentDestination?.route}")
                            
                            navController.navigate(targetRoute) {
                                // Clear the back stack to the start destination
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = false  // Don't save state when popping
                                }
                                launchSingleTop = true
                                restoreState = false  // Don't restore state - always show fresh screen
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
                CurrentScreen(healthConnectManager, navController)
            }
            composable(
                route = Screen.Historical.route,
                arguments = listOf(
                    navArgument("date") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("expandCard") { 
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date")
                val expandCard = backStackEntry.arguments?.getString("expandCard")
                
                HistoricalScreen(
                    healthConnectManager = healthConnectManager,
                    initialDate = date,
                    initialExpandedCard = expandCard
                )
            }
            composable(Screen.Trends.route) {
                TrendsScreen(healthConnectManager)
            }
            composable(Screen.Data.route) {
                DataScreen(healthConnectManager)
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
