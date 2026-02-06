package com.marlobell.ghcv.data.exceptions

/**
 * Base exception class for all Health Connect related errors.
 * Provides a consistent way to handle errors across the data layer.
 */
sealed class HealthConnectException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    
    /**
     * Thrown when required Health Connect permissions are not granted.
     *
     * @property missingPermissions Set of permission strings that are missing
     */
    class PermissionDeniedException(
        val missingPermissions: Set<String> = emptySet(),
        message: String = "Health Connect permissions not granted"
    ) : HealthConnectException(message)
    
    /**
     * Thrown when requested data is not available in Health Connect.
     * This is different from an error - the operation succeeded but no data exists.
     */
    class DataNotAvailableException(
        message: String = "No data available for the requested time range"
    ) : HealthConnectException(message)
    
    /**
     * Thrown when a changes token has expired.
     * Changes tokens are valid for 30 days from the time they are issued.
     *
     * @property expiredToken The token that expired
     */
    class TokenExpiredException(
        val expiredToken: String,
        message: String = "Changes token has expired (valid for 30 days)"
    ) : HealthConnectException(message)
    
    /**
     * Thrown when Health Connect SDK is not available on the device.
     * Can happen if Health Connect is not installed or is an incompatible version.
     *
     * @property sdkStatus The SDK status code from HealthConnectClient.getSdkStatus()
     */
    class SdkUnavailableException(
        val sdkStatus: Int,
        message: String = "Health Connect SDK is not available"
    ) : HealthConnectException(message)
    
    /**
     * Thrown when there's a network/communication error with Health Connect.
     * Usually indicates a temporary issue that may resolve with retry.
     */
    class NetworkException(
        message: String = "Failed to communicate with Health Connect",
        cause: Throwable? = null
    ) : HealthConnectException(message, cause)
    
    /**
     * Thrown when Health Connect returns an invalid or unexpected response.
     */
    class InvalidResponseException(
        message: String = "Received invalid response from Health Connect",
        cause: Throwable? = null
    ) : HealthConnectException(message, cause)
    
    /**
     * Thrown when a requested data type or record is not registered/supported.
     */
    class UnsupportedRecordTypeException(
        val recordType: String,
        message: String = "Record type is not supported or registered"
    ) : HealthConnectException(message)
    
    /**
     * Generic catch-all for other Health Connect errors.
     */
    class UnknownException(
        message: String = "An unknown error occurred",
        cause: Throwable? = null
    ) : HealthConnectException(message, cause)
}
