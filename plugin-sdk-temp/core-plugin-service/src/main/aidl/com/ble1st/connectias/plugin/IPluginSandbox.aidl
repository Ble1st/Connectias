package com.ble1st.connectias.plugin;

import com.ble1st.connectias.plugin.PluginMetadataParcel;
import com.ble1st.connectias.plugin.PluginResultParcel;

/**
 * AIDL interface for plugin sandbox communication
 */
interface IPluginSandbox {
    
    /**
     * Loads a plugin in the sandbox process
     * @param pluginPath Absolute path to plugin APK/JAR
     * @return PluginResultParcel with success/failure and metadata
     */
    PluginResultParcel loadPlugin(String pluginPath);
    
    /**
     * Enables a plugin
     * @param pluginId Plugin identifier
     * @return PluginResultParcel with success/failure
     */
    PluginResultParcel enablePlugin(String pluginId);
    
    /**
     * Disables a plugin
     * @param pluginId Plugin identifier
     * @return PluginResultParcel with success/failure
     */
    PluginResultParcel disablePlugin(String pluginId);
    
    /**
     * Unloads a plugin
     * @param pluginId Plugin identifier
     * @return PluginResultParcel with success/failure
     */
    PluginResultParcel unloadPlugin(String pluginId);
    
    /**
     * Gets list of loaded plugins
     * @return List of plugin IDs
     */
    List<String> getLoadedPlugins();
    
    /**
     * Gets plugin metadata
     * @param pluginId Plugin identifier
     * @return PluginMetadataParcel or null if not found
     */
    PluginMetadataParcel getPluginMetadata(String pluginId);
    
    /**
     * Checks if sandbox is alive
     * @return true if alive
     */
    boolean ping();
    
    /**
     * Shuts down the sandbox
     */
    void shutdown();
}
