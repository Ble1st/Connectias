package com.ble1st.connectias.feature.privacy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * App permission information for a single app.
 */
@Parcelize
data class AppPermissionInfo(
    val packageName: String,
    val appName: String,
    val runtimePermissions: List<PermissionDetail>,
    val installTimePermissions: List<String>,
    val specialPermissions: List<SpecialPermission>,
    val riskLevel: PermissionRiskLevel
) : Parcelable

/**
 * Detailed information about a permission.
 */
@Parcelize
data class PermissionDetail(
    val permission: String,
    val granted: Boolean,
    val isDangerous: Boolean
) : Parcelable

/**
 * Special permission information (e.g., SYSTEM_ALERT_WINDOW).
 */
@Parcelize
data class SpecialPermission(
    val permission: String,
    val granted: Boolean
) : Parcelable

/**
 * Risk level based on granted permissions.
 */
enum class PermissionRiskLevel {
    LOW,        // No dangerous permissions or only safe permissions
    MEDIUM,     // Some dangerous permissions granted
    HIGH        // Multiple high-risk permissions granted
}

