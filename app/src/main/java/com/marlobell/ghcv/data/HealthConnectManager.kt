package com.marlobell.ghcv.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*

class HealthConnectManager(private val context: Context) {
    
    private val healthConnectClient by lazy { 
        HealthConnectClient.getOrCreate(context)
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(NutritionRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.getReadPermission(SpeedRecord::class),
            HealthPermission.getReadPermission(PowerRecord::class)
        )
    }

    fun getClient(): HealthConnectClient = healthConnectClient

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d("GHCV", "Currently granted permissions: $granted")
            granted.containsAll(PERMISSIONS)
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking permissions", e)
            false
        }
    }

    suspend fun checkAvailability(): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            Log.d("GHCV", "Health Connect SDK status: $status")
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking HC availability", e)
            false
        }
    }
    
    suspend fun triggerHealthConnectRegistration(): Boolean {
        return try {
            // Attempt to check permissions - this registers the app with HC
            Log.d("GHCV", "Attempting to trigger HC registration...")
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d("GHCV", "HC registration triggered, granted count: ${granted.size}")
            
            // Also try to request (even though it will fail on Android 16)
            // This makes HC aware of what permissions we want
            try {
                // This will likely fail but registers our intent
                val contract = PermissionController.createRequestPermissionResultContract()
                Log.d("GHCV", "Permission contract created")
            } catch (e: Exception) {
                Log.d("GHCV", "Contract creation failed (expected): ${e.message}")
            }
            
            true
        } catch (e: Exception) {
            Log.e("GHCV", "Error triggering HC registration", e)
            false
        }
    }

    fun createPermissionRequestContract() = 
        PermissionController.createRequestPermissionResultContract()
    
    fun createManualPermissionIntent(): Intent {
        val intent = Intent("android.health.connect.action.REQUEST_HEALTH_PERMISSIONS")
        intent.setPackage("com.google.android.healthconnect.controller")
        intent.putExtra("android.health.connect.extra.REQUESTED_PERMISSIONS", PERMISSIONS.toTypedArray())
        intent.putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
        Log.d("GHCV", "Created manual intent: $intent")
        Log.d("GHCV", "Intent extras: ${intent.extras}")
        return intent
    }
    
    fun createHealthConnectSettingsIntent(): Intent {
        // Open Android Settings for our app where HC permissions can be managed
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:${context.packageName}")
        Log.d("GHCV", "Created app settings intent: $intent")
        return intent
    }
    
    fun createHealthConnectHomeIntent(): Intent {
        // Open Health Connect main screen
        val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        intent.setPackage("com.google.android.healthconnect.controller")
        Log.d("GHCV", "Created HC home settings intent: $intent")
        return intent
    }
}
