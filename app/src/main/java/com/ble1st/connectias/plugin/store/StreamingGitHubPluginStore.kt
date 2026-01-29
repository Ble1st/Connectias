package com.ble1st.connectias.plugin.store

import android.content.Context
import com.ble1st.connectias.plugin.StreamingPluginManager
import com.ble1st.connectias.plugin.sdk.PluginCategory
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.streaming.PluginLoadingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced GitHub plugin store with streaming support
 */
@Singleton
class StreamingGitHubPluginStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamingPluginManager: StreamingPluginManager
) {
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val REPO_OWNER = "Ble1st"
        private const val REPO_NAME = "Connectias-Plugins"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Longer timeout for streaming
        .build()
    
    @Serializable
    data class GitHubRelease(
        val tag_name: String,
        val name: String,
        val body: String,
        val published_at: String,
        val assets: List<GitHubAsset>
    )
    
    @Serializable
    data class GitHubAsset(
        val name: String,
        val content_type: String,
        val size: Long,
        val browser_download_url: String
    )
    
    data class StorePlugin(
        val id: String,
        val name: String,
        val description: String,
        val version: String,
        val downloadUrl: String,
        val checksum: String,
        val category: PluginCategory,
        val releaseDate: String,
        val releaseNotes: String,
        val fileSize: Long,
        val isInstalled: Boolean = false,
        val installedVersion: String? = null,
        val canUpdate: Boolean = false,
        val supportsStreaming: Boolean = true
    )
    
    /**
     * Fetch all available plugins from GitHub releases
     */
    suspend fun getAvailablePlugins(): Result<List<StorePlugin>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$REPO_OWNER/$REPO_NAME/releases")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("GitHub API error: ${response.code}"))
            }
            
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))
            
            val releases = json.decodeFromString<List<GitHubRelease>>(responseBody)
            val installedPlugins = streamingPluginManager.getLoadedPlugins().associateBy { it.metadata.pluginId }
            
            // Group releases by plugin ID and keep only the latest version
            val latestReleases = mutableMapOf<String, GitHubRelease>()
            releases.forEach { release ->
                val pluginId = extractPluginId(release.name, release.tag_name)
                if (pluginId != null) {
                    val existing = latestReleases[pluginId]
                    if (existing == null || isNewerRelease(release, existing)) {
                        latestReleases[pluginId] = release
                    }
                }
            }
            
            val storePlugins = latestReleases.values.mapNotNull { release ->
                // Find the APK asset
                val apkAsset = release.assets.find { 
                    it.name.endsWith(".apk") && !it.name.contains("debug") 
                } ?: return@mapNotNull null
                
                // Extract plugin ID from release name or tag
                val pluginId = extractPluginId(release.name, release.tag_name)
                    ?: return@mapNotNull null
                
                // Parse version from tag
                val version = release.tag_name.removePrefix("v").removeSuffix("-${pluginId}")
                
                // Determine category (default to UTILITY for now)
                val category = PluginCategory.UTILITY
                
                // Check if already installed
                val installedPlugin = installedPlugins[pluginId]
                val isInstalled = installedPlugin != null
                val installedVersion = installedPlugin?.metadata?.version
                val canUpdate = isInstalled && isNewerVersion(version, installedVersion ?: "")
                
                StorePlugin(
                    id = pluginId,
                    name = release.name.removeSuffix(" v$version"),
                    description = extractDescription(release.body),
                    version = version,
                    downloadUrl = apkAsset.browser_download_url,
                    checksum = "", // Will be filled during download
                    category = category,
                    releaseDate = release.published_at,
                    releaseNotes = release.body,
                    fileSize = apkAsset.size,
                    isInstalled = isInstalled,
                    installedVersion = installedVersion,
                    canUpdate = canUpdate,
                    supportsStreaming = apkAsset.size > 5 * 1024 * 1024 // Enable streaming for files > 5MB
                )
            }
            
            Result.success(storePlugins)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch plugins from GitHub")
            Result.failure(e)
        }
    }
    
    /**
     * Stream and install a plugin from the store
     */
    suspend fun streamAndInstallPlugin(
        storePlugin: StorePlugin,
        onProgress: (PluginLoadingState) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("Streaming plugin: ${storePlugin.name} v${storePlugin.version}")
            
            // Use streaming plugin manager for installation
            val result = streamingPluginManager.streamAndInstallPlugin(
                pluginId = storePlugin.id,
                downloadUrl = storePlugin.downloadUrl,
                version = storePlugin.version,
                onProgress = onProgress
            )
            
            result.onSuccess {
                Timber.i("Plugin streamed and installed successfully: ${storePlugin.name}")
            }.onFailure { error ->
                Timber.e(error, "Failed to stream and install plugin: ${storePlugin.id}")
            }
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to stream/install plugin: ${storePlugin.id}")
            Result.failure(e)
        }
    }
    
    /**
     * Get loading states for plugin downloads
     */
    fun getLoadingStates(): Flow<Map<String, PluginLoadingState>> {
        return streamingPluginManager.getLoadingStates()
    }
    
    /**
     * Check for updates to installed plugins
     */
    suspend fun checkForUpdates(): Result<List<StorePlugin>> = withContext(Dispatchers.IO) {
        try {
            val availablePlugins = getAvailablePlugins().getOrThrow()
            val updates = availablePlugins.filter { it.canUpdate }
            Result.success(updates)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Search plugins by name or description
     */
    suspend fun searchPlugins(query: String): Result<List<StorePlugin>> = withContext(Dispatchers.IO) {
        try {
            val allPlugins = getAvailablePlugins().getOrThrow()
            val filtered = allPlugins.filter { plugin ->
                plugin.name.contains(query, ignoreCase = true) ||
                plugin.description.contains(query, ignoreCase = true) ||
                plugin.id.contains(query, ignoreCase = true)
            }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): com.ble1st.connectias.plugin.streaming.CacheStats {
        return streamingPluginManager.getCacheStats()
    }
    
    /**
     * Optimize cache
     */
    suspend fun optimizeCache(): com.ble1st.connectias.plugin.streaming.CacheOptimizationResult {
        return streamingPluginManager.optimizeCache()
    }
    
    private fun extractPluginId(releaseName: String, tagName: String): String? {
        // Try to extract from tag name first (format: "v0.0.5-test-plugin")
        val tagParts = tagName.split("-")
        if (tagParts.size > 1) {
            return tagParts.drop(1).joinToString("-")
        }
        
        // Fallback to release name
        return if (releaseName.contains(" ")) {
            releaseName.split(" ").first().lowercase()
        } else {
            releaseName.lowercase()
        }
    }
    
    private fun extractDescription(releaseBody: String): String {
        val lines = releaseBody.split("\n")
        return lines.firstOrNull { it.isNotBlank() } ?: "No description available"
    }
    
    private fun isNewerVersion(current: String, installed: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val installedParts = installed.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(currentParts.size, installedParts.size)) {
                val currentPart = currentParts.getOrNull(i) ?: 0
                val installedPart = installedParts.getOrNull(i) ?: 0
                
                if (currentPart > installedPart) return true
                if (currentPart < installedPart) return false
            }
            false
        } catch (e: Exception) {
            current > installed
        }
    }
    
    private fun isNewerRelease(release1: GitHubRelease, release2: GitHubRelease): Boolean {
        return try {
            val version1 = release1.tag_name.removePrefix("v")
            val version2 = release2.tag_name.removePrefix("v")
            isNewerVersion(version1, version2)
        } catch (e: Exception) {
            // Fallback to date comparison
            release1.published_at > release2.published_at
        }
    }
}
