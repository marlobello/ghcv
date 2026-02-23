package com.marlobell.ghcv.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class StepsWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StepsWidget()

    private val coroutineScope = MainScope()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        coroutineScope.launch {
            refreshAllWidgets(context)
        }
    }

    private suspend fun refreshAllWidgets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(StepsWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateStepsData(context, glanceId)
        }
    }

    private suspend fun updateStepsData(
        context: Context,
        glanceId: androidx.glance.GlanceId
    ) {
        val healthManager = HealthConnectManager(context)
        val hasPermission = healthManager.hasAllPermissions()

        var currentSteps = 0L
        var sevenDayAvg = 0L
        var stepsSource: String? = null

        if (hasPermission) {
            val repo = HealthConnectRepository(healthManager.getClient())

            try {
                val (steps, source) = repo.getTodaySteps()
                currentSteps = steps
                stepsSource = source
            } catch (e: Exception) {
                Log.w("StepsWidget", "Failed to fetch today's steps", e)
            }

            try {
                val dailySteps = (1..7).map { daysAgo ->
                    try {
                        repo.getStepsForDate(LocalDate.now().minusDays(daysAgo.toLong()))
                    } catch (e: Exception) {
                        0L
                    }
                }
                val total = dailySteps.sum()
                sevenDayAvg = if (total > 0L) total / 7L else 0L
            } catch (e: Exception) {
                Log.w("StepsWidget", "Failed to compute 7-day avg steps", e)
            }
        }

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[StepsWidget.CURRENT_STEPS] = currentSteps
            prefs[StepsWidget.SEVEN_DAY_AVG] = sevenDayAvg
            if (stepsSource != null) {
                prefs[StepsWidget.STEPS_SOURCE] = stepsSource
            } else {
                prefs.remove(StepsWidget.STEPS_SOURCE)
            }
            prefs[StepsWidget.HAS_PERMISSION] = hasPermission
            prefs[StepsWidget.LAST_UPDATED] = System.currentTimeMillis().toString()
        }

        glanceAppWidget.update(context, glanceId)
    }
}
