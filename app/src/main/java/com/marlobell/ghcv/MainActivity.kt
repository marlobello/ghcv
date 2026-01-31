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
    
    when {
        !healthConnectAvailable.value -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                HealthConnectUnavailableScreen(Modifier.padding(innerPadding))
            }
        }
        !permissionsGranted.value -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                PermissionRequestScreen(
                    onRequestPermissions = {
                        permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
        else -> {
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
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permissions")
        }
    }
}
