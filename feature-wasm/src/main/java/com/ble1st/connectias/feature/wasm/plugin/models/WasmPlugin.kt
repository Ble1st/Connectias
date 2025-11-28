package com.ble1st.connectias.feature.wasm.plugin.models

import com.ble1st.connectias.feature.wasm.wasm.WasmModule
import com.ble1st.connectias.feature.wasm.wasm.WasmStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents a loaded WASM plugin instance.
 */
data class WasmPlugin(
    /**
     * Plugin metadata.
     */
    val metadata: PluginMetadata,
    
    /**
     * Loaded WASM module.
     */
    val wasmModule: WasmModule,
    
    /**
     * WASM store for execution.
     */
    val store: WasmStore,
    
    /**
     * Current plugin status.
     */
    val status: AtomicReference<PluginStatus> = AtomicReference(PluginStatus.LOADED),
    
    /**
     * Error message if status is ERROR.
     */
    val errorMessage: String? = null,
    
    /**
     * Timestamp when plugin was loaded.
     */
    val loadedAt: Long = System.currentTimeMillis()
) {
    /**
     * Check if plugin is ready to execute.
     */
    fun isReady(): Boolean {
        return status.get() == PluginStatus.READY
    }
    
    /**
     * Check if plugin is running.
     */
    fun isRunning(): Boolean {
        return status.get() == PluginStatus.RUNNING
    }
    
    /**
     * Check if plugin has error.
     */
    fun hasError(): Boolean {
        return status.get() == PluginStatus.ERROR
    }
}

