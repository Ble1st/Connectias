package com.ble1st.connectias.plugin.version

import android.content.Context
import com.ble1st.connectias.plugin.PluginManager
import com.ble1st.connectias.plugin.StreamingPluginManager
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plugin manager with version management capabilities
 */
@Singleton
class VersionedPluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val streamingManager: StreamingPluginManager,
    private val versionManager: PluginVersionManager,
    private val rollbackManager: PluginRollbackManager
) : IVersionedPluginManager {
    
    /**
     * Load plugin with version tracking
     */
    override suspend fun loadPluginWithVersion(
        pluginFile: File,
        version: PluginVersion
    ): Result<PluginMetadata> {
        return try {
            val result = pluginManager.loadPluginFromFile(pluginFile)
            
            if (result.isSuccess) {
                val metadata = result.getOrThrow()
                
                versionManager.registerPluginVersion(
                    pluginId = metadata.pluginId,
                    version = version
                )
                
                rollbackManager.createBackup(metadata.pluginId, version)
                
                Timber.d("Loaded plugin ${metadata.pluginId} with version ${version.version}")
                Result.success(metadata)
            } else {
                result
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin with version tracking")
            Result.failure(e)
        }
    }
    
    /**
     * Update plugin to new version
     */
    override suspend fun updatePlugin(
        pluginId: String,
        targetVersion: PluginVersion
    ): Result<Unit> {
        return try {
            // Check if plugin is installed
            val currentVersion = versionManager.getCurrentVersion(pluginId)
                ?: return Result.failure(IllegalStateException("Plugin not installed"))
            
            // Create backup before update
            val backupCreated = rollbackManager.createBackup(pluginId, currentVersion)
            if (!backupCreated) {
                Timber.w("Failed to create backup before update for $pluginId")
            }
            
            // Download and install new version
            val updateResult = streamingManager.updatePlugin(
                pluginId = pluginId,
                targetVersion = targetVersion
            )
            
            if (updateResult.isSuccess) {
                // Register new version
                versionManager.registerPluginVersion(
                    pluginId = pluginId,
                    version = targetVersion
                )
                
                // Clean up old versions
                versionManager.cleanupOldVersions(pluginId)
                
                Timber.d("Successfully updated $pluginId to ${targetVersion.version}")
                Result.success(Unit)
            } else {
                // Rollback on failure
                rollbackManager.rollbackPlugin(
                    pluginId = pluginId,
                    reason = "Update failed"
                )
                Result.failure(updateResult.exceptionOrNull() ?: Exception("Update failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update plugin $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Rollback plugin to previous version
     */
    override suspend fun rollbackPlugin(
        pluginId: String,
        targetVersion: String?,
        reason: String
    ): Result<PluginVersion> {
        val result = rollbackManager.rollbackPlugin(pluginId, targetVersion, reason)
        return when (result) {
            is RollbackResult.Success -> Result.success(result.version)
            is RollbackResult.Failure -> Result.failure(Exception(result.reason))
        }
    }
    
    /**
     * Get plugin version info
     */
    override fun getPluginVersion(pluginId: String): PluginVersion? {
        return versionManager.getCurrentVersion(pluginId)
    }
    
    /**
     * Get available updates for all plugins
     */
    override fun getAvailableUpdates(): Flow<List<PluginVersionUpdate>> {
        return versionManager.availableUpdates
    }
    
    /**
     * Get version history for a plugin
     */
    override fun getVersionHistory(pluginId: String): List<PluginVersionHistory> {
        return versionManager.getVersionHistory(pluginId)
    }
    
    /**
     * Check if rollback is available
     */
    override fun canRollback(pluginId: String): Boolean {
        return rollbackManager.canRollback(pluginId)
    }
    
    /**
     * Get rollback history
     */
    override fun getRollbackHistory(): Flow<List<RollbackEntry>> {
        return rollbackManager.rollbackHistory
    }
    
    /**
     * Get rollback statistics
     */
    override fun getRollbackStats(): RollbackStats {
        return rollbackManager.getRollbackStats()
    }
    
    /**
     * Clean up old versions and backups
     */
    override suspend fun cleanup() {
        try {
            // Get all loaded plugins
            val loadedPlugins = pluginManager.getLoadedPlugins()
            
            for (plugin in loadedPlugins) {
                // Clean up old versions
                versionManager.cleanupOldVersions(plugin.pluginId)
                
                // Clean up old backups
                rollbackManager.cleanupOldBackups(plugin.pluginId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old versions")
        }
    }
    
    /**
     * Export plugin with version info
     */
    override suspend fun exportPluginWithVersion(pluginId: String): Result<File> {
        return try {
            val version = versionManager.getCurrentVersion(pluginId)
                ?: return Result.failure(IllegalStateException("No version info found"))
            
            val exportResult = pluginManager.exportPlugin(pluginId)
            
            if (exportResult.isSuccess) {
                val exportedFile = exportResult.getOrThrow()
                
                val versionInfoFile = File(exportedFile.parentFile, "${pluginId}_version.json")
                val json = kotlinx.serialization.json.Json { prettyPrint = true }
                versionInfoFile.writeText(json.encodeToString(version))
                
                Timber.d("Exported plugin $pluginId with version info")
                Result.success(exportedFile)
            } else {
                exportResult
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to export plugin with version")
            Result.failure(e)
        }
    }
    
    /**
     * Import plugin with version validation
     */
    override suspend fun importPluginWithVersion(
        pluginFile: File,
        versionInfoFile: File?
    ): Result<PluginMetadata> {
        return try {
            // Parse version info if provided
            val version = versionInfoFile?.let { file ->
                val json = kotlinx.serialization.json.Json
                json.decodeFromString<PluginVersion>(file.readText())
            }
            
            if (version != null) {
                loadPluginWithVersion(pluginFile, version)
            } else {
                val result = pluginManager.loadPluginFromFile(pluginFile)
                
                if (result.isSuccess) {
                    val metadata = result.getOrThrow()
                    val autoVersion = PluginVersion(
                        version = metadata.version,
                        versionCode = 1,
                        releaseDate = java.util.Date(),
                        changelog = "Imported from file",
                        minHostVersion = metadata.minAppVersion,
                        dependencies = metadata.dependencies,
                        size = pluginFile.length(),
                        checksum = "",
                        downloadUrl = ""
                    )
                    versionManager.registerPluginVersion(metadata.pluginId, autoVersion)
                }
                
                result
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to import plugin with version")
            Result.failure(e)
        }
    }
    
    private suspend fun extractVersionFromPlugin(pluginFile: File): PluginVersion? {
        return try {
            // This would extract version from plugin manifest
            // Implementation depends on your plugin format
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract version from plugin")
            null
        }
    }
}
