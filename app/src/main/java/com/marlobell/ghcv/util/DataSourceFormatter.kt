package com.marlobell.ghcv.util

/**
 * Utility to format Health Connect data source package names into user-friendly display names.
 */
object DataSourceFormatter {
    
    /**
     * Known data source mappings from package name to friendly name
     */
    private val knownSources = mapOf(
        "com.ouraring.oura" to "Oura",
        "com.google.android.apps.fitness" to "Fit",
        "com.google.android.gms" to "Google Fit",
        "com.samsung.health" to "Samsung Health",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.nike.plusgps" to "Nike Run Club",
        "com.strava" to "Strava",
        "com.underarmour.mapmyfitness" to "MapMyFitness",
        "com.myfitnesspal.android" to "MyFitnessPal",
        "com.runtastic.android" to "Adidas Running",
        "com.withings.wiscale2" to "Withings",
        "com.xiaomi.hm.health" to "Mi Fitness",
        "com.huawei.health" to "Huawei Health",
        "is.hello.sense.android" to "Sense"
    )
    
    /**
     * Formats a package name into a user-friendly display name.
     * 
     * Examples:
     * - "com.ouraring.oura" → "Oura"
     * - "com.google.android.apps.fitness" → "Fit"
     * - "com.unknown.app" → "Unknown App"
     * 
     * @param packageName The full package name from Health Connect metadata
     * @return User-friendly display name
     */
    fun format(packageName: String?): String {
        if (packageName.isNullOrBlank()) {
            return "Unknown"
        }
        
        // Check known sources first
        knownSources[packageName]?.let { return it }
        
        // Fallback: Extract and capitalize the last segment
        return packageName
            .split(".")
            .lastOrNull()
            ?.replaceFirstChar { it.uppercase() }
            ?: "Unknown"
    }
}
