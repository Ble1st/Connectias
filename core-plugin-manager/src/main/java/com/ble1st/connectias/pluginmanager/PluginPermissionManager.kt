package com.ble1st.connectias.pluginmanager

import android.content.Context
import com.ble1st.connectias.api.PluginPermission
import com.ble1st.connectias.storage.database.PluginDatabase
import com.ble1st.connectias.storage.database.entity.PluginPermissionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class PluginPermissionManager(
    private val database: PluginDatabase,
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
        showPermissionDialog(pluginId, permission) { granted ->
            // 3. Entscheidung speichern
            savePermissionDecision(pluginId, permission, granted)
            permissionCache.getOrPut(pluginId) { mutableMapOf() }[permission] = granted
            onResult(granted)
        }
    }
    
    suspend fun hasPermission(pluginId: String, permission: PluginPermission): Boolean {
        return permissionCache[pluginId]?.get(permission)
            ?: getStoredPermission(pluginId, permission)
            ?: false
    }
    
    private suspend fun getStoredPermission(pluginId: String, permission: PluginPermission): Boolean? {
        return database.pluginPermissionDao().getPermission(pluginId, permission.name)?.granted
    }
    
    private suspend fun savePermissionDecision(
        pluginId: String, 
        permission: PluginPermission, 
        granted: Boolean
    ) {
        database.pluginPermissionDao().insert(
            PluginPermissionEntity(
                pluginId = pluginId,
                permission = permission.name,
                granted = granted,
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    private fun showPermissionDialog(
        pluginId: String,
        permission: PluginPermission,
        onResult: (Boolean) -> Unit
    ) {
        // This would show a dialog to the user
        // For now, we'll just grant the permission
        Timber.i("Permission requested for plugin $pluginId: ${permission.name}")
        onResult(true)
    }
}
