package com.ble1st.connectias.storage

import com.ble1st.connectias.storage.database.PluginDatabase
import timber.log.Timber

class StorageQuotaManager(
    private val database: PluginDatabase
) {
    private val usageCache = mutableMapOf<String, StorageUsage>()
    
    companion object {
        const val MAX_STORAGE_SIZE_MB = 50L
        const val MAX_STORAGE_ENTRIES = 10000
    }
    
    suspend fun checkQuota(pluginId: String, additionalBytes: Int) {
        val usage = getUsage(pluginId)
        
        // Entry-Count-Check
        if (usage.entryCount >= MAX_STORAGE_ENTRIES) {
            throw QuotaExceededException("Max entries exceeded: ${usage.entryCount} >= $MAX_STORAGE_ENTRIES")
        }
        
        // Size-Check
        val newSizeMb = (usage.totalSizeBytes + additionalBytes) / 1024 / 1024
        if (newSizeMb > MAX_STORAGE_SIZE_MB) {
            throw QuotaExceededException("Max storage exceeded: ${newSizeMb}MB > ${MAX_STORAGE_SIZE_MB}MB")
        }
    }
    
    suspend fun getUsage(pluginId: String): StorageUsage {
        // Cache prüfen
        usageCache[pluginId]?.let { return it }
        
        // Aus DB berechnen
        val tableName = pluginId.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val cursor = database.openHelper.readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(value_size_bytes) FROM plugin_data_$tableName",
            emptyArray()
        )
        
        val usage = cursor.use {
            if (it.moveToFirst()) {
                StorageUsage(
                    entryCount = it.getInt(0),
                    totalSizeBytes = it.getLong(1)
                )
            } else {
                StorageUsage(0, 0)
            }
        }
        
        usageCache[pluginId] = usage
        return usage
    }
    
    suspend fun updateUsage(pluginId: String) {
        usageCache.remove(pluginId) // Cache invalidieren
    }
    
    suspend fun resetUsage(pluginId: String) {
        usageCache[pluginId] = StorageUsage(0, 0)
    }
}

data class StorageUsage(
    val entryCount: Int,
    val totalSizeBytes: Long
)

class QuotaExceededException(message: String) : Exception(message)
