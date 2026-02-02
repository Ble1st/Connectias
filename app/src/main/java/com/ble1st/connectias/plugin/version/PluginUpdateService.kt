package com.ble1st.connectias.plugin.version

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ble1st.connectias.MainActivity
import com.ble1st.connectias.plugin.PluginManager
import com.ble1st.connectias.plugin.StreamingPluginManager
import com.ble1st.connectias.plugin.store.GitHubPluginStore
import com.ble1st.connectias.plugin.streaming.PluginLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

/**
 * Service for checking and applying plugin updates
 */
@HiltWorker
class PluginUpdateWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pluginManager: PluginManager,
    private val streamingManager: StreamingPluginManager,
    private val versionManager: PluginVersionManager,
    private val gitHubStore: GitHubPluginStore
) : CoroutineWorker(appContext, workerParams) {
    
    private val notificationManager = 
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    override suspend fun doWork(): Result {
        try {
            setForeground(createForegroundInfo())
            
            Timber.d("Starting plugin update check")
            
            // Check for updates
            val updates = checkForUpdates()
            
            if (updates.isNotEmpty()) {
                // Apply updates if auto-update is enabled
                val autoUpdate = inputData.getBoolean(KEY_AUTO_UPDATE, false)
                
                if (autoUpdate) {
                    applyUpdates(updates)
                } else {
                    // Just notify about available updates
                    notifyUpdatesAvailable(updates)
                }
                
                versionManager.updateAvailableUpdates(updates)
            }
            
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Plugin update check failed")
            return Result.failure()
        }
    }
    
    private suspend fun checkForUpdates(): List<PluginVersionUpdate> {
        val updates = mutableListOf<PluginVersionUpdate>()
        
        // Get all installed plugins
        val installedPlugins = pluginManager.getLoadedPlugins()
        
        for (plugin in installedPlugins) {
            try {
                val currentVersion = versionManager.getCurrentVersion(plugin.pluginId)
                if (currentVersion != null) {
                    // Get available versions from GitHub
                    val availableVersionsResult = gitHubStore.getPluginVersions(plugin.pluginId)
                    val availableVersions = if (availableVersionsResult.isSuccess) {
                        availableVersionsResult.getOrThrow()
                    } else {
                        Timber.e(availableVersionsResult.exceptionOrNull(), "Failed to fetch versions for ${plugin.pluginId}")
                        emptyList()
                    }
                    
                    val update = versionManager.checkUpdateAvailable(
                        pluginId = plugin.pluginId,
                        currentVersion = currentVersion,
                        availableVersions = availableVersions
                    )
                    
                    update?.let { updates.add(it) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check updates for ${plugin.pluginId}")
            }
        }
        
        return updates
    }
    
    private suspend fun applyUpdates(updates: List<PluginVersionUpdate>) {
        for (update in updates) {
            try {
                Timber.d("Applying update for ${update.pluginId}")
                
                // Download new version
                val newPluginFile = downloadPlugin(update.availableVersion)
                
                // Create backup of current version
                createBackup(update.pluginId)
                
                // Install update (reload plugin)
                val installResult = pluginManager.loadPluginFromFile(newPluginFile)
                val success = installResult.isSuccess
                
                if (success) {
                    // Register new version
                    versionManager.registerPluginVersion(
                        pluginId = update.pluginId,
                        version = update.availableVersion
                    )
                    
                    // Clean up old versions
                    versionManager.cleanupOldVersions(update.pluginId)
                    
                    Timber.d("Successfully updated ${update.pluginId} to ${update.availableVersion.version}")
                } else {
                    Timber.e("Failed to update ${update.pluginId}")
                    // Restore backup on failure
                    restoreBackup(update.pluginId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to apply update for ${update.pluginId}")
            }
        }
    }
    
    private suspend fun downloadPlugin(version: PluginVersion): File {
        // Use streaming manager to download
        val pluginId = version.downloadUrl.substringAfterLast("/").removeSuffix(".apk")
        streamingManager.streamAndInstallPlugin(
            pluginId = pluginId,
            downloadUrl = version.downloadUrl,
            version = version.version,
            onProgress = { state ->
                // Update notification with progress
                if (state is PluginLoadingState.Downloading) {
                    updateNotification(state.progress)
                }
            }
        )
        
        // Return the installed plugin file
        return File(applicationContext.filesDir, "plugins/$pluginId.apk")
    }
    
    private fun createBackup(pluginId: String) {
        try {
            val pluginFile = File(applicationContext.filesDir, "plugins/$pluginId.apk")
            if (pluginFile.exists()) {
                val backupDir = File(applicationContext.filesDir, "plugin_backups")
                backupDir.mkdirs()
                val backupFile = File(backupDir, "$pluginId.backup")
                pluginFile.copyTo(backupFile, overwrite = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create backup for $pluginId")
        }
    }
    
    private fun restoreBackup(pluginId: String) {
        try {
            val backupFile = File(applicationContext.filesDir, "plugin_backups/$pluginId.backup")
            if (backupFile.exists()) {
                val pluginFile = File(applicationContext.filesDir, "plugins/$pluginId.apk")
                backupFile.copyTo(pluginFile, overwrite = true)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore backup for $pluginId")
        }
    }
    
    private fun notifyUpdatesAvailable(updates: List<PluginVersionUpdate>) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_plugin_updates", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_PLUGIN_UPDATES
        )
            .setContentTitle("Plugin Updates Available")
            .setContentText("${updates.size} plugin(s) have updates available")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_UPDATES_AVAILABLE, notification)
    }
    
    private fun createForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannel(
            CHANNEL_PLUGIN_UPDATES,
            "Plugin Updates",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
        
        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_PLUGIN_UPDATES
        )
            .setContentTitle("Checking for Plugin Updates")
            .setContentText("Scanning for available updates...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        
        return ForegroundInfo(NOTIFICATION_UPDATE_CHECK, notification)
    }
    
    private fun updateNotification(progress: Float) {
        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_PLUGIN_UPDATES
        )
            .setContentTitle("Downloading Plugin Update")
            .setContentText("${(progress * 100).toInt()}% complete")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_UPDATE_CHECK, notification)
    }
    
    companion object {
        const val KEY_AUTO_UPDATE = "auto_update"
        
        private const val CHANNEL_PLUGIN_UPDATES = "plugin_updates"
        private const val NOTIFICATION_UPDATE_CHECK = 1001
        private const val NOTIFICATION_UPDATES_AVAILABLE = 1002
    }
}
