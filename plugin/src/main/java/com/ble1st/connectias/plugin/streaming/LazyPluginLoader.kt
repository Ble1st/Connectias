package com.ble1st.connectias.plugin.streaming

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazy plugin loader for on-demand resource loading
 */
@Singleton
class LazyPluginLoader @Inject constructor(
    private val context: Context
) {
    
    private val loadedMetadata = ConcurrentHashMap<String, com.ble1st.connectias.plugin.sdk.PluginMetadata>()
    private val resourceCache = ConcurrentHashMap<String, ByteArray>()
    private val nativeLibraries = ConcurrentHashMap<String, Boolean>()
    
    /**
     * Load plugin metadata without loading the full plugin
     */
    suspend fun loadPluginMetadata(
        pluginStream: PluginStream
    ): com.ble1st.connectias.plugin.sdk.PluginMetadata = withContext(Dispatchers.IO) {
        val pluginId = pluginStream.pluginId
        
        // Check cache first
        loadedMetadata[pluginId]?.let { return@withContext it }
        
        try {
            // Reassemble just enough to read metadata
            val tempFile = File(context.cacheDir, "${pluginId}_metadata.tmp")
            
            // Write chunks to temp file
            tempFile.outputStream().use { output ->
                pluginStream.chunks.sortedBy { it.index }.forEach { chunk ->
                    output.write(chunk.data)
                }
            }
            
            // Extract metadata from zip
            val metadata = extractMetadataFromApk(tempFile)
            
            // Cache it
            loadedMetadata[pluginId] = metadata
            
            // Clean up
            tempFile.delete()
            
            Timber.d("Loaded metadata for plugin: $pluginId")
            metadata
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin metadata: $pluginId")
            throw e
        }
    }
    
    /**
     * Load a specific resource on demand
     */
    suspend fun loadResource(
        pluginId: String,
        resourcePath: String
    ): InputStream? = withContext(Dispatchers.IO) {
        val cacheKey = "$pluginId:$resourcePath"
        
        // Check memory cache
        resourceCache[cacheKey]?.let { cached ->
            return@withContext cached.inputStream()
        }
        
        try {
            // Find plugin file
            val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
            if (!pluginFile.exists()) {
                return@withContext null
            }
            
            // Extract resource from APK
            ZipFile(pluginFile).use { zip ->
                val entry = zip.getEntry(resourcePath) ?: zip.getEntry("assets/$resourcePath")
                if (entry != null) {
                    val data = zip.getInputStream(entry).readBytes()
                    
                    // Cache if small enough
                    if (data.size < 1024 * 1024) { // 1MB limit
                        resourceCache[cacheKey] = data
                    }
                    
                    return@withContext data.inputStream()
                }
            }
            
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load resource: $pluginId:$resourcePath")
            null
        }
    }
    
    /**
     * Load native library progressively with progress callback
     */
    suspend fun loadNativeLibrary(
        pluginId: String,
        libName: String,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val libKey = "$pluginId:$libName"
            
            // Check if already loaded
            if (nativeLibraries[libKey] == true) {
                return@withContext Result.success(Unit)
            }
            
            // Find plugin file
            val pluginFile = File(context.filesDir, "plugins/$pluginId.apk")
            if (!pluginFile.exists()) {
                return@withContext Result.failure(Exception("Plugin file not found"))
            }
            
            // Extract native library
            val libPath = "lib/$libName.so"
            ZipFile(pluginFile).use { zip ->
                val libEntry = zip.getEntry(libPath) ?: return@withContext Result.failure(
                    Exception("Native library not found: $libName")
                )
                
                val libSize = libEntry.size
                val libData = ByteArray(libSize.toInt())
                
                zip.getInputStream(libEntry).use { input ->
                    var totalRead = 0
                    val buffer = ByteArray(8192)
                    
                    while (totalRead < libSize) {
                        val read = input.read(buffer, 0, minOf(buffer.size, (libSize - totalRead).toInt()))
                        if (read == -1) break
                        
                        System.arraycopy(buffer, 0, libData, totalRead, read)
                        totalRead += read
                        
                        val progress = totalRead.toFloat() / libSize
                        onProgress(progress)
                    }
                }
                
                // Write to app's native library directory
                val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
                val targetFile = File(nativeLibDir, "lib$libName.so")
                
                targetFile.writeBytes(libData)
                targetFile.setExecutable(true, false)
                
                // Mark as loaded
                nativeLibraries[libKey] = true
                
                Timber.d("Loaded native library: $libName for plugin: $pluginId")
                Result.success(Unit)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load native library: $libName for plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Create a lazy class loader that only loads classes on demand
     */
    fun createLazyClassLoader(
        pluginFile: File,
        parentClassLoader: ClassLoader
    ): LazyClassLoader {
        val optimizedDexDir = File(context.cacheDir, "lazy_dex/${pluginFile.nameWithoutExtension}")
        optimizedDexDir.mkdirs()
        
        return LazyClassLoader(
            pluginFile.absolutePath,
            optimizedDexDir.absolutePath,
            null,
            parentClassLoader
        )
    }
    
    /**
     * Clear all caches
     */
    fun clearCache() {
        loadedMetadata.clear()
        resourceCache.clear()
        nativeLibraries.clear()
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): LazyLoaderStats {
        return LazyLoaderStats(
            cachedMetadata = loadedMetadata.size,
            cachedResources = resourceCache.size,
            loadedLibraries = nativeLibraries.size,
            memoryUsage = resourceCache.values.sumOf { it.size.toLong() }
        )
    }
    
    private fun extractMetadataFromApk(apkFile: File): com.ble1st.connectias.plugin.sdk.PluginMetadata {
        return ZipFile(apkFile).use { zip ->
            val manifestEntry = zip.getEntry("plugin-manifest.json") 
                ?: zip.getEntry("assets/plugin-manifest.json")
                ?: throw IllegalArgumentException("Plugin manifest not found")
                
            val manifestJson = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(manifestJson)
            
            com.ble1st.connectias.plugin.sdk.PluginMetadata(
                pluginId = json.getString("pluginId"),
                pluginName = json.getString("pluginName"),
                version = json.getString("version"),
                author = json.getString("author"),
                minApiLevel = json.getInt("minApiLevel"),
                maxApiLevel = json.getInt("maxApiLevel"),
                minAppVersion = json.getString("minAppVersion"),
                nativeLibraries = json.optJSONArray("nativeLibraries")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                fragmentClassName = json.optString("fragmentClassName"),
                description = json.getString("description"),
                permissions = json.optJSONArray("permissions")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                category = com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                    json.getString("category")
                ),
                dependencies = json.optJSONArray("dependencies")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        }
    }
}

/**
 * Lazy class loader that only loads classes when requested
 */
class LazyClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    parent: ClassLoader
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {
    
    private val loadedClasses = ConcurrentHashMap<String, Class<*>>()
    private val loadTimes = ConcurrentHashMap<String, Long>()
    
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Check if already loaded
        loadedClasses[name]?.let { return it }
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Load class on demand
            val clazz = super.loadClass(name, resolve)
            loadedClasses[name] = clazz
            
            val loadTime = System.currentTimeMillis() - startTime
            loadTimes[name] = loadTime
            
            Timber.v("Lazy loaded class: $name (${loadTime}ms)")
            return clazz
        } catch (e: ClassNotFoundException) {
            Timber.w("Class not found: $name")
            throw e
        }
    }
    
    /**
     * Get statistics about loaded classes
     */
    fun getLoadStats(): ClassLoaderStats {
        return ClassLoaderStats(
            totalClassesLoaded = loadedClasses.size,
            averageLoadTime = if (loadTimes.isNotEmpty()) {
                loadTimes.values.average()
            } else 0.0,
            slowestClass = loadTimes.maxByOrNull { it.value }?.key
        )
    }
    
    /**
     * Unload unused classes (hint to GC)
     */
    fun unloadUnusedClasses() {
        // Clear references to help GC
        loadedClasses.clear()
        loadTimes.clear()
        System.gc()
    }
}

/**
 * Statistics for lazy loader
 */
data class LazyLoaderStats(
    val cachedMetadata: Int,
    val cachedResources: Int,
    val loadedLibraries: Int,
    val memoryUsage: Long
) {
    val memoryUsageMB: Float
        get() = memoryUsage / (1024f * 1024f)
}

/**
 * Statistics for class loader
 */
data class ClassLoaderStats(
    val totalClassesLoaded: Int,
    val averageLoadTime: Double,
    val slowestClass: String?
)

/**
 * Plugin metadata for lazy loading
 */
private data class PluginMetadata(
    val metadata: com.ble1st.connectias.plugin.sdk.PluginMetadata,
    val resources: Map<String, Long> = emptyMap(),
    val nativeLibs: List<String> = emptyList()
)
