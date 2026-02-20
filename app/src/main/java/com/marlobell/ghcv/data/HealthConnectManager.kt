package com.marlobell.ghcv.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ChangesTokenRequest
import com.marlobell.ghcv.data.model.ChangesMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
        val PERMISSIONS = setOf(
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
            HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        )
    }

    init {
        checkAvailability()
    }

    fun getClient(): HealthConnectClient = healthConnectClient

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
            granted.containsAll(PERMISSIONS)
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
     * Checks if background read permission is available as a feature and granted.
     * Background read allows the app to read Health Connect data when not in foreground.
     * Note: Experimental API usage disabled to allow compilation.
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        // Experimental HealthConnect API - disabled for now
        // TODO: Enable when experimental APIs are stable
        return false
        
        /* Original implementation - uses experimental APIs
        return try {
            val featureAvailable = try {
                val status = healthConnectClient.features.getFeatureStatus(
                    HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                )
                status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } catch (e: NoSuchMethodError) {
                Log.d("GHCV", "Background read feature not available - API not supported")
                return false
            }
            
            if (!featureAvailable) {
                Log.d("GHCV", "Background read feature not available on this device")
                return false
            }
            
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            val hasPermission = granted.contains(
                HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            )
            Log.d("GHCV", "Background read permission granted: $hasPermission")
            hasPermission
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking background read permission", e)
            false
        }
        */
    }
    
    /**
     * Checks if a specific Health Connect feature is available.
     * @param feature One of the HealthConnectFeatures constants
     * @return true if the feature is available on this device/HC version
     * Note: Experimental API usage disabled to allow compilation.
     */
    fun isFeatureAvailable(feature: Int): Boolean {
        // Experimental HealthConnect API - disabled for now
        // TODO: Enable when experimental APIs are stable
        return false
        
        /* Original implementation - uses experimental APIs
        return try {
            val status = healthConnectClient.features.getFeatureStatus(feature)
            val available = status == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            Log.d("GHCV", "Feature $feature status: $status (available: $available)")
            available
        } catch (e: NoSuchMethodError) {
            Log.d("GHCV", "Feature check not supported - API unavailable")
            false
        } catch (e: Exception) {
            Log.e("GHCV", "Error checking feature availability", e)
            false
        }
        */
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
        return intent
    }
    
    fun createHealthConnectSettingsIntent(): Intent {
        // Open Android Settings for our app where HC permissions can be managed
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.parse("package:${context.packageName}")
        return intent
    }
    
    fun createHealthConnectHomeIntent(): Intent {
        // Open Health Connect main screen
        val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        intent.setPackage("com.google.android.healthconnect.controller")
        return intent
    }
    
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
