package com.ble1st.connectias.pluginmanager

import android.content.Context
import com.ble1st.connectias.api.PluginPermission
import com.ble1st.connectias.storage.PluginDatabaseManager
import timber.log.Timber

class PluginPermissionManager(
    private val databaseManager: PluginDatabaseManager,
    private val context: Context
) {
    private val permissionCache = mutableMapOf<String, MutableMap<PluginPermission, Boolean>>()
    
    suspend fun requestPermission(
        pluginId: String,
        permission: PluginPermission,
        onResult: (Boolean) -> Unit
    ) {
        // 1. Aus Cache/DB prüfen, ob bereits entschieden
        val cachedDecision = getStoredPermission(pluginId, permission)
        if (cachedDecision != null) {
            onResult(cachedDecision)
            return
        }
        
        // 2. Dialog anzeigen (nur einmal pro Plugin + Permission)
        val granted = showPermissionDialog(pluginId, permission)
        // 3. Entscheidung speichern
        savePermissionDecision(pluginId, permission, granted)
        permissionCache.getOrPut(pluginId) { mutableMapOf() }[permission] = granted
        onResult(granted)
    }
    
    suspend fun hasPermission(pluginId: String, permission: PluginPermission): Boolean {
        return permissionCache[pluginId]?.get(permission)
            ?: getStoredPermission(pluginId, permission)
            ?: false
    }
    
    private suspend fun getStoredPermission(pluginId: String, permission: PluginPermission): Boolean? {
        // For now, we'll use a simple in-memory cache
        // In a real implementation, this would query the database
        return permissionCache[pluginId]?.get(permission)
    }
    
    private suspend fun savePermissionDecision(
        pluginId: String, 
        permission: PluginPermission, 
        granted: Boolean
    ) {
        // For now, we'll just update the cache
        // In a real implementation, this would save to the database
        permissionCache.getOrPut(pluginId) { mutableMapOf() }[permission] = granted
        Timber.d("Permission decision saved for plugin $pluginId: ${permission.name} = $granted")
    }
    
    private suspend fun showPermissionDialog(
        pluginId: String,
        permission: PluginPermission
    ): Boolean {
        // This would show a dialog to the user
        // For now, we'll just grant the permission
        Timber.i("Permission requested for plugin $pluginId: ${permission.name}")
        return true
    }
}
