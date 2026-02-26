package com.marlobell.ghcv.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ChangesTokenRequest
import com.marlobell.ghcv.data.model.ChangesMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectManager(private val context: Context) {
    
    private val healthConnectClient by lazy { 
        HealthConnectClient.getOrCreate(context)
    }

    /**
     * Tracks the availability status of Health Connect SDK.
     * Observable state that can be monitored by ViewModels and UI.
     */
    var availability = mutableStateOf(HealthConnectClient.SDK_UNAVAILABLE)
        private set

    companion object {
        // The 12 record-type permissions needed for foreground Health Connect reads.
        private val RECORD_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
        )

        // Full permission set to request: record permissions + optional background read.
        // Background read allows widgets/background jobs to read HC data without the app
        // being in the foreground. It's optional — the app functions without it.
        val PERMISSIONS: Set<String> = RECORD_PERMISSIONS +
            setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    init {
        checkAvailability()
    }

    fun getClient(): HealthConnectClient = healthConnectClient

    fun getApplicationContext(): Context = context.applicationContext

    /**
     * Checks and updates the availability status of Health Connect.
     * Should be called on app start and when returning from background.
     */
    fun checkAvailability() {
        availability.value = HealthConnectClient.getSdkStatus(context)
        Log.d("GHCV", "Health Connect availability: ${availability.value}")
    }

    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.containsAll(RECORD_PERMISSIONS)  // background read is optional
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking permissions", e)
            false
        }
    }

    /**
     * Checks if permission is granted for a specific record type.
     *
     * @param recordClass The record type to check (e.g., StepsRecord::class)
     * @return true if permission is granted for this record type
     */
    suspend fun hasPermissionFor(recordClass: KClass<out Record>): Boolean {
        return try {
            val permission = HealthPermission.getReadPermission(recordClass)
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(permission)
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking permission for ${recordClass.simpleName}", e)
            false
        }
    }
    
    /**
     * Checks permissions for multiple record types at once.
     *
     * @param recordClasses Set of record types to check
     * @return Map of record class to permission status
     */
    suspend fun getPermissionsStatus(recordClasses: Set<KClass<out Record>>): Map<KClass<out Record>, Boolean> {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            recordClasses.associateWith { recordClass ->
                val permission = HealthPermission.getReadPermission(recordClass)
                granted.contains(permission)
            }
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking permissions status", e)
            recordClasses.associateWith { false }
        }
    }
    
    /**
     * Returns true if the READ_HEALTH_DATA_IN_BACKGROUND permission has been granted.
     * When granted, Health Connect data can be read from background contexts (e.g. widgets).
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            granted.contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking background read permission", e)
            false
        }
    }
    
    /**
     * Triggers Health Connect registration by querying granted permissions.
     * This registers the app with Health Connect so permissions can be managed.
     */
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
                PermissionController.createRequestPermissionResultContract()
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
    
    fun openHealthConnectSettings(context: Context) {
        try {
            val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            intent.setPackage("com.google.android.healthconnect.controller")
            intent.putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
            context.startActivity(intent)
            Log.d("GHCV", "Opened Health Connect permissions screen")
        } catch (e: Exception) {
            Log.e("GHCV", "Failed to open HC settings", e)
        }
    }
    
    /**
     * Obtains a changes token for the specified record types.
     * This token can be used with getChanges() to retrieve differential updates.
     * Tokens expire after 30 days.
     *
     * @param dataTypes Set of record types to track changes for
     * @return Changes token string to use with getChanges()
     */
    suspend fun getChangesToken(dataTypes: Set<KClass<out Record>>): String {
        val request = ChangesTokenRequest(dataTypes)
        return healthConnectClient.getChangesToken(request)
    }

    /**
     * Creates a Flow of change messages using a changes token as a start point.
     * The flow will emit changes until no more are available, then emit the next token.
     *
     * @param token The changes token from getChangesToken() or a previous getChanges() call
     * @return Flow that emits ChangesMessage.ChangeList for each batch, then ChangesMessage.NoMoreChanges with next token
     * @throws IOException if the token has expired (tokens are valid for 30 days)
     */
    suspend fun getChanges(token: String): Flow<ChangesMessage> = flow {
        var nextChangesToken = token
        do {
            val response = healthConnectClient.getChanges(nextChangesToken)
            if (response.changesTokenExpired) {
                // Tokens expire after 30 days. The app should use the Changes API regularly
                // enough that tokens don't expire, and should have a fallback mechanism
                // (e.g., fetch all data since a certain date) if they do expire.
                throw IOException("Changes token has expired")
            }
            emit(ChangesMessage.ChangeList(response.changes))
            nextChangesToken = response.nextChangesToken
        } while (response.hasMore)
        emit(ChangesMessage.NoMoreChanges(nextChangesToken))
    }
}
