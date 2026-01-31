package com.marlobell.ghcv.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TrendPeriod(val days: Int, val label: String) {
    WEEK(7, "Week"),
    TWO_WEEKS(14, "2 Weeks"),
    MONTH(30, "Month"),
    THREE_MONTHS(90, "3 Months")
}

data class TrendData(
    val stepsTrend: List<Pair<LocalDate, Long>> = emptyList(),
    val heartRateTrend: List<Pair<LocalDate, Double>> = emptyList(),
    val sleepTrend: List<Pair<LocalDate, Long>> = emptyList(),
    val period: TrendPeriod = TrendPeriod.WEEK,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedMetric: String = "steps"
) {
    // Statistics for steps
    val avgSteps: Long
        get() = if (stepsTrend.isNotEmpty()) stepsTrend.map { it.second }.average().toLong() else 0
    val totalSteps: Long
        get() = stepsTrend.sumOf { it.second }
    val maxSteps: Long
        get() = stepsTrend.maxOfOrNull { it.second } ?: 0
    
    // Statistics for heart rate
    val avgHeartRate: Double
        get() = if (heartRateTrend.isNotEmpty()) heartRateTrend.map { it.second }.average() else 0.0
    val minHeartRate: Double
        get() = heartRateTrend.minOfOrNull { it.second } ?: 0.0
    val maxHeartRate: Double
        get() = heartRateTrend.maxOfOrNull { it.second } ?: 0.0
    
    // Statistics for sleep
    val avgSleep: Long
        get() = if (sleepTrend.isNotEmpty()) sleepTrend.map { it.second }.average().toLong() else 0
    val totalSleep: Long
        get() = sleepTrend.sumOf { it.second }
}

class TrendsViewModel(
    private val repository: HealthConnectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendData())
    val uiState: StateFlow<TrendData> = _uiState.asStateFlow()

    init {
        loadTrends(TrendPeriod.WEEK)
    }

    fun loadTrends(period: TrendPeriod) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                period = period,
                isLoading = true,
                error = null
            )
            
            try {
                val stepsTrend = repository.getStepsTrend(period.days)
                
                // Get heart rate trends (average per day)
                val heartRateTrend = mutableListOf<Pair<LocalDate, Double>>()
                for (i in 0 until period.days) {
                    val date = LocalDate.now().minusDays(i.toLong())
                    val avgHr = repository.getAverageHeartRateForDate(date)
                    if (avgHr != null) {
                        heartRateTrend.add(date to avgHr)
                    }
                }
                
                // Get sleep trends (duration per night)
                val sleepTrend = mutableListOf<Pair<LocalDate, Long>>()
                for (i in 0 until period.days) {
                    val date = LocalDate.now().minusDays(i.toLong())
                    val sleep = repository.getSleepForDate(date)
                    if (sleep != null) {
                        sleepTrend.add(date to sleep.durationMinutes)
                    }
                }

                _uiState.value = TrendData(
                    stepsTrend = stepsTrend,
                    heartRateTrend = heartRateTrend.reversed(),
                    sleepTrend = sleepTrend.reversed(),
                    period = period,
                    isLoading = false,
                    selectedMetric = _uiState.value.selectedMetric
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun changePeriod(period: TrendPeriod) {
        loadTrends(period)
    }
    
    fun selectMetric(metric: String) {
        _uiState.value = _uiState.value.copy(selectedMetric = metric)
    }
}
