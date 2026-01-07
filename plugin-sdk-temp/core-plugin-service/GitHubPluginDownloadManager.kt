package com.ble1st.connectias.core.plugin

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray

/**
 * Downloads plugins from GitHub Releases
 * 
 * GitHub API: https://docs.github.com/en/rest/releases/releases
 */
class GitHubPluginDownloadManager(
    private val context: Context,
    private val pluginDirectory: File,
    private val httpClient: OkHttpClient
) {
    
    private val GITHUB_API_BASE = "https://api.github.com"
    private val PLUGIN_REPO = "Ble1st/Connectias-Plugins"
    private val PLUGIN_TAG_PREFIX = "v"  // v1.2.0-plugin-name
    
    data class PluginRelease(
        val pluginId: String,
        val version: String,
        val tagName: String,
        val aabUrl: String,
        val manifestUrl: String,
        val sha256Url: String,
        val body: String,
        val releaseDate: String,
        val downloadCount: Int = 0
    )
    
    /**
     * Fetches all available plugins from GitHub Releases
     */
    suspend fun fetchAvailablePlugins(): Result<List<PluginRelease>> = 
        withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$GITHUB_API_BASE/repos/$PLUGIN_REPO/releases")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("GitHub API error: ${response.code}")
                )
            }
            
            val body = response.body?.string() 
                ?: return@withContext Result.failure(Exception("Empty response"))
            
            val releasesJson = JSONArray(body)
            val plugins = mutableListOf<PluginRelease>()
            
            for (i in 0 until releasesJson.length()) {
                val release = releasesJson.getJSONObject(i)
                
                // Filter only plugin releases (tag_name = v*-plugin-*)
                val tagName = release.getString("tag_name")
                if (!isPluginRelease(tagName)) continue
                
                val aabAsset = findAssetByName(release, ".aab")
                val manifestAsset = findAssetByName(release, "plugin-manifest.json")
                val sha256Asset = findAssetByName(release, "sha256sum.txt")
                
                if (aabAsset != null && manifestAsset != null && sha256Asset != null) {
                    val pluginId = extractPluginId(tagName)
                    
                    plugins.add(PluginRelease(
                        pluginId = pluginId,
                        version = extractVersion(tagName),
                        tagName = tagName,
                        aabUrl = aabAsset.getString("browser_download_url"),
                        manifestUrl = manifestAsset.getString("browser_download_url"),
                        sha256Url = sha256Asset.getString("browser_download_url"),
                        body = release.getString("body"),
                        releaseDate = release.getString("published_at"),
                        downloadCount = aabAsset.getInt("download_count")
                    ))
                }
            }
            
            Timber.d("Found ${plugins.size} available plugins on GitHub")
            Result.success(plugins)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch plugins from GitHub")
            Result.failure(e)
        }
    }
    
    /**
     * Checks if update is available for a specific plugin
     */
    suspend fun checkForUpdate(pluginId: String, currentVersion: String): 
        Result<PluginRelease?> = withContext(Dispatchers.IO) {
        
        try {
            val allPlugins = fetchAvailablePlugins().getOrNull() 
                ?: return@withContext Result.success(null)
            
            val latestRelease = allPlugins
                .filter { it.pluginId == pluginId }
                .maxByOrNull { it.version.toVersionCode() }
            
            if (latestRelease != null && 
                latestRelease.version.toVersionCode() > currentVersion.toVersionCode()) {
                
                Timber.d("Update available for $pluginId: ${latestRelease.version}")
                Result.success(latestRelease)
            } else {
                Result.success(null)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for update: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Gets the latest release for a plugin
     */
    suspend fun getLatestRelease(pluginId: String): Result<PluginRelease?> = 
        withContext(Dispatchers.IO) {
        try {
            val allPlugins = fetchAvailablePlugins().getOrNull()
                ?: return@withContext Result.success(null)
            
            val latest = allPlugins
                .filter { it.pluginId == pluginId }
                .maxByOrNull { it.version.toVersionCode() }
            
            Result.success(latest)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get latest release: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Finds a release by plugin ID and version
     */
    suspend fun findRelease(pluginId: String, version: String): Result<PluginRelease?> =
        withContext(Dispatchers.IO) {
        try {
            val allPlugins = fetchAvailablePlugins().getOrNull()
                ?: return@withContext Result.success(null)
            
            val release = allPlugins.find { 
                it.pluginId == pluginId && it.version == version 
            }
            
            Result.success(release)
        } catch (e: Exception) {
            Timber.e(e, "Failed to find release: $pluginId v$version")
            Result.failure(e)
        }
    }
    
    /**
     * Downloads Plugin AAB with verification
     */
    suspend fun downloadPlugin(
        release: PluginRelease,
        onProgress: (current: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 1. Download Manifest
            val manifestJson = downloadFileAsString(release.manifestUrl)
            val manifest = JSONObject(manifestJson)
            
            // 2. Download SHA256 Checksum
            val sha256Text = downloadFileAsString(release.sha256Url)
            val expectedChecksum = sha256Text.split("  ")[0].trim()  // "hash  filename"
            
            // 3. Download AAB
            val tempFile = File(pluginDirectory, "temp_${release.pluginId}.aab")
            downloadFile(
                url = release.aabUrl,
                outputFile = tempFile,
                onProgress = onProgress
            )
            
            // 4. Verify Checksum
            val actualChecksum = tempFile.sha256()
            if (actualChecksum != expectedChecksum) {
                tempFile.delete()
                return@withContext Result.failure(
                    Exception("Checksum mismatch! Expected: $expectedChecksum, Got: $actualChecksum")
                )
            }
            
            // 5. Save in correct directory
            val finalFile = File(
                pluginDirectory,
                "${release.pluginId}-${release.version}.aab"
            )
            tempFile.renameTo(finalFile)
            
            Timber.i("Plugin downloaded & verified: ${release.pluginId} v${release.version}")
            Result.success(finalFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to download plugin: ${release.pluginId}")
            Result.failure(e)
        }
    }
    
    /**
     * Helper function: Download file with progress callback
     */
    private suspend fun downloadFile(
        url: String,
        outputFile: File,
        onProgress: (current: Long, total: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }
        
        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        var downloaded: Long = 0
        
        outputFile.outputStream().use { output ->
            body.byteStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    onProgress(downloaded, contentLength)
                }
            }
        }
        
        outputFile
    }
    
    /**
     * Helper function: Download file as string
     */
    private suspend fun downloadFileAsString(url: String): String = 
        withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        response.body?.string() ?: throw Exception("Empty response body")
    }
    
    private fun isPluginRelease(tagName: String): Boolean {
        // Format: v1.2.0-plugin-name
        val pattern = Regex("^v[\\d.]+-.+$")
        return pattern.matches(tagName)
    }
    
    private fun extractPluginId(tagName: String): String {
        // v1.2.0-network-tools → network-tools
        return tagName
            .removePrefix("v")
            .replaceFirst(Regex("^[\\d.]+-"), "")
    }
    
    private fun extractVersion(tagName: String): String {
        // v1.2.0-plugin-name → 1.2.0
        return tagName
            .removePrefix("v")
            .substringBefore("-")
    }
    
    private fun findAssetByName(releaseJson: JSONObject, filename: String): 
        JSONObject? {
        
        val assets = releaseJson.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").contains(filename)) {
                return asset
            }
        }
        return null
    }
}

// Extension Functions
private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return inputStream().buffered().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private fun String.toVersionCode(): Long {
    // "1.2.3" → 10203000
    return split(".").let { parts ->
        val padded = parts + List(4 - parts.size) { "0" }
        padded.take(4).mapIndexed { i, part ->
            part.toIntOrNull()?.toLong()?.times(1000L.pow(3 - i)) ?: 0L
        }.sum()
    }
}

private fun Long.pow(exponent: Int): Long {
    return (1..exponent).fold(1L) { acc, _ -> acc * this }
}
