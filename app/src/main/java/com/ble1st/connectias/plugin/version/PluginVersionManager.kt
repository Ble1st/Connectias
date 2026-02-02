package com.ble1st.connectias.plugin.version

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages plugin versions, updates, and rollback information
 */
@Singleton
class PluginVersionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json
) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("plugin_versions", Context.MODE_PRIVATE)
    
    private val versionDir = File(context.filesDir, "plugin_versions")
    private val historyFile = File(versionDir, "version_history.json")
    
    private val _availableUpdates = MutableStateFlow<List<PluginVersionUpdate>>(emptyList())
    val availableUpdates: Flow<List<PluginVersionUpdate>> = _availableUpdates.asStateFlow()
    
    private val pluginVersions = ConcurrentHashMap<String, PluginVersion>()
    private val versionHistory = mutableListOf<PluginVersionHistory>()
    
    init {
        versionDir.mkdirs()
        loadVersionHistory()
    }
    
    /**
     * Register a plugin version
     */
    fun registerPluginVersion(pluginId: String, version: PluginVersion) {
        pluginVersions[pluginId] = version
        
        // Add to history
        versionHistory.add(
            PluginVersionHistory(
                pluginId = pluginId,
                version = version,
                installedAt = Date()
            )
        )
        
        saveVersionHistory()
        prefs.edit { putString("version_$pluginId", json.encodeToString(version))}
        
        Timber.d("Registered version $version for plugin $pluginId")
    }
    
    /**
     * Get current version of a plugin
     */
    fun getCurrentVersion(pluginId: String): PluginVersion? {
        return pluginVersions[pluginId] ?: prefs.getString("version_$pluginId", null)?.let {
            try {
                json.decodeFromString<PluginVersion>(it)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse version for $pluginId")
                null
            }
        }
    }
    
    /**
     * Check if update is available for a plugin
     */
    fun checkUpdateAvailable(
        pluginId: String,
        currentVersion: PluginVersion,
        availableVersions: List<PluginVersion>
    ): PluginVersionUpdate? {
        val latestVersion = availableVersions
            .filter { it.isNewerThan(currentVersion) }
            .filter { it.isCompatibleWith(getHostVersion()) }
            .maxByOrNull { it.versionCode }
            
        return latestVersion?.let { latest ->
            val updateType = determineUpdateType(currentVersion, latest)
            PluginVersionUpdate(
                pluginId = pluginId,
                currentVersion = currentVersion,
                availableVersion = latest,
                updateType = updateType,
                isMandatory = updateType == PluginVersionUpdate.UpdateType.MAJOR
            )
        }
    }
    
    /**
     * Update available updates list
     */
    fun updateAvailableUpdates(updates: List<PluginVersionUpdate>) {
        _availableUpdates.value = updates
    }
    
    /**
     * Get version history for a plugin
     */
    fun getVersionHistory(pluginId: String): List<PluginVersionHistory> {
        return versionHistory.filter { it.pluginId == pluginId }
    }
    
    /**
     * Mark version as rollback
     */
    fun markAsRollback(pluginId: String, version: PluginVersion, reason: String) {
        val rollbackVersion = version.copy(isRollback = true)
        pluginVersions[pluginId] = rollbackVersion
        
        // Update history
        val historyIndex = versionHistory.indexOfLast { 
            it.pluginId == pluginId && it.version.version == version.version 
        }
        if (historyIndex >= 0) {
            val updatedEntry = versionHistory[historyIndex].copy(rollbackReason = reason)
            versionHistory[historyIndex] = updatedEntry
        }
        
        saveVersionHistory()
        prefs.edit { putString("version_$pluginId", json.encodeToString(rollbackVersion)) }
    }
    
    /**
     * Get all installed plugin versions
     */
    fun getAllInstalledVersions(): Map<String, PluginVersion> {
        return pluginVersions.toMap()
    }
    
    /**
     * Clean up old versions (keep last 5)
     */
    fun cleanupOldVersions(pluginId: String) {
        val history = getVersionHistory(pluginId)
        if (history.size > 5) {
            val toRemove = history.dropLast(5)
            toRemove.forEach { entry ->
                versionHistory.remove(entry)
            }
            saveVersionHistory()
        }
    }
    
    private fun determineUpdateType(
        current: PluginVersion, 
        available: PluginVersion
    ): PluginVersionUpdate.UpdateType {
        val currentParts = current.version.split(".")
        val availableParts = available.version.split(".")
        
        return when {
            availableParts.getOrNull(0)?.toIntOrNull() != currentParts.getOrNull(0)?.toIntOrNull() ->
                PluginVersionUpdate.UpdateType.MAJOR
            availableParts.getOrNull(1)?.toIntOrNull() != currentParts.getOrNull(1)?.toIntOrNull() ->
                PluginVersionUpdate.UpdateType.MINOR
            available.isPrerelease ->
                PluginVersionUpdate.UpdateType.PRERELEASE
            else ->
                PluginVersionUpdate.UpdateType.PATCH
        }
    }
    
    private fun getHostVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    private fun loadVersionHistory() {
        if (historyFile.exists()) {
            try {
                val content = historyFile.readText()
                val history = json.decodeFromString<List<PluginVersionHistory>>(content)
                versionHistory.clear()
                versionHistory.addAll(history)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load version history")
            }
        }
    }
    
    private fun saveVersionHistory() {
        try {
            historyFile.writeText(json.encodeToString(versionHistory))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save version history")
        }
    }
    
    /**
     * Create rollback version from existing plugin
     */
    fun createRollbackVersion(pluginId: String): PluginVersion? {
        val currentVersion = getCurrentVersion(pluginId) ?: return null
        return currentVersion.copy(
            version = "${currentVersion.version}-rollback-${System.currentTimeMillis()}",
            versionCode = currentVersion.versionCode - 1,
            isRollback = true,
            changelog = "Rollback to previous stable version"
        )
    }
}
