package com.marlobell.ghcv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Manages storage and retrieval of Health Connect changes tokens.
 * Tokens are stored per record type and expire after 30 days.
 */
class ChangesTokenStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "health_connect_changes_tokens",
        Context.MODE_PRIVATE
    )

    /**
     * Stores a changes token for a specific record type.
     *
     * @param recordType The class name of the record type (e.g., "StepsRecord")
     * @param token The changes token to store
     */
    fun saveToken(recordType: String, token: String) {
        prefs.edit { putString(TOKEN_PREFIX + recordType, token) }
    }

    /**
     * Retrieves a stored changes token for a specific record type.
     *
     * @param recordType The class name of the record type
     * @return The stored token, or null if no token exists
     */
    fun getToken(recordType: String): String? {
        return prefs.getString(TOKEN_PREFIX + recordType, null)
    }

    /**
     * Clears the stored token for a specific record type.
     * Useful when a token expires and needs to be regenerated.
     *
     * @param recordType The class name of the record type
     */
    fun clearToken(recordType: String) {
        prefs.edit { remove(TOKEN_PREFIX + recordType) }
    }

    /**
     * Clears all stored changes tokens.
     * Use when resetting the app or handling major permission changes.
     */
    fun clearAllTokens() {
        prefs.edit {
            prefs.all.keys.forEach { key ->
                if (key.startsWith(TOKEN_PREFIX)) remove(key)
            }
        }
    }

    /**
     * Gets all stored token keys.
     * Useful for debugging or showing which data types have active tokens.
     */
    fun getAllTokenKeys(): Set<String> {
        return prefs.all.keys.filter { it.startsWith(TOKEN_PREFIX) }
            .map { it.removePrefix(TOKEN_PREFIX) }
            .toSet()
    }

    companion object {
        private const val TOKEN_PREFIX = "changes_token_"
    }
}
