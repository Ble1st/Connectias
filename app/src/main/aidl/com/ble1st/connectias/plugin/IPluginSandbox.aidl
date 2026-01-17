package com.ble1st.connectias.plugin;

import com.ble1st.connectias.plugin.PluginMetadataParcel;
import com.ble1st.connectias.plugin.PluginResultParcel;
import android.os.ParcelFileDescriptor;

/**
 * AIDL interface for plugin sandbox communication
 * 
 * Version 2.0: Added isolated process support
 * - loadPlugin now uses ParcelFileDescriptor instead of String path
 * - Added setHardwareBridge for hardware access delegation
 */
interface IPluginSandbox {
    
    /**
     * Loads a plugin in the sandbox process
     * 
     * Version 2.0: Changed to ParcelFileDescriptor for isolated process support
     * The file descriptor is closed after the plugin is loaded
     * 
     * @param pluginFd ParcelFileDescriptor for plugin APK/JAR file (MODE_READ_ONLY)
     * @param pluginId Plugin identifier for tracking
     * @return PluginResultParcel with success/failure and metadata
     */
    PluginResultParcel loadPluginFromDescriptor(in ParcelFileDescriptor pluginFd, String pluginId);
    
    /**
     * Loads a plugin in the sandbox process (legacy, for backward compatibility)
     * 
     * DEPRECATED: Use loadPluginFromDescriptor for isolated process support
     * This method will fail when isolatedProcess=true
     * 
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
     * Gets the process ID of the sandbox
     * @return Process ID
     */
    int getSandboxPid();
    
    /**
     * Gets current memory usage of sandbox process
     * @return Used memory in bytes
     */
    long getSandboxMemoryUsage();
    
    /**
     * Gets maximum heap size of sandbox process
     * @return Max memory in bytes
     */
    long getSandboxMaxMemory();
    
    /**
     * Gets estimated memory usage for a specific plugin
     * @param pluginId Plugin identifier
     * @return Estimated memory usage in bytes, or -1 if plugin not found
     */
    long getPluginMemoryUsage(String pluginId);
    
    /**
     * Shuts down the sandbox
     */
    void shutdown();
    
    /**
     * Set hardware bridge interface for hardware access delegation
     * Must be called before plugins try to access hardware
     * 
     * @param hardwareBridge IBinder from HardwareBridgeService
     */
    void setHardwareBridge(IBinder hardwareBridge);
    
    /**
     * Set file system bridge interface for file system access
     * Must be called before plugins try to access files
     * 
     * @param fileSystemBridge IBinder from FileSystemBridgeService
     */
    void setFileSystemBridge(IBinder fileSystemBridge);
    
    /**
     * Request permission asynchronously for a plugin
     * @param pluginId Plugin identifier
     * @param permission Permission to request
     * @return True if request was sent successfully, result will be delivered via callback
     */
    boolean requestPermissionAsync(String pluginId, String permission);
    
    /**
     * Request multiple permissions asynchronously for a plugin
     * @param pluginId Plugin identifier
     * @param permissions List of permissions to request
     * @return True if request was sent successfully, result will be delivered via callback
     */
    boolean requestPermissionsAsync(String pluginId, in List<String> permissions);
    
    /**
     * Set permission result callback interface
     * Must be called before requesting permissions asynchronously
     * 
     * @param callback IBinder for permission result callbacks
     */
    void setPermissionCallback(IBinder callback);
}
