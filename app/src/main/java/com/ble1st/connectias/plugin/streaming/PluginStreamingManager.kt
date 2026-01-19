package com.ble1st.connectias.plugin.streaming

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.buffer
import okio.sink
import okio.source
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plugin streaming manager for chunk-based downloads and installation
 */
@Singleton
class PluginStreamingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamCache: StreamCache,
    private val okHttpClient: OkHttpClient
) {
    
    companion object {
        private const val DEFAULT_CHUNK_SIZE = 1024 * 1024 // 1MB chunks
        private const val MAX_CONCURRENT_CHUNKS = 4
        private const val TIMEOUT_SECONDS = 30
    }
    
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    private val _loadingStates = MutableStateFlow<Map<String, PluginLoadingState>>(emptyMap())
    val loadingStates: Flow<Map<String, PluginLoadingState>> = _loadingStates.asStateFlow()
    
    private val downloadDir = File(context.cacheDir, "plugin_downloads")
    
    init {
        downloadDir.mkdirs()
    }
    
    /**
     * Stream a plugin from the given URL with progress tracking
     */
    suspend fun streamPlugin(
        pluginId: String,
        downloadUrl: String,
        version: String,
        onProgress: (Float) -> Unit = {}
    ): Result<PluginStream> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = streamCache.getCachedPlugin(pluginId)
            if (cached != null && cached.version == version) {
                Timber.d("Loading plugin from cache: $pluginId")
                return@withContext reconstructPluginStream(cached)
            }
            
            updateLoadingState(pluginId, PluginLoadingState.Downloading(
                progress = 0f,
                stage = "Starting download",
                bytesDownloaded = 0,
                totalBytes = 0
            ))
            
            // Get file info first
            val request = Request.Builder()
                .url(downloadUrl)
                .head()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Failed to get plugin info: ${response.code}")
            }
            
            val totalSize = response.body?.contentLength() ?: -1
            val chunkSize = calculateOptimalChunkSize(totalSize)
            val totalChunks = Math.ceil(totalSize.toDouble() / chunkSize).toInt()
            
            updateLoadingState(pluginId, PluginLoadingState.Downloading(
                progress = 0f,
                stage = "Downloading chunks",
                bytesDownloaded = 0,
                totalBytes = totalSize
            ))
            
            // Download in chunks
            val chunks = mutableListOf<PluginStreamChunk>()
            var bytesDownloaded = 0L
            val startTime = System.currentTimeMillis()
            
            for (chunkIndex in 0 until totalChunks) {
                val startByte = chunkIndex * chunkSize.toLong()
                val endByte = minOf(startByte + chunkSize - 1, totalSize - 1)
                
                val chunkRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Range", "bytes=$startByte-$endByte")
                    .build()
                
                val chunkResponse = okHttpClient.newCall(chunkRequest).execute()
                if (!chunkResponse.isSuccessful) {
                    throw IOException("Failed to download chunk $chunkIndex: ${chunkResponse.code}")
                }
                
                val chunkData = chunkResponse.body?.bytes()
                    ?: throw IOException("Empty chunk response")
                
                val checksum = calculateSHA256(chunkData)
                
                val chunk = PluginStreamChunk(
                    index = chunkIndex,
                    data = chunkData,
                    checksum = checksum,
                    isLast = chunkIndex == totalChunks - 1
                )
                
                chunks.add(chunk)
                bytesDownloaded += chunkData.size
                
                // Cache the chunk
                streamCache.cacheChunk(pluginId, chunkIndex, chunkData)
                
                // Update progress
                val progress = if (totalSize > 0) {
                    bytesDownloaded.toFloat() / totalSize
                } else {
                    (chunkIndex + 1).toFloat() / totalChunks
                }
                
                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                val speed = if (elapsed > 0) bytesDownloaded / elapsed else 0f
                
                updateLoadingState(pluginId, PluginLoadingState.Downloading(
                    progress = progress,
                    stage = "Downloading chunk ${chunkIndex + 1}/$totalChunks",
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalSize,
                    downloadSpeed = speed
                ))
                
                onProgress(progress)
            }
            
            // Create metadata
            val metadata = PluginStreamMetadata(
                totalChunks = totalChunks,
                chunkSize = chunkSize,
                totalSize = totalSize,
                compression = CompressionType.NONE,
                encryption = null
            )
            
            val pluginStream = PluginStream(
                metadata = metadata,
                chunks = chunks,
                pluginId = pluginId,
                version = version
            )
            
            // Cache the complete plugin info
            streamCache.cachePluginInfo(pluginId, version, metadata)
            
            updateLoadingState(pluginId, PluginLoadingState.Completed)
            
            Timber.i("Successfully streamed plugin: $pluginId ($totalSize bytes)")
            Result.success(pluginStream)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to stream plugin: $pluginId")
            updateLoadingState(pluginId, PluginLoadingState.Failed(e, "Streaming"))
            Result.failure(e)
        }
    }
    
    /**
     * Load a plugin from chunks into a file
     */
    suspend fun loadPluginInChunks(
        stream: PluginStream,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): Result<PluginPackage> = withContext(Dispatchers.IO) {
        try {
            updateLoadingState(stream.pluginId, PluginLoadingState.Installing(
                currentStep = 1,
                totalSteps = 3,
                currentOperation = "Reassembling chunks"
            ))
            
            // Create temporary file
            val tempFile = File(downloadDir, "${stream.pluginId}_${stream.version}.tmp")
            
            // Reassemble file from chunks
            tempFile.outputStream().use { output ->
                stream.chunks.sortedBy { it.index }.forEach { chunk ->
                    output.write(chunk.data)
                }
            }
            
            updateLoadingState(stream.pluginId, PluginLoadingState.Installing(
                currentStep = 2,
                totalSteps = 3,
                currentOperation = "Verifying integrity"
            ))
            
            // Verify checksums
            val verified = verifyChunks(stream.chunks)
            if (!verified) {
                tempFile.delete()
                throw IOException("Chunk verification failed")
            }
            
            updateLoadingState(stream.pluginId, PluginLoadingState.Installing(
                currentStep = 3,
                totalSteps = 3,
                currentOperation = "Finalizing package"
            ))
            
            // Create final plugin file
            val pluginFile = File(context.filesDir, "plugins/${stream.pluginId}.apk")
            pluginFile.parentFile?.mkdirs()
            
            if (pluginFile.exists()) {
                pluginFile.delete()
            }
            
            tempFile.copyTo(pluginFile)
            tempFile.delete()
            
            // Make read-only (Android security requirement)
            pluginFile.setReadOnly()
            
            // Extract metadata from the assembled plugin file
            val metadata = extractMetadataFromApk(pluginFile)
            
            val pluginPackage = PluginPackage(
                pluginFile = pluginFile,
                metadata = metadata,
                stream = stream
            )
            
            updateLoadingState(stream.pluginId, PluginLoadingState.Completed)
            
            Timber.i("Successfully assembled plugin: ${stream.pluginId}")
            Result.success(pluginPackage)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin chunks: ${stream.pluginId}")
            updateLoadingState(stream.pluginId, PluginLoadingState.Failed(e, "Chunk loading"))
            Result.failure(e)
        }
    }
    
    /**
     * Cancel an ongoing download
     */
    fun cancelDownload(pluginId: String) {
        activeDownloads[pluginId]?.cancel()
        activeDownloads.remove(pluginId)
        updateLoadingState(pluginId, PluginLoadingState.Cancelled("User cancelled"))
    }
    
    /**
     * Get current loading state for a plugin
     */
    fun getLoadingState(pluginId: String): PluginLoadingState? {
        return _loadingStates.value[pluginId]
    }
    
    private fun updateLoadingState(pluginId: String, state: PluginLoadingState) {
        val currentStates = _loadingStates.value.toMutableMap()
        currentStates[pluginId] = state
        _loadingStates.value = currentStates
        
        // Clean up completed states after delay
        if (state is PluginLoadingState.Completed || 
            state is PluginLoadingState.Failed || 
            state is PluginLoadingState.Cancelled) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(5000) // Keep state for 5 seconds
                val updatedStates = _loadingStates.value.toMutableMap()
                updatedStates.remove(pluginId)
                _loadingStates.value = updatedStates
            }
        }
    }
    
    private fun calculateOptimalChunkSize(totalSize: Long): Int {
        return when {
            totalSize < 5 * 1024 * 1024 -> 512 * 1024 // 512KB for small files
            totalSize < 50 * 1024 * 1024 -> 1024 * 1024 // 1MB for medium files
            totalSize < 200 * 1024 * 1024 -> 2 * 1024 * 1024 // 2MB for large files
            else -> 4 * 1024 * 1024 // 4MB for very large files
        }
    }
    
    private fun verifyChunks(chunks: List<PluginStreamChunk>): Boolean {
        return chunks.all { chunk ->
            val calculatedChecksum = calculateSHA256(chunk.data)
            calculatedChecksum == chunk.checksum
        }
    }
    
    private fun calculateSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private suspend fun reconstructPluginStream(cached: CachedPlugin): Result<PluginStream> = withContext(Dispatchers.IO) {
        try {
            val chunks = cached.chunks.map { (index, data) ->
                PluginStreamChunk(
                    index = index,
                    data = data,
                    checksum = calculateSHA256(data),
                    isLast = index == cached.metadata.totalChunks - 1
                )
            }
            
            val stream = PluginStream(
                metadata = cached.metadata,
                chunks = chunks,
                pluginId = cached.pluginId,
                version = cached.version
            )
            
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Extract plugin metadata from APK file
     */
    private fun extractMetadataFromApk(apkFile: File): com.ble1st.connectias.plugin.sdk.PluginMetadata {
        return java.util.zip.ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("plugin-manifest.json")
                ?: zip.getEntry("assets/plugin-manifest.json")
                ?: throw IllegalArgumentException("Plugin manifest not found in ${apkFile.name}")
            
            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(manifestJson)
            
            com.ble1st.connectias.plugin.sdk.PluginMetadata(
                pluginId = json.getString("pluginId"),
                pluginName = json.getString("pluginName"),
                version = json.getString("version"),
                author = json.optString("author", "Unknown"),
                minApiLevel = json.optInt("minApiLevel", 21),
                maxApiLevel = json.optInt("maxApiLevel", 34),
                minAppVersion = json.optString("minAppVersion", "1.0.0"),
                nativeLibraries = json.optJSONArray("nativeLibraries")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                fragmentClassName = json.optString("fragmentClassName", null),
                description = json.optString("description", "No description available"),
                permissions = json.optJSONArray("permissions")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                category = try {
                    com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                        json.optString("category", "UTILITY")
                    )
                } catch (e: Exception) {
                    com.ble1st.connectias.plugin.sdk.PluginCategory.UTILITY
                },
                dependencies = json.optJSONArray("dependencies")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        }
    }
}
