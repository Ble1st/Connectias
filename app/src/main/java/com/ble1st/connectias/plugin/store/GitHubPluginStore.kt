package com.ble1st.connectias.plugin.store

import android.content.Context
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.sdk.PluginCategory
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.store.GitHubPluginStore.GitHubRelease
import com.ble1st.connectias.plugin.version.PluginVersion
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * GitHub-based plugin store implementation
 * Fetches plugins from GitHub releases and provides installation functionality
 */
class GitHubPluginStore(
    private val context: Context,
    private val pluginManager: PluginManagerSandbox
) {
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val REPO_OWNER = "Ble1st"
        private const val REPO_NAME = "Connectias-Plugins"
        private const val PLUGIN_DOWNLOAD_TIMEOUT = 30_000L // 30 seconds
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(PLUGIN_DOWNLOAD_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    @Serializable
    data class GitHubRelease(
        val tag_name: String,
        val name: String,
        val body: String,
        val published_at: String,
        val assets: List<GitHubAsset>,
        val prerelease: Boolean = false
    )
    
    @Serializable
    data class GitHubAsset(
        val name: String,
        val content_type: String,
        val size: Long,
        val browser_download_url: String
    )
    
    data class PluginHashInfo(
        val pluginId: String,
        val sha256Hash: String,
        val downloadUrl: String
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
        val canUpdate: Boolean = false
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
            val installedPlugins = pluginManager.getLoadedPlugins().associateBy { it.metadata.pluginId }
            
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
                
                // Try to get checksum from .sha256sum or sha256sum.txt file in release assets
                val sha256Asset = release.assets.find { 
                    it.name.endsWith(".sha256sum") || it.name.equals("sha256sum.txt", ignoreCase = true)
                }
                val checksum = if (sha256Asset != null) {
                    try {
                        val hashResponse = httpClient.newCall(
                            Request.Builder().url(sha256Asset.browser_download_url).build()
                        ).execute()
                        if (hashResponse.isSuccessful) {
                            val hashContent = hashResponse.body?.string() ?: ""
                            // Parse hash file format: <hash>  <filename>
                            hashContent.split(" ").firstOrNull()?.lowercase() ?: ""
                        } else ""
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to fetch checksum for $pluginId")
                        ""
                    }
                } else ""
                
                StorePlugin(
                    id = pluginId,
                    name = release.name.removeSuffix(" v$version"),
                    description = extractDescription(release.body),
                    version = version,
                    downloadUrl = apkAsset.browser_download_url,
                    checksum = checksum,
                    category = category,
                    releaseDate = release.published_at,
                    releaseNotes = release.body,
                    fileSize = apkAsset.size,
                    isInstalled = isInstalled,
                    installedVersion = installedVersion,
                    canUpdate = canUpdate
                )
            }
            
            Result.success(storePlugins)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch plugins from GitHub")
            Result.failure(e)
        }
    }
    
    /**
     * Download and install a plugin from the store
     */
    suspend fun downloadAndInstallPlugin(storePlugin: StorePlugin): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("Downloading plugin: ${storePlugin.name} v${storePlugin.version}")
            
            // Download the plugin
            val request = Request.Builder()
                .url(storePlugin.downloadUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Download failed: ${response.code}"))
            }
            
            val responseBody = response.body?.byteStream()
                ?: return@withContext Result.failure(IOException("Empty response body"))
            
            // Save to temporary file
            val tempFile = File(context.cacheDir, "${storePlugin.id}_temp.apk")
            tempFile.outputStream().use { output ->
                responseBody.use { input ->
                    input.copyTo(output)
                }
            }
            
            // Calculate checksum and verify
            val calculatedChecksum = calculateSHA256(tempFile)
            Timber.d("Plugin checksum: $calculatedChecksum")
            
            // Verify checksum if available from store
            if (storePlugin.checksum.isNotEmpty()) {
                if (!calculatedChecksum.equals(storePlugin.checksum, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        IOException("Checksum verification failed: expected ${storePlugin.checksum}, got $calculatedChecksum")
                    )
                }
                Timber.i("Checksum verification passed for ${storePlugin.name}")
            } else {
                Timber.w("No checksum available for ${storePlugin.name}, skipping verification")
            }
            
            // Move to plugin directory
            val pluginDir = File(context.filesDir, "plugins")
            pluginDir.mkdirs()
            val pluginFile = File(pluginDir, "${storePlugin.id}.apk")
            
            // Delete existing file if updating
            if (pluginFile.exists()) {
                pluginFile.delete()
            }
            
            tempFile.copyTo(pluginFile)
            tempFile.delete()
            
            // Make read-only (Android security requirement)
            pluginFile.setReadOnly()
            
            // Load and enable the plugin
            val loadResult = pluginManager.loadAndEnablePlugin(storePlugin.id)
            loadResult.onSuccess {
                Timber.i("Plugin installed successfully: ${storePlugin.name}")
            }.onFailure { error ->
                // Clean up on failure
                pluginFile.delete()
                return@withContext Result.failure(error)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download/install plugin: ${storePlugin.id}")
            Result.failure(e)
        }
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
    
    /**
     * Load SHA256 hash for a plugin from .sha256sum file in GitHub release
     */
    suspend fun loadPluginHash(pluginId: String, version: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$REPO_OWNER/$REPO_NAME/releases")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Failed to fetch releases: ${response.code}"))
            }
            
            val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
            val releases = json.decodeFromString<List<GitHubRelease>>(responseBody)
            
            // Find the release for this plugin version (try exact version match first)
            var release = releases.find { release ->
                val releasePluginId = extractPluginId(release.name, release.tag_name)
                if (releasePluginId == pluginId) {
                    // Extract version the same way as in getAvailablePlugins
                    val releaseVersion = release.tag_name.removePrefix("v").removeSuffix("-${pluginId}")
                    releaseVersion == version
                } else {
                    false
                }
            }
            
            // Fallback: If exact version not found, try to find release by partial plugin ID match
            // This handles cases where plugin manifest version differs from GitHub release tag version
            if (release == null) {
                Timber.d("Exact version $version not found for $pluginId, trying fallback to latest release")
                
                // Extract short plugin name from full plugin ID (e.g., "pingplugin" from "com.ble1st.connectias.pingplugin")
                val shortPluginName = pluginId.substringAfterLast(".")
                
                // Normalize both names by removing hyphens for comparison
                val normalizedShortName = shortPluginName.replace("-", "").lowercase()
                
                // Try to find a release where the tag matches (allowing for hyphen differences)
                release = releases.find { release ->
                    val extractedId = extractPluginId(release.name, release.tag_name)
                    val normalizedExtractedId = extractedId?.replace("-", "")?.lowercase()
                    
                    // Match if either contains the other, or if they're equal after normalization
                    normalizedExtractedId?.let { 
                        it.contains(normalizedShortName) || normalizedShortName.contains(it)
                    } == true
                }
                
                // Last resort: Find ANY release that has a sha256sum file
                // This assumes the repo is dedicated to this plugin or has very few plugins
                if (release == null) {
                    Timber.w("Plugin ID match failed, searching for any release with sha256sum file")
                    release = releases.find { r ->
                        r.assets.any { it.name.endsWith(".sha256sum") || it.name.equals("sha256sum.txt", ignoreCase = true) }
                    }
                }
            }
            
            if (release != null) {
                // Look for .sha256sum or sha256sum.txt file in assets
                val sha256Asset = release.assets.find { 
                    it.name.endsWith(".sha256sum") || it.name.equals("sha256sum.txt", ignoreCase = true)
                }
                if (sha256Asset != null) {
                    // Download and parse the SHA256 file
                    val hashResponse = httpClient.newCall(
                        Request.Builder().url(sha256Asset.browser_download_url).build()
                    ).execute()
                    
                    if (hashResponse.isSuccessful) {
                        val hashContent = hashResponse.body?.string() ?: return@withContext Result.failure(IOException("Empty hash file"))
                        // Parse hash file format: <hash>  <filename>
                        val hash = hashContent.split(" ").firstOrNull()
                        if (!hash.isNullOrEmpty()) {
                            Timber.i("Found SHA256 hash for $pluginId from release ${release.tag_name}")
                            return@withContext Result.success(hash.lowercase())
                        }
                    }
                }
            }
            
            Result.failure(Exception("SHA256 hash not found for $pluginId v$version"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to load SHA256 hash for $pluginId v$version")
            Result.failure(e)
        }
    }
    
    /**
     * Get all available versions for a specific plugin from GitHub releases
     */
    suspend fun getPluginVersions(pluginId: String): Result<List<PluginVersion>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$REPO_OWNER/$REPO_NAME/releases")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Failed to fetch releases: ${response.code}"))
            }
            
            val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
            val releases = json.decodeFromString<List<GitHubRelease>>(responseBody)
            
            // Filter releases for this plugin and convert to PluginVersion
            val pluginVersions = releases
                .filter { release ->
                    val releasePluginId = extractPluginId(release.name, release.tag_name)
                    releasePluginId == pluginId
                }
                .mapNotNull { release ->
                    val asset = release.assets.find { it.name.endsWith(".aab") || it.name.endsWith(".apk") }
                    if (asset != null) {
                        val version = release.tag_name.removePrefix("v")
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                        val releaseDate = dateFormat.parse(release.published_at) ?: Date()
                        
                        PluginVersion(
                            version = version,
                            versionCode = extractVersionCode(version),
                            releaseDate = releaseDate,
                            changelog = extractDescription(release.body),
                            minHostVersion = "1.0.0", // TODO: Extract from manifest
                            size = asset.size,
                            checksum = "", // TODO: Get from release assets or calculate
                            downloadUrl = asset.browser_download_url,
                            isPrerelease = release.prerelease
                        )
                    } else null
                }
                .sortedByDescending { it.releaseDate }
            
            Result.success(pluginVersions)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get plugin versions for $pluginId")
            Result.failure(e)
        }
    }
    
    private fun extractVersionCode(version: String): Int {
        // Simple version code extraction from version string
        return try {
            val parts = version.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            1
        }
    }
    
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
