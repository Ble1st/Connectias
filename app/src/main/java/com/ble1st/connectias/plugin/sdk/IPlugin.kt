package com.ble1st.connectias.plugin.sdk

/**
 * Base interface that all Connectias plugins must implement
 * 
 * IMPORTANT: Method order must match the plugin SDK version to ensure binary compatibility.
 * Plugins are compiled against the SDK version, so the interface signature must match exactly.
 */
interface IPlugin {
    
    /**
     * Get plugin metadata
     * Must be first method to match SDK interface order
     */
    fun getMetadata(): PluginMetadata
    
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
     * Lifecycle: App was moved to background
     * Default implementation does nothing - plugins can override if needed
     */
    fun onPause() {}
    
    /**
     * Lifecycle: App is active again
     * Default implementation does nothing - plugins can override if needed
     */
    fun onResume() {}
}
