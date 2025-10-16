package com.ble1st.connectias.storage

import androidx.room.RoomDatabase
import timber.log.Timber

/**
 * Handles database corruption detection and recovery for plugin data
 */
class DatabaseCorruptionHandler(
    private val database: RoomDatabase
) {
    
    /**
     * Checks if the database is corrupted for a specific plugin
     */
    fun isDatabaseCorrupted(pluginId: String): Boolean {
        return try {
            // Simple corruption check - try to access the database
            database.openHelper.readableDatabase
            false
        } catch (e: Exception) {
            Timber.e(e, "Database corruption detected for plugin: $pluginId")
            true
        }
    }
    
    /**
     * Handles database corruption by clearing plugin data
     */
    fun handleCorruptedDatabase(pluginId: String) {
        try {
            Timber.w("Handling database corruption for plugin: $pluginId")
            
            // Clear all plugin data
            database.clearAllTables()
            
            Timber.i("Database corruption handled successfully for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle database corruption for plugin: $pluginId")
        }
    }
}