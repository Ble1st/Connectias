package com.ble1st.connectias.hardware;

import com.ble1st.connectias.hardware.HardwareResponseParcel;
import android.os.ParcelFileDescriptor;

/**
 * AIDL interface for hardware access from isolated sandbox process
 * 
 * All hardware operations must go through this bridge to maintain security
 * in isolated process mode. Each method checks permissions via PluginPermissionManager
 * before granting access.
 * 
 * SECURITY MODEL:
 * - Main Process: Has all app permissions, runs this service
 * - Sandbox Process: Isolated, NO permissions, calls this via IPC
 * - Permission checks happen in Main Process before hardware access
 * 
 * @since 2.0.0 (isolated process support)
 */
interface IHardwareBridge {
    
    // ════════════════════════════════════════════════════════
    // CAMERA BRIDGE
    // ════════════════════════════════════════════════════════
    
    /**
     * Request runtime permission
     * 
     * @param pluginId Plugin requesting access (for permission check)
     * @param permission Permission to request
     * @return HardwareResponseParcel with permission result or error
     */
    HardwareResponseParcel requestPermission(String pluginId, String permission);
    
    /**
     * Capture image from camera
     * 
     * @param pluginId Plugin requesting access (for permission check)
     * @return HardwareResponseParcel with image data as ParcelFileDescriptor or error
     */
    HardwareResponseParcel captureImage(String pluginId);
    
    /**
     * Start camera preview and return SharedMemory for frame access
     * 
     * @param pluginId Plugin requesting access
     * @return HardwareResponseParcel with SharedMemory FD for preview frames
     */
    HardwareResponseParcel startCameraPreview(String pluginId);
    
    /**
     * Stop camera preview
     * 
     * @param pluginId Plugin ID
     */
    void stopCameraPreview(String pluginId);
    
    // ════════════════════════════════════════════════════════
    // NETWORK BRIDGE
    // ════════════════════════════════════════════════════════
    
    /**
     * HTTP GET request
     * 
     * @param pluginId Plugin requesting access
     * @param url Target URL (validated for security)
     * @return HardwareResponseParcel with response data or error
     */
    HardwareResponseParcel httpGet(String pluginId, String url);
    
    /**
     * HTTP POST request with data
     * 
     * @param pluginId Plugin requesting access
     * @param url Target URL (validated for security)
     * @param dataFd ParcelFileDescriptor with POST data (will be closed after use)
     * @return HardwareResponseParcel with response data or error
     */
    HardwareResponseParcel httpPost(String pluginId, String url, in ParcelFileDescriptor dataFd);
    
    /**
     * Open TCP socket to remote host
     * 
     * @param pluginId Plugin requesting access
     * @param host Remote host address
     * @param port Remote port
     * @return HardwareResponseParcel with socket as ParcelFileDescriptor or error
     */
    HardwareResponseParcel openSocket(String pluginId, String host, int port);
    
    // ════════════════════════════════════════════════════════
    // PRINTER BRIDGE
    // ════════════════════════════════════════════════════════
    
    /**
     * Get list of available printers
     * 
     * @param pluginId Plugin requesting access
     * @return List of printer IDs
     */
    List<String> getAvailablePrinters(String pluginId);
    
    /**
     * Print document to printer
     * 
     * @param pluginId Plugin requesting access
     * @param printerId Printer ID from getAvailablePrinters
     * @param documentFd ParcelFileDescriptor with document data (PDF/image)
     * @return HardwareResponseParcel with print job status or error
     */
    HardwareResponseParcel printDocument(String pluginId, String printerId, in ParcelFileDescriptor documentFd);
    
    // ════════════════════════════════════════════════════════
    // BLUETOOTH BRIDGE
    // ════════════════════════════════════════════════════════
    
    /**
     * Get paired Bluetooth devices
     * 
     * @param pluginId Plugin requesting access
     * @return List of device addresses (MAC addresses)
     */
    List<String> getPairedBluetoothDevices(String pluginId);
    
    /**
     * Connect to Bluetooth device and get socket
     * 
     * @param pluginId Plugin requesting access
     * @param deviceAddress Device MAC address
     * @return HardwareResponseParcel with Bluetooth socket as ParcelFileDescriptor or error
     */
    HardwareResponseParcel connectBluetoothDevice(String pluginId, String deviceAddress);
    
    /**
     * Disconnect from Bluetooth device
     * 
     * @param pluginId Plugin ID
     * @param deviceAddress Device MAC address
     */
    void disconnectBluetoothDevice(String pluginId, String deviceAddress);
    
    // ════════════════════════════════════════════════════════
    // FILE BRIDGE (for plugin loading)
    // ════════════════════════════════════════════════════════
    
    /**
     * Get plugin file as ParcelFileDescriptor for loading in isolated process
     * This is used by PluginSandboxService to load plugin APK/JAR files
     * 
     * @param pluginPath Absolute path to plugin file (validated for security)
     * @return ParcelFileDescriptor for reading plugin file or null if not found
     */
    ParcelFileDescriptor getPluginFile(String pluginPath);
    
    /**
     * Write data to temp file and return ParcelFileDescriptor
     * Used for passing large data from sandbox to main process
     * 
     * @param pluginId Plugin ID (for cleanup)
     * @param dataFd ParcelFileDescriptor with data to write
     * @return Path to temp file or null on error
     */
    String writeTempFile(String pluginId, in ParcelFileDescriptor dataFd);
    
    // ════════════════════════════════════════════════════════
    // UTILITY
    // ════════════════════════════════════════════════════════
    
    /**
     * Ping to check if bridge is alive
     * 
     * @return true if bridge is responsive
     */
    boolean ping();
}
