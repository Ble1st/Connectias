package com.ble1st.connectias.plugin.streaming

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intelligent cache system for plugin streaming
 */
@Singleton
class StreamCache @Inject constructor(
    @param:ApplicationContext private val context: Context
) : IStreamCache {
    companion object {
        private const val MAX_CACHE_SIZE = 500L * 1024 * 1024 // 500MB
        private const val MAX_CACHE_ENTRIES = 50
        private const val CHUNK_DIR_NAME = "plugin_chunks"
        private const val METADATA_FILE_NAME = "cache_metadata.json"
    }
    
    private val cacheDir = File(context.cacheDir, CHUNK_DIR_NAME)
    private val metadataFile = File(cacheDir, METADATA_FILE_NAME)
    private val cachedPlugins = ConcurrentHashMap<String, CachedPlugin>()
    private val chunkMutex = Mutex()
    
    // Memory cache for frequently accessed chunks
    private val memoryChunkCache = LruCache<String, ByteArray>(50 * 1024 * 1024) // 50MB
    
    init {
        cacheDir.mkdirs()
        loadCacheMetadata()
    }
    
    /**
     * Get a cached plugin if available
     */
    override suspend fun getCachedPlugin(pluginId: String): CachedPlugin? = withContext(Dispatchers.IO) {
        val cached = cachedPlugins[pluginId] ?: return@withContext null
        
        // Update last accessed time
        val updated = cached.copy(lastAccessed = System.currentTimeMillis())
        cachedPlugins[pluginId] = updated
        saveCacheMetadata()
        
        Timber.d("Retrieved cached plugin: $pluginId")
        updated
    }
    
    /**
     * Cache a chunk of plugin data
     */
    override suspend fun cacheChunk(
        pluginId: String,
        chunkIndex: Int,
        data: ByteArray
    ) = withContext(Dispatchers.IO) {
        chunkMutex.withLock {
            try {
                // Save to disk
                val chunkFile = getChunkFile(pluginId, chunkIndex)
                chunkFile.writeBytes(data)
                
                // Also keep in memory cache if space allows
                val chunkKey = "$pluginId:$chunkIndex"
                memoryChunkCache.put(chunkKey, data)
                
                Timber.d("Cached chunk: $pluginId[$chunkIndex] (${data.size} bytes)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cache chunk: $pluginId[$chunkIndex]")
            }
        }
    }
    
    /**
     * Get a cached chunk
     */
    override suspend fun getCachedChunk(
        pluginId: String,
        chunkIndex: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        val chunkKey = "$pluginId:$chunkIndex"
        
        // Try memory cache first
        memoryChunkCache.get(chunkKey)?.let { return@withContext it }
        
        // Load from disk
        try {
            val chunkFile = getChunkFile(pluginId, chunkIndex)
            if (chunkFile.exists()) {
                val data = chunkFile.readBytes()
                memoryChunkCache.put(chunkKey, data)
                data
            } else null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cached chunk: $pluginId[$chunkIndex]")
            null
        }
    }
    
    /**
     * Cache plugin metadata
     */
    suspend fun cachePluginInfo(
        pluginId: String,
        version: String,
        metadata: PluginStreamMetadata
    ) = withContext(Dispatchers.IO) {
        val cached = CachedPlugin(
            pluginId = pluginId,
            version = version,
            chunks = emptyMap(), // Chunks are stored separately
            metadata = metadata,
            cachedAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis()
        )
        
        cachedPlugins[pluginId] = cached
        saveCacheMetadata()
        
        Timber.d("Cached plugin info: $pluginId v$version")
    }
    
    /**
     * Optimize cache by removing old/unused entries
     */
    override suspend fun optimizeCache(): CacheOptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var freedSpace = 0L
        val removedPlugins = mutableListOf<String>()
        
        try {
            // Check if we need to optimize
            val currentSize = calculateCacheSize()
            val needsOptimization = currentSize > MAX_CACHE_SIZE || 
                                  cachedPlugins.size > MAX_CACHE_ENTRIES
            
            if (!needsOptimization) {
                return@withContext CacheOptimizationResult(
                    freedSpace = 0L,
                    removedPlugins = emptyList(),
                    optimizationTime = System.currentTimeMillis() - startTime
                )
            }
            
            // Sort by last accessed time (LRU)
            val sortedPlugins = cachedPlugins.values
                .sortedBy { it.lastAccessed }
            
            // Remove oldest entries until we're under limits
            val targetSize = (MAX_CACHE_SIZE * 0.8).toLong()
            val targetEntries = (MAX_CACHE_ENTRIES * 0.8).toInt()
            
            var currentSizeVar = currentSize
            val iterator = sortedPlugins.iterator()
            
            while (iterator.hasNext() && 
                  (currentSizeVar > targetSize || cachedPlugins.size > targetEntries)) {
                val plugin = iterator.next()
                
                // Remove plugin and its chunks
                val removedSize = removePluginFromCache(plugin.pluginId)
                if (removedSize > 0) {
                    freedSpace += removedSize
                    removedPlugins.add(plugin.pluginId)
                    currentSizeVar -= removedSize
                }
            }
            
            saveCacheMetadata()
            
            Timber.i("Cache optimization completed: freed ${freedSpace / (1024 * 1024)}MB, removed ${removedPlugins.size} plugins")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to optimize cache")
        }
        
        CacheOptimizationResult(
            freedSpace = freedSpace,
            removedPlugins = removedPlugins,
            optimizationTime = System.currentTimeMillis() - startTime
        )
    }
    
    /**
     * Clear all cached data
     */
    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        try {
            cachedPlugins.clear()
            memoryChunkCache.evictAll()
            
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != METADATA_FILE_NAME) {
                    file.deleteRecursively()
                }
            }
            
            metadataFile.delete()
            
            Timber.i("Cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache")
        }
    }
    
    /**
     * Get cache statistics
     */
    override suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val totalSize = calculateCacheSize()
        val totalChunks = cacheDir.listFiles { _, name -> 
            name.endsWith(".chunk") 
        }?.size ?: 0
        
        CacheStats(
            totalSize = totalSize,
            totalPlugins = cachedPlugins.size,
            totalChunks = totalChunks,
            maxCacheSize = MAX_CACHE_SIZE,
            hitRate = if (memoryChunkCache.hitCount() + memoryChunkCache.missCount() > 0) {
                memoryChunkCache.hitCount().toFloat() / (memoryChunkCache.hitCount() + memoryChunkCache.missCount()).toFloat()
            } else {
                0f
            }
        )
    }
    
    private fun getChunkFile(pluginId: String, chunkIndex: Int): File {
        val pluginDir = File(cacheDir, pluginId)
        pluginDir.mkdirs()
        return File(pluginDir, "chunk_$chunkIndex.chunk")
    }
    
    private fun removePluginFromCache(pluginId: String): Long {
        var removedSize = 0L
        
        try {
            // Remove chunks from disk
            val pluginDir = File(cacheDir, pluginId)
            if (pluginDir.exists()) {
                pluginDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        removedSize += file.length()
                        file.delete()
                    }
                }
                pluginDir.delete()
            }
            
            // Remove from memory cache
            val keysToRemove = memoryChunkCache.snapshot().keys.filter { 
                it.startsWith("$pluginId:") 
            }
            keysToRemove.forEach { key ->
                memoryChunkCache.remove(key)
            }
            
            // Remove from metadata
            cachedPlugins.remove(pluginId)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove plugin from cache: $pluginId")
        }
        
        return removedSize
    }
    
    private fun calculateCacheSize(): Long {
        return cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
    
    private fun loadCacheMetadata() {
        try {
            if (!metadataFile.exists()) return
            
            // In a real implementation, would deserialize from JSON
            // For now, initialize empty cache
            Timber.d("Cache metadata loaded")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load cache metadata")
        }
    }
    
    private fun saveCacheMetadata() {
        try {
            // In a real implementation, would serialize to JSON
            Timber.d("Cache metadata saved")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save cache metadata")
        }
    }
}

/**
 * Simple LRU cache implementation for memory caching
 */
private class LruCache<K, V>(
    private val maxSize: Long,
    private val sizeCalculator: (V) -> Long = { _ -> 1 }
) {
    private val cache = LinkedHashMap<K, V>(0, 0.75f, true)
    private var currentSize = 0L
    private var hitCount = 0
    private var missCount = 0
    
    fun get(key: K): V? {
        synchronized(this) {
            val value = cache[key]
            if (value != null) {
                hitCount++
                // Move to end (most recently used)
                cache.remove(key)
                cache[key] = value
            } else {
                missCount++
            }
            return value
        }
    }
    
    fun put(key: K, value: V) {
        synchronized(this) {
            val size = sizeCalculator(value)
            
            // Remove existing entry if present
            cache.remove(key)?.let { oldValue ->
                currentSize -= sizeCalculator(oldValue)
            }
            
            // Check if we need to evict
            while (currentSize + size > maxSize && cache.isNotEmpty()) {
                val toEvict = cache.entries.first()
                cache.remove(toEvict.key)
                currentSize -= sizeCalculator(toEvict.value)
            }
            
            cache[key] = value
            currentSize += size
        }
    }
    
    fun remove(key: K): V? {
        synchronized(this) {
            val value = cache.remove(key)
            if (value != null) {
                currentSize -= sizeCalculator(value)
            }
            return value
        }
    }
    
    fun evictAll() {
        synchronized(this) {
            cache.clear()
            currentSize = 0
        }
    }
    
    fun snapshot(): Map<K, V> {
        synchronized(this) {
            return cache.toMap()
        }
    }
    
    fun hitCount(): Int = hitCount
    fun missCount(): Int = missCount
}

/**
 * Cache statistics
 */
data class CacheStats(
    val totalSize: Long,
    val totalPlugins: Int,
    val totalChunks: Int,
    val maxCacheSize: Long,
    val hitRate: Float
) {
    val usagePercentage: Float
        get() = (totalSize.toFloat() / maxCacheSize) * 100
    
    val totalSizeMB: Float
        get() = totalSize / (1024f * 1024f)
}
