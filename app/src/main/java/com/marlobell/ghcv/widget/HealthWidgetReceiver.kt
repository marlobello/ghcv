package com.marlobell.ghcv.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.marlobell.ghcv.data.HealthConnectManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Abstract base class for all GHCV home-screen widgets.
 *
 * Handles the common boilerplate shared by every widget receiver:
 * - A [MainScope] coroutine scope for async work
 * - The [onUpdate] → background-permission guard → Glance-ID loop pattern
 *
 * Subclasses only need to implement [doUpdate] with their specific data-fetch
 * and widget-state logic.
 *
 * To add a new widget:
 * 1. Create a `widget/fetcher/[Metric]Fetcher.kt` data helper
 * 2. Create a `[Metric]Widget : GlanceAppWidget` composable
 * 3. Create a `[Metric]WidgetReceiver : HealthWidgetReceiver()` implementing [doUpdate]
 */
abstract class HealthWidgetReceiver : GlanceAppWidgetReceiver() {

    protected val coroutineScope = MainScope()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        coroutineScope.launch {
            val healthManager = HealthConnectManager(context)

            // Health Connect blocks reads from background contexts on Android 14+ unless
            // READ_HEALTH_DATA_IN_BACKGROUND is granted. If it isn't, bail out and let the
            // last data pushed by the foreground app remain visible.
            if (!healthManager.hasBackgroundReadPermission()) {
                Log.d(logTag, "Background read not granted; retaining last foreground data")
                return@launch
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(widgetClass)
            glanceIds.forEach { glanceId ->
                doUpdate(context, healthManager, glanceId)
            }
        }
    }

    /**
     * Called once per placed widget instance when an update is triggered.
     * Implementations should fetch data and push it to widget state.
     *
     * The background-permission check has already passed before this is called.
     *
     * @param context       Application context
     * @param healthManager A [HealthConnectManager] instance for this context
     * @param glanceId      The [GlanceId] of the specific widget instance to update
     */
    protected abstract suspend fun doUpdate(
        context: Context,
        healthManager: HealthConnectManager,
        glanceId: GlanceId
    )

    /** Used for log tag; defaults to the concrete class simple name. */
    protected open val logTag: String get() = this::class.java.simpleName

    /** The [GlanceAppWidget] class this receiver manages; used to enumerate placed instances. */
    protected abstract val widgetClass: Class<out GlanceAppWidget>
}
