package com.marlobell.ghcv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.data.model.SleepStage
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SleepStageChart(
    stages: List<SleepStage>,
    modifier: Modifier = Modifier
) {
    if (stages.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sleep stage data available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    val sortedStages = stages.sortedBy { it.startTime }
    val sleepStart = sortedStages.first().startTime
    val sleepEnd = sortedStages.last().endTime
    val totalDuration = Duration.between(sleepStart, sleepEnd).toMinutes()
    
    // Pre-calculate colors outside of Canvas and log stage names for debugging
    val stageColors = sortedStages.map { stage ->
        android.util.Log.d("SleepStageChart", "Stage: ${stage.stage}")
        getStageColor(stage.stage)
    }
    
    Column(modifier = modifier) {
        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(sleepStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(sleepEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Visual timeline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF37474F)) // Dark gray background for better contrast
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 14.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                sortedStages.forEachIndexed { index, stage ->
                    val stageStart = Duration.between(sleepStart, stage.startTime).toMinutes()
                    val stageDuration = Duration.between(stage.startTime, stage.endTime).toMinutes()
                    
                    val startX = (stageStart.toFloat() / totalDuration) * size.width
                    val width = (stageDuration.toFloat() / totalDuration) * size.width
                    
                    val stageLevel = getStageLevel(stage.stage)
                    // Map negative values (-1 to -4) to positive canvas positions (0 to height)
                    // -1 (awake) should be at top (small Y), -4 (deep) should be at bottom (large Y)
                    val normalizedLevel = Math.abs(stageLevel)  // Convert -1..-4 to 1..4
                    val yPosition = (normalizedLevel.toFloat() / 4f) * size.height
                    val barHeight = size.height / 6f // Thicker bars
                    
                    drawRect(
                        color = stageColors[index],
                        topLeft = Offset(startX, yPosition - barHeight / 2),
                        size = Size(width, barHeight)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Stage legend - ordered by Oura depth
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Group stages and calculate totals
            val stageGroups = sortedStages.groupBy { it.stage }
            val stageTotals = stageGroups.mapValues { (_, stages) ->
                stages.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            }
            
            // Display in Oura order: Awake -> REM -> Light -> Deep
            val orderedStages = listOf("1", "6", "4", "5")
            orderedStages.forEach { stageNum ->
                val totalMinutes = stageTotals[stageNum]
                if (totalMinutes != null && totalMinutes > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(getStageColor(stageNum))
                            )
                            Text(
                                text = formatStageName(stageNum),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "${totalMinutes}min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(instant: Instant): String {
    return instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatStageName(stage: String): String {
    return when (stage) {
        "1" -> "Awake"
        "4" -> "Light Sleep"
        "5" -> "Deep Sleep"
        "6" -> "REM Sleep"
        "2" -> "Sleeping"
        "3" -> "Unknown"
        else -> stage
    }
}

private fun getStageLevel(stage: String): Int {
    android.util.Log.d("SleepStageChart", "Getting level for stage: '$stage'")
    // Oura Ring order (high to low depth): 1=Awake, 6=REM, 4=Light, 5=Deep
    // Return values so they render top to bottom in that order
    val level = when (stage) {
        "1" -> -1  // Awake - highest (top)
        "6" -> -2  // REM Sleep
        "4" -> -3  // Light Sleep
        "5" -> -4  // Deep Sleep - lowest (bottom)
        "2" -> -3  // Unknown/Sleeping - treat as Light
        "3" -> -3  // Unknown - treat as Light
        else -> {
            android.util.Log.w("SleepStageChart", "Unknown stage type: $stage, defaulting to -3")
            -3
        }
    }
    return level
}

@Composable
private fun getStageColor(stage: String): Color {
    android.util.Log.d("SleepStageChart", "Getting color for stage: '$stage'")
    // Use distinct hardcoded colors for better visibility
    val color = when {
        // Check for numeric values (Health Connect sometimes uses numbers)
        stage == "1" -> Color(0xFFE57373) // Awake - Red
        stage == "2" -> Color(0xFF4FC3F7) // Sleeping - Cyan
        stage == "3" -> Color(0xFF81C784) // Light Sleep - Light Green
        stage == "4" -> Color(0xFF64B5F6) // Light Sleep - Light Blue
        stage == "5" -> Color(0xFF9575CD) // REM - Purple
        stage == "6" -> Color(0xFF1565C0) // Deep - Dark Blue
        
        // Check for string names (case insensitive)
        stage.contains("AWAKE", ignoreCase = true) -> Color(0xFFE57373) // Red
        stage.contains("OUT", ignoreCase = true) -> Color(0xFFFFB74D) // Orange
        stage.contains("REM", ignoreCase = true) -> Color(0xFF9575CD) // Purple
        stage.contains("LIGHT", ignoreCase = true) -> Color(0xFF64B5F6) // Light Blue
        stage.contains("DEEP", ignoreCase = true) -> Color(0xFF1565C0) // Dark Blue
        stage.contains("SLEEP", ignoreCase = true) -> Color(0xFF4FC3F7) // Cyan
        stage.contains("UNKNOWN", ignoreCase = true) -> Color(0xFFB0BEC5) // Gray
        
        else -> {
            android.util.Log.w("SleepStageChart", "Unknown stage type: $stage, using gray")
            Color(0xFFB0BEC5) // Gray for unknown
        }
    }
    android.util.Log.d("SleepStageChart", "Color for '$stage': $color")
    return color
}
