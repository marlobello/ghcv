package com.marlobell.ghcv.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.health.connect.client.records.StepsRecord
import com.marlobell.ghcv.data.HealthConnectManager
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import com.marlobell.ghcv.widget.fetcher.StepsFetcher

class StepsWidgetReceiver : HealthWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = StepsWidget()
    override val widgetClass: Class<out GlanceAppWidget> = StepsWidget::class.java
    override val logTag = "StepsWidget"

    override suspend fun doUpdate(
        context: Context,
        healthManager: HealthConnectManager,
        glanceId: GlanceId
    ) {
        val hasPermission = healthManager.hasPermissionFor(StepsRecord::class)

        var currentSteps = 0L
        var sevenDayAvg = 0L
        var stepsSource: String? = null

        if (hasPermission) {
            val repo = HealthConnectRepository(healthManager.getClient())
            val (steps, avg, source) = StepsFetcher.fetch(repo)
            currentSteps = steps
            sevenDayAvg = avg
            stepsSource = source
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

