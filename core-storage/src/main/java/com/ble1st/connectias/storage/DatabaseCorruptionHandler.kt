package com.ble1st.connectias.storage

import com.ble1st.connectias.storage.database.PluginDatabase
import timber.log.Timber

class DatabaseCorruptionHandler(
    private val database: PluginDatabase,
    private val databaseManager: PluginDatabaseManager
) {
    suspend fun handleCorruptedDatabase(pluginId: String) {
        Timber.e("Database corruption detected for plugin: $pluginId")
        
        try {
            // 1. Alte Tabelle löschen
            databaseManager.deletePluginTable(pluginId)
            
            // 2. Neue Tabelle erstellen
            databaseManager.createPluginTable(pluginId)
            
            // 3. Plugin-Status aktualisieren
            val plugin = database.pluginDao().getPlugin(pluginId)
            plugin?.let {
                database.pluginDao().updatePlugin(
                    it.copy(lastUpdated = System.currentTimeMillis())
                )
            }
            
            Timber.i("Database re-initialized for plugin: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to recover from database corruption")
            throw DatabaseRecoveryException("Database recovery failed for plugin: $pluginId", e)
        }
    }
    
    fun isDatabaseCorrupted(pluginId: String): Boolean {
        return try {
            val tableName = pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
            database.openHelper.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM plugin_data_$tableName LIMIT 1",
                emptyArray()
            ).use { true }
            false
        } catch (e: Exception) {
            Timber.w(e, "Database corruption check failed")
            true
        }
    }
}

class DatabaseRecoveryException(message: String, cause: Throwable) : Exception(message, cause)
