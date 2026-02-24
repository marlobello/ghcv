package com.marlobell.ghcv.widget.fetcher

import android.util.Log
import com.marlobell.ghcv.data.repository.HealthConnectRepository
import java.time.LocalDate

/**
 * Fetches today's step count, the 7-day average, and the contributing data source
 * from Health Connect.
 *
 * Centralising this logic avoids duplication between [CurrentViewModel][com.marlobell.ghcv.ui.viewmodel.CurrentViewModel]
 * (foreground, in-app) and [StepsWidgetReceiver][com.marlobell.ghcv.widget.StepsWidgetReceiver]
 * (background, widget OS trigger).
 *
 * Returns a [Triple] of `(todaySteps, sevenDayAvg, source)`.
 */
object StepsFetcher {

    private const val TAG = "StepsFetcher"

    /**
     * @param repo A [HealthConnectRepository] backed by an active [HealthConnectClient].
     * @return Triple of (todaySteps, sevenDayAvg, source package name or null).
     */
    suspend fun fetch(repo: HealthConnectRepository): Triple<Long, Long, String?> {
        val (todaySteps, source) = try {
            repo.getTodaySteps()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch today's steps", e)
            Pair(0L, null)
        }

        val sevenDayAvg = try {
            val dailySteps = (1..7).map { daysAgo ->
                try {
                    repo.getStepsForDate(LocalDate.now().minusDays(daysAgo.toLong()))
                } catch (e: Exception) {
                    0L
                }
            }
            val total = dailySteps.sum()
            if (total > 0L) total / 7L else 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute 7-day avg steps", e)
            0L
        }

        return Triple(todaySteps, sevenDayAvg, source)
    }
}
