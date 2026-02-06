package com.ble1st.connectias.plugin;

import com.ble1st.connectias.plugin.PermissionResult;

/**
 * AIDL interface for permission request callbacks
 * Used to deliver async permission results to plugins
 */
interface IPermissionCallback {
    /**
     * Called when a single permission request is completed
     * @param pluginId Plugin identifier
     * @param permission The requested permission
     * @param granted True if permission was granted, false otherwise
     * @param errorMessage Error message if request failed, null otherwise
     */
    void onPermissionResult(String pluginId, String permission, boolean granted, String errorMessage);
    
    /**
     * Called when multiple permissions request is completed
     * @param pluginId Plugin identifier
     * @param results List of permission results
     * @param errorMessage Error message if request failed, null otherwise
     */
    void onPermissionsResult(String pluginId, in List<PermissionResult> results, String errorMessage);
}
