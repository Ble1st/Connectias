package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the status of a network scan operation.
 * Provides structured information about scan state, permissions, and errors.
 */
@Parcelize
sealed class ScanStatus : Parcelable {
    /**
     * Scan is in progress.
     */
    @Parcelize
    object Loading : ScanStatus()
    
    /**
     * Scan completed successfully with data.
     */
    @Parcelize
    data class Success(
        val itemCount: Int
    ) : ScanStatus()
    
    /**
     * Scan completed but no items found (not an error).
     */
    @Parcelize
    object Empty : ScanStatus()
    
    /**
     * Permission is required but not granted.
     * @param permissionType The type of permission needed (e.g., "Location")
     * @param canRequest Whether the permission can be requested via runtime request
     */
    @Parcelize
    data class PermissionRequired(
        val permissionType: String,
        val canRequest: Boolean = true
    ) : ScanStatus()
    
    /**
     * Network-related error occurred.
     * @param message User-friendly error message
     * @param errorType Specific error category
     */
    @Parcelize
    data class NetworkError(
        val message: String,
        val errorType: ErrorType
    ) : ScanStatus()
    
    /**
     * Unknown or unexpected error.
     */
    @Parcelize
    data class UnknownError(
        val message: String
    ) : ScanStatus()
}

/**
 * Represents the permission state for network operations.
 */
@Parcelize
sealed class PermissionState : Parcelable {
    /**
     * All required permissions are granted.
     */
    @Parcelize
    object Granted : PermissionState()
    
    /**
     * Permission is required but not granted.
     * @param missingPermissions List of missing permission names
     * @param canRequest Whether permissions can be requested
     */
    @Parcelize
    data class Required(
        val missingPermissions: List<String>,
        val canRequest: Boolean = true
    ) : PermissionState()
    
    /**
     * Permission was denied and user selected "Don't ask again".
     */
    @Parcelize
    data class PermanentlyDenied(
        val missingPermissions: List<String>
    ) : PermissionState()
}

