package com.ble1st.connectias.plugin.sdk

/**
 * Base interface that all Connectias plugins must implement
 * Must match the app's plugin SDK exactly
 */
interface IPlugin {
    
    /**
     * Called when the plugin is loaded
     * @return true if successful, false otherwise
     */
    fun onLoad(context: PluginContext): Boolean
    
    /**
     * Called when the plugin is enabled
     * @return true if successful, false otherwise
     */
    fun onEnable(): Boolean
    
    /**
     * Called when the plugin is disabled
     * @return true if successful, false otherwise
     */
    fun onDisable(): Boolean
    
    /**
     * Called when the plugin is unloaded
     * @return true if successful, false otherwise
     */
    fun onUnload(): Boolean
    
    /**
     * Get plugin metadata
     */
    fun getMetadata(): PluginMetadata
}
