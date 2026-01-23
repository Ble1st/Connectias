// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.security

import com.ble1st.connectias.plugin.PluginPermissionManager
import timber.log.Timber

/**
 * Permission pre-checker for API methods.
 *
 * Checks if a plugin has required permissions BEFORE executing API methods,
 * instead of checking after execution. This provides better security and
 * clearer error messages.
 *
 * Security Model:
 * - Each API method is mapped to required permissions
 * - Pre-check happens before any hardware/filesystem access
 * - Fails fast with clear error messages
 *
 * Usage in bridge wrappers:
 * ```kotlin
 * override fun captureImage(pluginId: String): HardwareResponseParcel {
 *     permissionPreChecker.preCheck(pluginId, "captureImage")
 *     return actualBridge.captureImage(pluginId)
 * }
 * ```
 *
 * @param permissionManager Permission manager for checking permissions
 */
class PermissionPreChecker(
    private val permissionManager: PluginPermissionManager
) {

    companion object {
        /**
         * API method → Required permissions mapping
         *
         * This defines which permissions are required for each API method.
         * If a method is not in this map, no permission check is performed.
         */
        private val API_PERMISSION_MAP = mapOf(
            // Camera APIs
            "captureImage" to listOf("CAMERA"),
            "startCameraPreview" to listOf("CAMERA"),
            "stopCameraPreview" to listOf("CAMERA"),

            // Network APIs
            "httpGet" to listOf("INTERNET"),
            "httpPost" to listOf("INTERNET"),
            "openSocket" to listOf("INTERNET"),

            // Location APIs
            "getLocation" to listOf("ACCESS_FINE_LOCATION"),
            "getLastKnownLocation" to listOf("ACCESS_FINE_LOCATION"),
            "requestLocationUpdates" to listOf("ACCESS_FINE_LOCATION"),

            // Bluetooth APIs
            "getPairedBluetoothDevices" to listOf("BLUETOOTH"),
            "connectBluetoothDevice" to listOf("BLUETOOTH", "BLUETOOTH_CONNECT"),
            "disconnectBluetoothDevice" to listOf("BLUETOOTH", "BLUETOOTH_CONNECT"),
            "scanBluetoothDevices" to listOf("BLUETOOTH", "BLUETOOTH_SCAN"),

            // File System APIs
            "createFile" to listOf("FILE_WRITE"),
            "openFile" to listOf("FILE_READ"),
            "openFileForWrite" to listOf("FILE_WRITE"),
            "deleteFile" to listOf("FILE_WRITE"),
            "listFiles" to listOf("FILE_READ"),
            "getFileSize" to listOf("FILE_READ"),
            "writeFile" to listOf("FILE_WRITE"),
            "readFile" to listOf("FILE_READ"),

            // Printer APIs
            "printDocument" to listOf("PRINTER"),
            "getAvailablePrinters" to listOf("PRINTER"),

            // Sensor APIs (if added in future)
            "getAccelerometer" to listOf("SENSORS"),
            "getGyroscope" to listOf("SENSORS"),

            // Microphone APIs (if added in future)
            "recordAudio" to listOf("RECORD_AUDIO"),

            // Contacts APIs (if added in future)
            "getContacts" to listOf("READ_CONTACTS"),

            // SMS APIs (if added in future)
            "sendSMS" to listOf("SEND_SMS"),
            "readSMS" to listOf("READ_SMS")
        )
    }

    /**
     * Pre-check permissions for an API method
     *
     * @param pluginId Plugin identifier
     * @param apiMethod API method name (e.g., "captureImage")
     * @throws SecurityException if required permissions are not granted
     */
    fun preCheck(pluginId: String, apiMethod: String) {
        val requiredPermissions = API_PERMISSION_MAP[apiMethod]

        // If method not in map, no permission check needed
        if (requiredPermissions == null) {
            Timber.d("[PRE-CHECK] No permission requirement for API: $apiMethod")
            return
        }

        // Check each required permission
        for (permission in requiredPermissions) {
            if (!permissionManager.isPermissionAllowed(pluginId, permission)) {
                val message = "[SECURITY] Plugin '$pluginId' missing permission '$permission' for API '$apiMethod'"
                Timber.e(message)

                // Audit log for security monitoring
                Timber.e("[SECURITY AUDIT] PERMISSION_VIOLATION - Plugin: $pluginId, Permission: $permission, API: $apiMethod")

                throw SecurityException(
                    "Plugin '$pluginId' does not have permission '$permission' required for $apiMethod(). " +
                            "Please declare this permission in plugin-manifest.json"
                )
            }
        }

        Timber.d("[PRE-CHECK] Plugin '$pluginId' passed permission check for API: $apiMethod")
    }

    /**
     * Check if a plugin has permission for an API without throwing exception
     *
     * @param pluginId Plugin identifier
     * @param apiMethod API method name
     * @return true if plugin has all required permissions, false otherwise
     */
    fun hasPermission(pluginId: String, apiMethod: String): Boolean {
        val requiredPermissions = API_PERMISSION_MAP[apiMethod] ?: return true

        return requiredPermissions.all { permission ->
            permissionManager.isPermissionAllowed(pluginId, permission)
        }
    }

    /**
     * Get required permissions for an API method
     *
     * @param apiMethod API method name
     * @return List of required permissions, or empty list if none required
     */
    fun getRequiredPermissions(apiMethod: String): List<String> {
        return API_PERMISSION_MAP[apiMethod] ?: emptyList()
    }

    /**
     * Get all API methods that require a specific permission
     *
     * @param permission Permission name
     * @return List of API methods that require this permission
     */
    fun getAPIsRequiringPermission(permission: String): List<String> {
        return API_PERMISSION_MAP
            .filter { (_, permissions) -> permissions.contains(permission) }
            .keys
            .toList()
    }
}
