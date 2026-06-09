package com.example.cmw_simulator

import android.content.Context

/**
 * Provides simulated fitness data for Gemini's generated UI.
 *
 * Gemini reads the KDoc of [getFitnessMetricsSnapshot] via the system prompt
 * and injects the returned values into the generated widget text nodes.
 */
object FitnessAppFunction {

    /**
     * Returns a snapshot of the user's current fitness metrics.
     *
     * Contains: step count, heart rate (BPM), calories burned,
     * active minutes, and distance walked for the current day.
     */
    fun getFitnessMetricsSnapshot(context: Context): Map<String, Any> {
        return mapOf(
            "steps" to 8742,
            "heartRateBpm" to 72,
            "caloriesBurned" to 486,
            "activeMinutes" to 45,
            "distanceKm" to 6.2,
            "date" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
        )
    }
}
