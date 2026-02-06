package com.marlobell.ghcv.ui

import java.util.UUID

/**
 * Represents the state of a UI operation with Health Connect.
 * 
 * Based on Google's official Health Connect sample pattern.
 * UUID in Error state prevents duplicate snackbar displays during recomposition.
 */
sealed class UiState {
    /** Initial state before any operation has been attempted. */
    object Uninitialized : UiState()
    
    /** Operation is currently in progress. */
    object Loading : UiState()
    
    /** Operation completed successfully. */
    object Done : UiState()
    
    /** 
     * Operation failed with an error.
     * @param exception The exception that caused the failure
     * @param uuid Unique identifier to prevent duplicate error displays during recomposition
     */
    data class Error(
        val exception: Throwable,
        val uuid: UUID = UUID.randomUUID()
    ) : UiState() {
        val message: String
            get() = exception.message ?: exception.toString()
    }
}
