package com.ble1st.connectias.plugin.version

import android.content.Context
import com.ble1st.connectias.plugin.PluginManager
import com.ble1st.connectias.plugin.StreamingPluginManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages plugin rollback functionality
 */
@Singleton
class PluginRollbackManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pluginManager: PluginManager,
    private val versionManager: PluginVersionManager
) {
    private val backupDir = File(context.filesDir, "plugin_backups")
    private val rollbackDir = File(context.filesDir, "plugin_rollbacks")
    
    private val _rollbackHistory = MutableStateFlow<List<RollbackEntry>>(emptyList())
    val rollbackHistory: Flow<List<RollbackEntry>> = _rollbackHistory.asStateFlow()
    
    init {
        backupDir.mkdirs()
        rollbackDir.mkdirs()
        loadRollbackHistory()
    }
    
    /**
     * Create a backup before updating
     */
    fun createBackup(pluginId: String, version: PluginVersion): Boolean {
        return try {
            val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
            if (!pluginFile.exists()) {
                Timber.e("Plugin file not found for $pluginId")
                return false
            }
            
            val backupFile = File(backupDir, "${pluginId}_${version.version}.backup")
            pluginFile.copyTo(backupFile, overwrite = true)
            
            // Create rollback metadata
            val metadata = RollbackMetadata(
                pluginId = pluginId,
                version = version,
                backupFile = backupFile.absolutePath,
                createdAt = Date(),
                reason = "Pre-update backup"
            )
            
            saveRollbackMetadata(metadata)
            
            Timber.d("Created backup for $pluginId version ${version.version}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup for $pluginId")
            false
        }
    }
    
    /**
     * Rollback plugin to previous version
     */
    suspend fun rollbackPlugin(
        pluginId: String, 
        targetVersion: String? = null,
        reason: String = "Manual rollback"
    ): RollbackResult {
        return try {
            // Find backup to rollback to
            val backup = findBackup(pluginId, targetVersion)
                ?: return RollbackResult.Failure("No backup found for rollback")
            
            // Unload current plugin
            val plugin = pluginManager.getPlugin(pluginId)
            val wasEnabled = plugin?.state == PluginManager.PluginState.ENABLED
            if (wasEnabled) {
                pluginManager.disablePlugin(pluginId)
            }
            
            // Create backup of current version before rollback
            val currentVersion = versionManager.getCurrentVersion(pluginId)
            if (currentVersion != null) {
                createBackup(pluginId, currentVersion)
            }
            
            // Restore backup
            val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
            java.io.FileInputStream(backup.backupFile).use { input ->
                java.io.FileOutputStream(pluginFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Reload plugin
            val loadResult = pluginManager.loadPluginFromFile(pluginFile)
            
            if (loadResult.isSuccess) {
                // Re-enable if it was enabled
                if (wasEnabled) {
                    pluginManager.enablePlugin(pluginId)
                }
                
                // Mark as rollback in version manager
                versionManager.markAsRollback(pluginId, backup.version, reason)
                
                // Add to rollback history
                val entry = RollbackEntry(
                    pluginId = pluginId,
                    fromVersion = currentVersion?.version ?: "Unknown",
                    toVersion = backup.version.version,
                    timestamp = Date(),
                    reason = reason,
                    success = true
                )
                addRollbackEntry(entry)
                
                Timber.d("Successfully rolled back $pluginId to ${backup.version.version}")
                RollbackResult.Success(backup.version)
            } else {
                // Restore failed, try to restore current version
                restoreCurrentVersion(pluginId)
                RollbackResult.Failure("Failed to load rollback version: ${loadResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to rollback $pluginId")
            RollbackResult.Failure("Rollback failed: ${e.message}")
        }
    }
    
    /**
     * Get available rollback versions for a plugin
     */
    fun getAvailableRollbacks(pluginId: String): List<RollbackMetadata> {
        return backupDir.listFiles { file ->
            file.name.startsWith("${pluginId}_") && file.name.endsWith(".backup")
        }?.mapNotNull { file ->
            try {
                val versionStr = file.name.removePrefix("${pluginId}_").removeSuffix(".backup")
                val version = PluginVersion.fromString(versionStr)
                version?.let {
                    RollbackMetadata(
                        pluginId = pluginId,
                        version = it,
                        backupFile = file.absolutePath,
                        createdAt = Date(file.lastModified()),
                        reason = "Available rollback"
                    )
                }
            } catch (e: Exception) {
                null
            }
        }?.sortedByDescending { it.createdAt } ?: emptyList()
    }
    
    /**
     * Check if rollback is available for a plugin
     */
    fun canRollback(pluginId: String): Boolean {
        return getAvailableRollbacks(pluginId).isNotEmpty()
    }
    
    /**
     * Clean up old backups (keep last 3)
     */
    fun cleanupOldBackups(pluginId: String) {
        try {
            val backups = getAvailableRollbacks(pluginId)
            if (backups.size > 3) {
                val toRemove = backups.drop(3)
                toRemove.forEach { backup ->
                    File(backup.backupFile).delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old backups for $pluginId")
        }
    }
    
    /**
     * Get rollback statistics
     */
    fun getRollbackStats(): RollbackStats {
        val history = _rollbackHistory.value
        return RollbackStats(
            totalRollbacks = history.size,
            successfulRollbacks = history.count { it.success },
            failedRollbacks = history.count { !it.success },
            mostRolledbackPlugin = history
                .groupBy { it.pluginId }
                .maxByOrNull { it.value.size }
                ?.key
        )
    }
    
    private fun findBackup(pluginId: String, targetVersion: String?): RollbackMetadata? {
        val backups = getAvailableRollbacks(pluginId)
        
        return if (targetVersion != null) {
            backups.find { it.version.version == targetVersion }
        } else {
            // Return the most recent backup
            backups.firstOrNull()
        }
    }
    
    private suspend fun restoreCurrentVersion(pluginId: String) {
        try {
            // Try to restore from current backup
            val currentBackup = File(backupDir, "${pluginId}_current.backup")
            if (currentBackup.exists()) {
                val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
                java.io.FileInputStream(currentBackup).use { input ->
                    java.io.FileOutputStream(pluginFile).use { output ->
                        input.copyTo(output)
                    }
                }
                pluginManager.loadPluginFromFile(pluginFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore current version for $pluginId")
        }
    }
    
    private fun saveRollbackMetadata(metadata: RollbackMetadata) {
        try {
            val metadataFile = File(rollbackDir, "${metadata.pluginId}_${metadata.version.version}.json")
            val json = kotlinx.serialization.json.Json { 
                prettyPrint = true 
                ignoreUnknownKeys = true 
            }
            metadataFile.writeText(json.encodeToString(metadata))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save rollback metadata")
        }
    }
    
    private fun loadRollbackHistory() {
        try {
            val historyFile = File(rollbackDir, "rollback_history.json")
            if (historyFile.exists()) {
                val json = kotlinx.serialization.json.Json { 
                    ignoreUnknownKeys = true 
                }
                val history = json.decodeFromString<List<RollbackEntry>>(historyFile.readText())
                _rollbackHistory.value = history
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load rollback history")
        }
    }
    
    private fun addRollbackEntry(entry: RollbackEntry) {
        val history = _rollbackHistory.value.toMutableList()
        history.add(entry)
        
        // Keep only last 50 entries
        if (history.size > 50) {
            history.removeAt(0)
        }
        
        _rollbackHistory.value = history
        
        // Save to file
        try {
            val historyFile = File(rollbackDir, "rollback_history.json")
            val json = kotlinx.serialization.json.Json { 
                prettyPrint = true 
                ignoreUnknownKeys = true 
            }
            historyFile.writeText(json.encodeToString(history))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save rollback history")
        }
    }
}

/**
 * Result of a rollback operation
 */
sealed class RollbackResult {
    data class Success(val version: PluginVersion) : RollbackResult()
    data class Failure(val reason: String) : RollbackResult()
}

/**
 * Metadata for a rollback backup
 */
data class RollbackMetadata(
    val pluginId: String,
    val version: PluginVersion,
    val backupFile: String,
    val createdAt: Date,
    val reason: String
)

/**
 * Entry in rollback history
 */
data class RollbackEntry(
    val pluginId: String,
    val fromVersion: String,
    val toVersion: String,
    val timestamp: Date,
    val reason: String,
    val success: Boolean
)

/**
 * Rollback statistics
 */
data class RollbackStats(
    val totalRollbacks: Int,
    val successfulRollbacks: Int,
    val failedRollbacks: Int,
    val mostRolledbackPlugin: String?
)
