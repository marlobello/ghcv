package com.marlobell.ghcv.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Pushes fresh steps data to any placed Steps widgets.
 * Must be called from a foreground context (e.g. ViewModel / Activity) because
 * Health Connect does not allow background reads without the experimental
 * READ_HEALTH_DATA_IN_BACKGROUND permission.
 */
object StepsWidgetUpdater {

    suspend fun update(
        context: Context,
        currentSteps: Long,
        sevenDayAvg: Long,
        source: String?
    ) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(StepsWidget::class.java)
            if (glanceIds.isEmpty()) return

            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[StepsWidget.CURRENT_STEPS] = currentSteps
                    prefs[StepsWidget.SEVEN_DAY_AVG] = sevenDayAvg
                    if (source != null) {
                        prefs[StepsWidget.STEPS_SOURCE] = source
                    } else {
                        prefs.remove(StepsWidget.STEPS_SOURCE)
                    }
                    prefs[StepsWidget.HAS_PERMISSION] = true
                    prefs[StepsWidget.LAST_UPDATED] = System.currentTimeMillis().toString()
                }
                StepsWidget().update(context, glanceId)
            }
        } catch (e: Exception) {
            Log.w("StepsWidgetUpdater", "Failed to update Steps widget", e)
        }
    }
}
