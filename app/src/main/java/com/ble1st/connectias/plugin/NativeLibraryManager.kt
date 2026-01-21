package com.ble1st.connectias.plugin

import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Manages native libraries for plugins
 * 
 * Note: JVM does not support unloading native libraries once loaded.
 * The tracking is for logging and debugging purposes only.
 */
class NativeLibraryManager(
    private val pluginDirectory: File
) {
    
    private val nativeLibDir = File(pluginDirectory, "native")
    
    // Track loaded libraries per plugin for debugging
    private val loadedLibraries = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    
    init {
        nativeLibDir.mkdirs()
    }
    
    /**
     * Extract native libraries from plugin APK/JAR
     */
    fun extractNativeLibraries(pluginFile: File, pluginId: String): Result<List<String>> {
        return try {
            val pluginNativeDir = File(nativeLibDir, pluginId)
            pluginNativeDir.mkdirs()
            
            val extractedLibs = mutableListOf<String>()
            
            ZipFile(pluginFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    
                    // Extract .so files from lib/arm64-v8a/
                    if (entry.name.startsWith("lib/arm64-v8a/") && entry.name.endsWith(".so")) {
                        val libName = File(entry.name).name
                        val targetFile = File(pluginNativeDir, libName)
                        
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        extractedLibs.add(targetFile.absolutePath)
                        Timber.d("Extracted native library: $libName")
                    }
                }
            }
            
            Result.success(extractedLibs)
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract native libraries for plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Load native library from absolute path.
     * 
     * Note: Uses System.load() instead of System.loadLibrary() because:
     * - Plugins provide native libraries at runtime from extracted files
     * - System.loadLibrary() only works with libraries in system library paths
     * - System.load() is necessary for dynamically loaded plugin libraries
     * 
     * Security: The library path is validated to be within the app's private directory
     * and is extracted from verified plugin APKs.
     */
    @Suppress("UnsafeDynamicallyLoadedCode")
    fun loadLibrary(libraryPath: String, pluginId: String? = null): Result<Unit> {
        return try {
            // Validate that library path is within app's private directory
            val libraryFile = File(libraryPath)
            val nativeLibDirCanonical = nativeLibDir.canonicalPath
            val libraryFileCanonical = libraryFile.canonicalPath
            
            if (!libraryFileCanonical.startsWith(nativeLibDirCanonical)) {
                return Result.failure(SecurityException("Library path outside allowed directory: $libraryPath"))
            }
            
            System.load(libraryPath)
            
            // Track loaded library for debugging
            if (pluginId != null) {
                loadedLibraries.getOrPut(pluginId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
                    .add(libraryPath)
            }
            
            Timber.i("Loaded native library: $libraryPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load native library: $libraryPath")
            Result.failure(e)
        }
    }
    
    /**
     * Clean up native libraries for a plugin
     * 
     * Note: JVM does not support unloading native libraries.
     * Once loaded, they remain in memory until the process terminates.
     * This method only deletes the files and clears tracking.
     */
    fun cleanupLibraries(pluginId: String): Result<Unit> {
        return try {
            // Log loaded libraries that cannot be unloaded
            val loaded = loadedLibraries.remove(pluginId)
            if (loaded != null && loaded.isNotEmpty()) {
                Timber.w("Plugin $pluginId had ${loaded.size} loaded native libraries that cannot be unloaded from JVM memory")
                loaded.forEach { lib -> Timber.d("  Still in memory: $lib") }
            }
            
            // Delete library files
            val pluginNativeDir = File(nativeLibDir, pluginId)
            if (pluginNativeDir.exists()) {
                pluginNativeDir.deleteRecursively()
                Timber.i("Cleaned up native library files for plugin: $pluginId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup native libraries for plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    /**
     * Get list of loaded libraries for a plugin (for debugging)
     */
    fun getLoadedLibraries(pluginId: String): Set<String> {
        return loadedLibraries[pluginId]?.toSet() ?: emptySet()
    }
}
