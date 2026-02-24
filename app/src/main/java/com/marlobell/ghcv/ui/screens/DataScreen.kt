package com.marlobell.ghcv.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.ui.viewmodel.DataViewModel

@Composable
fun DataScreen(
    healthConnectManager: HealthConnectManager,
    modifier: Modifier = Modifier
) {
    val repository = remember {
        HealthConnectRepository(healthConnectManager.getClient())
    }
    
    val viewModel: DataViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DataViewModel(repository, healthConnectManager) as T
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }
    
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.loadData()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Health Connect Data",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.jsonData.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.jsonData,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
