package com.ble1st.connectias.feature.privacy.timeline.models

import kotlinx.serialization.Serializable

/**
 * Represents a permission usage event.
 */
@Serializable
data class PermissionUsageEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val permission: String,
    val permissionGroup: String?,
    val timestamp: Long,
    val duration: Long = 0, // Duration in milliseconds
    val accessType: AccessType,
    val isBackground: Boolean = false,
    val isSuspicious: Boolean = false,
    val suspiciousReason: String? = null
)

/**
 * Type of permission access.
 */
enum class AccessType {
    FOREGROUND,
    BACKGROUND,
    DENIED,
    GRANTED
}

/**
 * Filter for permission timeline.
 */
data class TimelineFilter(
    val startTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000, // Last 24 hours
    val endTime: Long = System.currentTimeMillis(),
    val permissions: Set<String> = emptySet(), // Empty = all permissions
    val packages: Set<String> = emptySet(), // Empty = all packages
    val showBackgroundOnly: Boolean = false,
    val showSuspiciousOnly: Boolean = false
)

/**
 * Grouped events for timeline display.
 */
data class TimelineGroup(
    val date: Long, // Start of day timestamp
    val events: List<PermissionUsageEvent>
)

/**
 * Statistics for permission usage.
 */
data class UsageStatistics(
    val packageName: String,
    val appName: String,
    val totalAccesses: Int,
    val backgroundAccesses: Int,
    val foregroundAccesses: Int,
    val deniedAccesses: Int,
    val mostUsedPermissions: List<PermissionCount>,
    val averageAccessesPerDay: Float,
    val lastAccess: Long?
)

/**
 * Permission access count.
 */
data class PermissionCount(
    val permission: String,
    val count: Int,
    val lastAccess: Long
)

/**
 * Anomaly detected in permission usage.
 */
@Serializable
data class AnomalyReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val permission: String,
    val anomalyType: AnomalyType,
    val description: String,
    val severity: AnomalySeverity,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedEvents: List<String> = emptyList()
)

/**
 * Type of anomaly.
 */
enum class AnomalyType {
    EXCESSIVE_ACCESS,       // Too many accesses in short time
    UNUSUAL_TIME,          // Access at unusual time (e.g., 3 AM)
    BACKGROUND_ACCESS,     // Frequent background access
    NEW_PERMISSION,        // App started using new permission
    PERMISSION_ABUSE,      // Accessing permission unnecessarily
    SENSITIVE_DATA_ACCESS  // Accessing sensitive data (contacts, location, etc.)
}

/**
 * Severity of anomaly.
 */
enum class AnomalySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Common dangerous permissions to track.
 */
object DangerousPermissions {
    val LOCATION = listOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    )
    
    val CAMERA = listOf(
        "android.permission.CAMERA"
    )
    
    val MICROPHONE = listOf(
        "android.permission.RECORD_AUDIO"
    )
    
    val CONTACTS = listOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS"
    )
    
    val STORAGE = listOf(
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO"
    )
    
    val PHONE = listOf(
        "android.permission.READ_PHONE_STATE",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG"
    )
    
    val SMS = listOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS"
    )
    
    val CALENDAR = listOf(
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR"
    )
    
    val ALL = LOCATION + CAMERA + MICROPHONE + CONTACTS + STORAGE + PHONE + SMS + CALENDAR
}
