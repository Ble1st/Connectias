package com.ble1st.connectias.plugin.native

import java.io.File

/**
 * Interface for loading native libraries (.so files) at runtime
 */
interface INativeLibraryManager {
    
    /**
     * Load a .so file from a specific path
     */
    suspend fun loadLibrary(
        libraryName: String,
        libraryPath: File
    ): Result<Unit>
    
    /**
     * Unload a .so file
     * Note: Android/Java cannot actually unload native libraries,
     * this just removes it from tracking
     */
    suspend fun unloadLibrary(libraryName: String): Result<Unit>
    
    /**
     * Check if a library is loaded
     */
    fun isLoaded(libraryName: String): Boolean
    
    /**
     * Get all loaded libraries
     */
    fun getLoadedLibraries(): List<String>
}
