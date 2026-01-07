package com.ble1st.connectias.plugin

/**
 * Base interface for all Connectias plugins.
 * Plugins MUST implement this interface.
 */
interface IPlugin {
    
    /**
     * Plugin metadata
     */
    fun getMetadata(): PluginMetadata
    
    /**
     * Called when plugin is loaded
     */
    fun onLoad(context: PluginContext): Boolean
    
    /**
     * Called when plugin is enabled
     */
    fun onEnable(): Boolean
    
    /**
     * Called when plugin is disabled
     */
    fun onDisable(): Boolean
    
    /**
     * Called before plugin is unloaded
     */
    fun onUnload(): Boolean
    
    /**
     * Lifecycle: App was moved to background
     */
    fun onPause() {}
    
    /**
     * Lifecycle: App is active again
     */
    fun onResume() {}
}
