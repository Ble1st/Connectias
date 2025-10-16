package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.api.StorageService
import com.ble1st.connectias.storage.PluginDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.reflect.KClass

class PluginStorageService(
    private val context: Context,
    private val pluginId: String
) : StorageService {
    
    private val databaseManager = PluginDatabaseManager(context)
    private val storage = mutableMapOf<String, String>()
    
    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            try {
                // Simple input validation
                if (key.isBlank() || value.isBlank()) {
                    Timber.w("Plugin $pluginId: Invalid key or value for storage")
                    return@withContext
                }
                
                // Store in memory (simplified implementation)
                storage[key] = value
                Timber.d("Plugin $pluginId: Stored string for key '$key'")
                
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error storing string for key '$key'")
            }
        }
    }
    
    override suspend fun getString(key: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val value = storage[key]
                Timber.d("Plugin $pluginId: Retrieved string for key '$key'")
                value
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error retrieving string for key '$key'")
                null
            }
        }
    }
    
    override suspend fun putObject(key: String, value: Any) {
        withContext(Dispatchers.IO) {
            try {
                // Simple serialization (just convert to string)
                val stringValue = value.toString()
                putString(key, stringValue)
                Timber.d("Plugin $pluginId: Stored object for key '$key'")
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error storing object for key '$key'")
            }
        }
    }
    
    override suspend fun <T : Any> getObject(key: String, type: KClass<T>): T? {
        return withContext(Dispatchers.IO) {
            try {
                val stringValue = getString(key)
                if (stringValue != null) {
                    // Simple deserialization (just return as string for now)
                    @Suppress("UNCHECKED_CAST")
                    stringValue as? T
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error retrieving object for key '$key'")
                null
            }
        }
    }
    
    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            try {
                storage.remove(key)
                Timber.d("Plugin $pluginId: Removed data for key '$key'")
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error removing data for key '$key'")
            }
        }
    }
    
    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                storage.clear()
                Timber.d("Plugin $pluginId: Cleared all data")
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error clearing all data")
            }
        }
    }
}