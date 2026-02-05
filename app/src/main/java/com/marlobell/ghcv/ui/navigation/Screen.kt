package com.marlobell.ghcv.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Current : Screen("current", "Current", Icons.Filled.Home)
    object Historical : Screen("historical", "Historical", Icons.Filled.CalendarMonth)
    object Trends : Screen("trends", "Trends", Icons.AutoMirrored.Filled.ShowChart)
}

val bottomNavItems = listOf(
    Screen.Current,
    Screen.Historical,
    Screen.Trends
)
