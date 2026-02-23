package com.marlobell.ghcv.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.MutablePreferences

/**
 * Toggles the Steps widget between Simple and Progress views when the widget is tapped.
 */
class ToggleViewAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            val current = prefs[StepsWidget.VIEW_MODE] ?: StepsWidget.VIEW_SIMPLE
            prefs[StepsWidget.VIEW_MODE] = if (current == StepsWidget.VIEW_SIMPLE) {
                StepsWidget.VIEW_PROGRESS
            } else {
                StepsWidget.VIEW_SIMPLE
            }
        }
        StepsWidget().update(context, glanceId)
    }
}
