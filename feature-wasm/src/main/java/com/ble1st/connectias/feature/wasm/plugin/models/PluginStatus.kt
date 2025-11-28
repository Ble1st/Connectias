package com.ble1st.connectias.feature.wasm.plugin.models

/**
 * Status of a WASM plugin.
 */
enum class PluginStatus {
    /**
     * Plugin is loaded but not initialized.
     */
    LOADED,
    
    /**
     * Plugin is initialized and ready to execute.
     */
    READY,
    
    /**
     * Plugin is currently executing.
     */
    RUNNING,
    
    /**
     * Plugin encountered an error.
     */
    ERROR,
    
    /**
     * Plugin has been unloaded.
     */
    UNLOADED
}

