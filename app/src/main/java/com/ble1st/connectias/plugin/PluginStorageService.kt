package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.api.StorageService
import com.ble1st.connectias.storage.PluginDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class PluginStorageService(
    private val context: Context,
    private val pluginId: String
) : StorageService {
    
    private val databaseManager = PluginDatabaseManager(context)
    
    override suspend fun putString(key: String, value: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Input-Sanitization
                val sanitizedKey = com.ble1st.connectias.storage.InputSanitizer.sanitizeKey(key)
                val sanitizedValue = com.ble1st.connectias.storage.InputSanitizer.sanitizeValue(value)
                
                if (sanitizedKey == null || sanitizedValue == null) {
                    Timber.w("Plugin $pluginId: Invalid key or value for storage")
                    return@withContext false
                }
                
                // In Plugin-Datenbank speichern
                val pluginData = com.ble1st.connectias.storage.database.entity.PluginDataEntity(
                    pluginId = pluginId,
                    key = sanitizedKey,
                    value = sanitizedValue,
                    timestamp = System.currentTimeMillis()
                )
                
                databaseManager.getPluginDataDao(pluginId).insertData(pluginData)
                Timber.d("Plugin $pluginId: Stored key '$sanitizedKey'")
                true
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Failed to store data")
                false
            }
        }
    }
    
    override suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val sanitizedKey = com.ble1st.connectias.storage.InputSanitizer.sanitizeKey(key)
                if (sanitizedKey == null) {
                    Timber.w("Plugin $pluginId: Invalid key for retrieval")
                    return@withContext null
                }
                
                val data = databaseManager.getPluginDataDao(pluginId).getData(pluginId, sanitizedKey)
                Timber.d("Plugin $pluginId: Retrieved key '$sanitizedKey'")
                data?.value
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Failed to retrieve data")
                null
            }
        }
    }
    
    override suspend fun delete(key: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sanitizedKey = com.ble1st.connectias.storage.InputSanitizer.sanitizeKey(key)
                if (sanitizedKey == null) {
                    Timber.w("Plugin $pluginId: Invalid key for deletion")
                    return@withContext false
                }
                
                databaseManager.getPluginDataDao(pluginId).deleteData(pluginId, sanitizedKey)
                Timber.d("Plugin $pluginId: Deleted key '$sanitizedKey'")
                true
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Failed to delete data")
                false
            }
        }
    }
    
    override suspend fun getAllKeys(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val keys = databaseManager.getPluginDataDao(pluginId).getAllKeys(pluginId)
                Timber.d("Plugin $pluginId: Retrieved ${keys.size} keys")
                keys
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Failed to get all keys")
                emptyList()
            }
        }
    }
    
    override suspend fun clear(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                databaseManager.getPluginDataDao(pluginId).clearAllData(pluginId)
                Timber.d("Plugin $pluginId: Cleared all data")
                true
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Failed to clear data")
                false
            }
        }
    }
}