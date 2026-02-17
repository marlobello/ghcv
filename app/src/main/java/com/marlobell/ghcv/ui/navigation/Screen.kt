package com.marlobell.ghcv.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    // Base route without parameters for navigation bar matching
    open val baseRoute: String get() = route
    
    object Current : Screen("current", "Current", Icons.Filled.Home)
    object Historical : Screen(
        "historical?date={date}&expandCard={expandCard}&scrollToCard={scrollToCard}", 
        "Historical", 
        Icons.Filled.CalendarMonth
    ) {
        override val baseRoute: String = "historical"
        
        fun createRoute(
            date: String? = null,
            expandCard: String? = null,
            scrollToCard: String? = null
        ): String {
            val params = mutableListOf<String>()
            date?.let { params.add("date=$it") }
            expandCard?.let { params.add("expandCard=$it") }
            scrollToCard?.let { params.add("scrollToCard=$it") }
            
            return if (params.isEmpty()) {
                "historical"
            } else {
                "historical?${params.joinToString("&")}"
            }
        }
    }
    object Trends : Screen("trends", "Trends", Icons.AutoMirrored.Filled.ShowChart)
    object Data : Screen("data", "Data", Icons.Filled.Home)
}

val bottomNavItems = listOf(
    Screen.Current,
    Screen.Historical,
    Screen.Trends
)
