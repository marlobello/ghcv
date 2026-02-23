package com.marlobell.ghcv.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Image
import com.marlobell.ghcv.R
import com.marlobell.ghcv.util.DataSourceFormatter
import java.text.NumberFormat

class StepsWidget : GlanceAppWidget() {

    companion object {
        val CURRENT_STEPS = longPreferencesKey("current_steps")
        val SEVEN_DAY_AVG = longPreferencesKey("seven_day_avg")
        val STEPS_SOURCE = stringPreferencesKey("steps_source")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val LAST_UPDATED = stringPreferencesKey("last_updated")
        val HAS_PERMISSION = booleanPreferencesKey("has_permission")

        const val VIEW_SIMPLE = "SIMPLE"
        const val VIEW_PROGRESS = "PROGRESS"
    }

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val currentSteps = prefs[CURRENT_STEPS] ?: 0L
            val sevenDayAvg = prefs[SEVEN_DAY_AVG] ?: 0L
            val stepsSource = prefs[STEPS_SOURCE]
            val viewMode = prefs[VIEW_MODE] ?: VIEW_SIMPLE
            val lastUpdatedMillis = prefs[LAST_UPDATED]?.toLongOrNull()
            val hasPermission = prefs[HAS_PERMISSION] ?: false

            GlanceTheme {
                WidgetContent(
                    hasPermission = hasPermission,
                    currentSteps = currentSteps,
                    sevenDayAvg = sevenDayAvg,
                    stepsSource = stepsSource,
                    viewMode = viewMode,
                    lastUpdatedMillis = lastUpdatedMillis
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        hasPermission: Boolean,
        currentSteps: Long,
        sevenDayAvg: Long,
        stepsSource: String?,
        viewMode: String,
        lastUpdatedMillis: Long?
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .clickable(actionRunCallback<ToggleViewAction>())
                .padding(12.dp)
        ) {
            if (!hasPermission) {
                NoPermissionContent()
            } else {
                when (viewMode) {
                    VIEW_PROGRESS -> ProgressView(
                        currentSteps = currentSteps,
                        sevenDayAvg = sevenDayAvg,
                        stepsSource = stepsSource,
                        lastUpdatedMillis = lastUpdatedMillis
                    )
                    else -> SimpleView(
                        currentSteps = currentSteps,
                        stepsSource = stepsSource,
                        lastUpdatedMillis = lastUpdatedMillis
                    )
                }
            }
        }
    }

    @Composable
    private fun SimpleView(
        currentSteps: Long,
        stepsSource: String?,
        lastUpdatedMillis: Long?
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_steps),
                    contentDescription = "Steps",
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Steps",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = formatSteps(currentSteps),
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (stepsSource != null) {
                Text(
                    text = "from ${DataSourceFormatter.format(stepsSource)}",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = formatLastUpdated(lastUpdatedMillis),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    @Composable
    private fun ProgressView(
        currentSteps: Long,
        sevenDayAvg: Long,
        stepsSource: String?,
        lastUpdatedMillis: Long?
    ) {
        val progress = if (sevenDayAvg > 0) {
            (currentSteps.toFloat() / sevenDayAvg.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val goalMet = sevenDayAvg > 0 && currentSteps >= sevenDayAvg

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_steps),
                    contentDescription = "Steps",
                    modifier = GlanceModifier.size(18.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Steps · 7-day avg",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = if (sevenDayAvg > 0) {
                    "${formatSteps(currentSteps)} / ${formatSteps(sevenDayAvg)}"
                } else {
                    formatSteps(currentSteps)
                },
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
            if (sevenDayAvg > 0) {
                Text(
                    text = "today / 7-day avg",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                ProgressBar(progress = progress, goalMet = goalMet)
                Spacer(modifier = GlanceModifier.height(4.dp))
                val percent = (progress * 100).toInt()
                Text(
                    text = if (goalMet) "✓ Goal reached ($percent%)" else "$percent% of avg",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (goalMet) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant
                    )
                )
            } else {
                Text(
                    text = "No 7-day average yet",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = formatLastUpdated(lastUpdatedMillis),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    @Composable
    private fun ProgressBar(progress: Float, goalMet: Boolean) {
        val filledSegments = (progress * 10).toInt().coerceIn(0, 10)
        val fillColor = if (goalMet) GlanceTheme.colors.primary else GlanceTheme.colors.secondary
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            repeat(10) { i ->
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(10.dp)
                        .padding(horizontal = 1.dp)
                        .cornerRadius(3.dp)
                        .background(if (i < filledSegments) fillColor else GlanceTheme.colors.surfaceVariant)
                ) {}
            }
        }
    }

    @Composable
    private fun NoPermissionContent() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_steps),
                contentDescription = "Steps",
                modifier = GlanceModifier.size(24.dp)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "Open GHCV app to grant\nHealth Connect permissions",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }

    private fun formatSteps(steps: Long): String =
        NumberFormat.getNumberInstance().format(steps)

    private fun formatLastUpdated(millis: Long?): String {
        if (millis == null) return "Tap to refresh"
        val diffMs = System.currentTimeMillis() - millis
        return when {
            diffMs < 60_000L -> "Updated just now"
            diffMs < 3_600_000L -> "Updated ${diffMs / 60_000} min ago"
            else -> "Updated ${diffMs / 3_600_000} hr ago"
        }
    }
}
