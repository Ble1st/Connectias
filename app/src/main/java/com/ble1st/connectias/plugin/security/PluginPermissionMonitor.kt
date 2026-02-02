package com.ble1st.connectias.plugin.security

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitor and track permission usage by plugins
 */
@Singleton
class PluginPermissionMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    data class PermissionUsageEvent(
        val pluginId: String,
        val permission: String,
        val granted: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val context: String? = null
    )
    
    data class PermissionStats(
        val pluginId: String,
        val totalRequests: Int,
        val grantedRequests: Int,
        val deniedRequests: Int,
        val recentPermissions: List<String>
    )
    
    private val _permissionEvents = MutableSharedFlow<PermissionUsageEvent>(replay = 100)
    val permissionEvents: Flow<PermissionUsageEvent> = _permissionEvents.asSharedFlow()
    
    // Thread-safe list with size limit to prevent memory leaks
    private val permissionHistory = ConcurrentHashMap<String, CopyOnWriteArrayList<PermissionUsageEvent>>()
    
    companion object {
        private const val MAX_HISTORY_SIZE = 1000 // Maximum events per plugin
    }
    
    /**
     * Track permission usage event
     */
    suspend fun trackPermissionUsage(
        pluginId: String,
        permission: String,
        granted: Boolean,
        context: String? = null
    ) {
        val event = PermissionUsageEvent(
            pluginId = pluginId,
            permission = permission,
            granted = granted,
            context = context
        )
        
        // Add to history with size limit (thread-safe)
        val history = permissionHistory.getOrPut(pluginId) { CopyOnWriteArrayList() }
        history.add(event)
        
        // Trim old events if exceeded limit
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(0)
        }
        
        // Emit event
        _permissionEvents.emit(event)
        
        Timber.d("Permission usage tracked: $pluginId - $permission (granted=$granted)")
    }
    
    /**
     * Get permission statistics for a plugin
     */
    fun getPermissionStats(pluginId: String): PermissionStats {
        val events = permissionHistory[pluginId] ?: emptyList()
        
        return PermissionStats(
            pluginId = pluginId,
            totalRequests = events.size,
            grantedRequests = events.count { it.granted },
            deniedRequests = events.count { !it.granted },
            recentPermissions = events.takeLast(10).map { it.permission }
        )
    }
    
    /**
     * Get all granted permissions for a plugin
     */
    fun getGrantedPermissions(pluginId: String): List<String> {
        return permissionHistory[pluginId]
            ?.filter { it.granted }
            ?.map { it.permission }
            ?.distinct()
            ?: emptyList()
    }
    
    /**
     * Check if a plugin has a specific permission
     */
    fun hasPermission(pluginId: String, permission: String): Boolean {
        return try {
            context.packageManager.checkPermission(
                permission,
                pluginId
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Timber.e(e, "Failed to check permission for $pluginId")
            false
        }
    }
    
    /**
     * Get suspicious permission usage patterns
     */
    fun getSuspiciousPatterns(pluginId: String): List<String> {
        val events = permissionHistory[pluginId] ?: return emptyList()
        val suspicious = mutableListOf<String>()
        
        // Check for excessive permission requests
        val recentEvents = events.filter { 
            System.currentTimeMillis() - it.timestamp < 60_000 // Last minute
        }
        
        if (recentEvents.size > 20) {
            suspicious.add("Excessive permission requests: ${recentEvents.size} in last minute")
        }
        
        // Check for sensitive permissions
        val sensitivePermissions = listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO"
        )
        
        val usedSensitivePerms = events
            .filter { it.granted && sensitivePermissions.contains(it.permission) }
            .map { it.permission }
            .distinct()
        
        if (usedSensitivePerms.isNotEmpty()) {
            suspicious.add("Using sensitive permissions: ${usedSensitivePerms.joinToString()}")
        }
        
        return suspicious
    }
    
    /**
     * Clear permission history for a plugin
     */
    fun clearPluginHistory(pluginId: String) {
        permissionHistory.remove(pluginId)
    }
    
    /**
     * Monitor permission usage for a plugin
     */
    fun monitorPermissionUsage(pluginId: String): Flow<PermissionUsageEvent> {
        return permissionEvents
    }

    /**
     * Returns a snapshot list of all permission usage events for a plugin.
     *
     * This is intended for admin dashboards and export functionality.
     */
    fun getPermissionUsageHistory(pluginId: String): List<PermissionUsageEvent> {
        return permissionHistory[pluginId]?.toList() ?: emptyList()
    }

    /**
     * Returns a snapshot list of all permission usage events across all plugins.
     *
     * Note: This may be expensive for very large histories; callers should filter by time window.
     */
    fun getAllPermissionUsageHistory(): List<PermissionUsageEvent> {
        return permissionHistory.values.flatMap { it.toList() }
    }

    /**
     * Returns a snapshot list of permission usage events within a time window across all plugins.
     */
    fun getPermissionUsageInTimeRange(startTimeMillis: Long, endTimeMillis: Long): List<PermissionUsageEvent> {
        return getAllPermissionUsageHistory()
            .asSequence()
            .filter { it.timestamp in startTimeMillis..endTimeMillis }
            .toList()
    }

    /**
     * Returns the set of plugin IDs that have recorded permission usage events.
     */
    fun getTrackedPluginIds(): Set<String> {
        return permissionHistory.keys
    }
}
