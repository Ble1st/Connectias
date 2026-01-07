package com.ble1st.connectias.core.plugin

import android.content.Context
import java.io.File

/**
 * Plugin interface - must match the SDK version
 * This is a copy for the Core module to avoid dependency on plugin SDK
 */
interface IPlugin {
    fun getMetadata(): PluginMetadata
    fun onLoad(context: PluginContext): Boolean
    fun onEnable(): Boolean
    fun onDisable(): Boolean
    fun onUnload(): Boolean
    fun onPause() {}
    fun onResume() {}
}

interface PluginContext {
    fun getApplicationContext(): Context
    fun getPluginDirectory(): File
    fun getNativeLibraryManager(): INativeLibraryManager
    fun registerService(name: String, service: Any)
    fun getService(name: String): Any?
    fun logDebug(message: String)
    fun logError(message: String, throwable: Throwable? = null)
}

interface INativeLibraryManager {
    suspend fun loadLibrary(libraryName: String, libraryPath: File): Result<Unit>
    suspend fun unloadLibrary(libraryName: String): Result<Unit>
    fun isLoaded(libraryName: String): Boolean
    fun getLoadedLibraries(): List<String>
}
