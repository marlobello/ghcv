package com.marlobell.ghcv.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Current : Screen("current", "Current", Icons.Default.Home)
    object Historical : Screen("historical", "Historical", Icons.Default.CalendarMonth)
    object Trends : Screen("trends", "Trends", Icons.Default.ShowChart)
}

val bottomNavItems = listOf(
    Screen.Current,
    Screen.Historical,
    Screen.Trends
)
