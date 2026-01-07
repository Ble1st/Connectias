package com.ble1st.connectias.plugin

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Manages native libraries for plugins
 */
class NativeLibraryManager(
    private val context: Context,
    private val pluginDirectory: File
) {
    
    private val nativeLibDir = File(pluginDirectory, "native")
    
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
     * Load native library
     */
    fun loadLibrary(libraryPath: String): Result<Unit> {
        return try {
            System.load(libraryPath)
            Timber.i("Loaded native library: $libraryPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load native library: $libraryPath")
            Result.failure(e)
        }
    }
    
    /**
     * Clean up native libraries for a plugin
     */
    fun cleanupLibraries(pluginId: String): Result<Unit> {
        return try {
            val pluginNativeDir = File(nativeLibDir, pluginId)
            if (pluginNativeDir.exists()) {
                pluginNativeDir.deleteRecursively()
                Timber.i("Cleaned up native libraries for plugin: $pluginId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup native libraries for plugin: $pluginId")
            Result.failure(e)
        }
    }
}
