package com.ble1st.connectias.feature.security.privacy.timeline

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.ble1st.connectias.feature.security.privacy.timeline.models.AccessType
import com.ble1st.connectias.feature.security.privacy.timeline.models.AnomalyReport
import com.ble1st.connectias.feature.security.privacy.timeline.models.AnomalySeverity
import com.ble1st.connectias.feature.security.privacy.timeline.models.AnomalyType
import com.ble1st.connectias.feature.security.privacy.timeline.models.DangerousPermissions
import com.ble1st.connectias.feature.security.privacy.timeline.models.PermissionCount
import com.ble1st.connectias.feature.security.privacy.timeline.models.PermissionUsageEvent
import com.ble1st.connectias.feature.security.privacy.timeline.models.TimelineFilter
import com.ble1st.connectias.feature.security.privacy.timeline.models.TimelineGroup
import com.ble1st.connectias.feature.security.privacy.timeline.models.UsageStatistics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for permission usage timeline functionality.
 *
 * Uses AppOpsManager to track permission usage across apps.
 */
@Singleton
class PermissionTimelineProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val packageManager = context.packageManager

    // Mapping of AppOps to permission names
    private val opsToPermission = mapOf(
        AppOpsManager.OPSTR_FINE_LOCATION to "android.permission.ACCESS_FINE_LOCATION",
        AppOpsManager.OPSTR_COARSE_LOCATION to "android.permission.ACCESS_COARSE_LOCATION",
        AppOpsManager.OPSTR_CAMERA to "android.permission.CAMERA",
        AppOpsManager.OPSTR_RECORD_AUDIO to "android.permission.RECORD_AUDIO",
        AppOpsManager.OPSTR_READ_CONTACTS to "android.permission.READ_CONTACTS",
        AppOpsManager.OPSTR_WRITE_CONTACTS to "android.permission.WRITE_CONTACTS",
        AppOpsManager.OPSTR_READ_CALL_LOG to "android.permission.READ_CALL_LOG",
        AppOpsManager.OPSTR_WRITE_CALL_LOG to "android.permission.WRITE_CALL_LOG",
        AppOpsManager.OPSTR_READ_CALENDAR to "android.permission.READ_CALENDAR",
        AppOpsManager.OPSTR_WRITE_CALENDAR to "android.permission.WRITE_CALENDAR",
        AppOpsManager.OPSTR_READ_SMS to "android.permission.READ_SMS",
        AppOpsManager.OPSTR_SEND_SMS to "android.permission.SEND_SMS",
        AppOpsManager.OPSTR_READ_EXTERNAL_STORAGE to "android.permission.READ_EXTERNAL_STORAGE",
        AppOpsManager.OPSTR_WRITE_EXTERNAL_STORAGE to "android.permission.WRITE_EXTERNAL_STORAGE"
    )

    /**
     * Gets permission usage events within a time range.
     */
    fun getPermissionUsage(
        filter: TimelineFilter = TimelineFilter()
    ): Flow<List<PermissionUsageEvent>> = flow {
        try {
            val events = mutableListOf<PermissionUsageEvent>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use AppOpsManager for Android 10+
                val usageEvents = getAppOpsUsage(filter)
                events.addAll(usageEvents)
            }

            // Apply filters
            val filtered = events.filter { event ->
                var include = true
                
                if (filter.permissions.isNotEmpty()) {
                    include = include && event.permission in filter.permissions
                }
                
                if (filter.packages.isNotEmpty()) {
                    include = include && event.packageName in filter.packages
                }
                
                if (filter.showBackgroundOnly) {
                    include = include && event.isBackground
                }
                
                if (filter.showSuspiciousOnly) {
                    include = include && event.isSuspicious
                }
                
                include
            }

            // Sort by timestamp descending
            val sorted = filtered.sortedByDescending { it.timestamp }
            
            emit(sorted)
        } catch (e: Exception) {
            Timber.e(e, "Error getting permission usage")
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gets usage events from AppOpsManager.
     */
    private suspend fun getAppOpsUsage(
        filter: TimelineFilter
    ): List<PermissionUsageEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<PermissionUsageEvent>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                
                for (appInfo in packages) {
                    try {
                        val packageName = appInfo.packageName
                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        
                        for ((op, permission) in opsToPermission) {
                            try {
                                // Check if the app has used this operation
                                val mode = appOpsManager.unsafeCheckOpNoThrow(
                                    op,
                                    appInfo.uid,
                                    packageName
                                )
                                
                                if (mode == AppOpsManager.MODE_ALLOWED) {
                                    // Note: In a real implementation, we would need to use
                                    // AppOpsManager.OnOpActiveChangedListener to track real-time usage
                                    // For now, we create a simplified representation
                                    
                                    events.add(
                                        PermissionUsageEvent(
                                            packageName = packageName,
                                            appName = appName,
                                            permission = permission,
                                            permissionGroup = getPermissionGroup(permission),
                                            timestamp = System.currentTimeMillis(),
                                            accessType = AccessType.GRANTED,
                                            isBackground = false,
                                            isSuspicious = checkIfSuspicious(packageName, permission)
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                // Skip this operation for this package
                            }
                        }
                    } catch (e: Exception) {
                        // Skip this package
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting AppOps usage")
            }
        }
        
        events
    }

    /**
     * Groups events by date for timeline display.
     */
    suspend fun groupByDate(
        events: List<PermissionUsageEvent>
    ): List<TimelineGroup> = withContext(Dispatchers.Default) {
        events.groupBy { event ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = event.timestamp
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.map { (date, events) ->
            TimelineGroup(date = date, events = events)
        }.sortedByDescending { it.date }
    }

    /**
     * Gets usage statistics for a package.
     */
    suspend fun getUsageStatistics(
        packageName: String,
        timeRange: TimelineFilter = TimelineFilter()
    ): UsageStatistics = withContext(Dispatchers.IO) {
        val filter = timeRange.copy(packages = setOf(packageName))
        val events = mutableListOf<PermissionUsageEvent>()
        
        getPermissionUsage(filter).collect { events.addAll(it) }
        
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
        
        val backgroundEvents = events.filter { it.isBackground }
        val foregroundEvents = events.filter { !it.isBackground && it.accessType == AccessType.FOREGROUND }
        val deniedEvents = events.filter { it.accessType == AccessType.DENIED }
        
        val permissionCounts = events.groupBy { it.permission }
            .map { (permission, evts) ->
                PermissionCount(
                    permission = permission,
                    count = evts.size,
                    lastAccess = evts.maxOfOrNull { it.timestamp } ?: 0
                )
            }
            .sortedByDescending { it.count }
        
        val daysDiff = (timeRange.endTime - timeRange.startTime) / (24 * 60 * 60 * 1000).toFloat()
        val avgPerDay = if (daysDiff > 0) events.size / daysDiff else 0f
        
        UsageStatistics(
            packageName = packageName,
            appName = appName,
            totalAccesses = events.size,
            backgroundAccesses = backgroundEvents.size,
            foregroundAccesses = foregroundEvents.size,
            deniedAccesses = deniedEvents.size,
            mostUsedPermissions = permissionCounts.take(5),
            averageAccessesPerDay = avgPerDay,
            lastAccess = events.maxOfOrNull { it.timestamp }
        )
    }

    /**
     * Detects anomalies in permission usage.
     */
    suspend fun detectAnomalies(
        timeRange: TimelineFilter = TimelineFilter()
    ): List<AnomalyReport> = withContext(Dispatchers.Default) {
        val anomalies = mutableListOf<AnomalyReport>()
        val events = mutableListOf<PermissionUsageEvent>()
        
        getPermissionUsage(timeRange).collect { events.addAll(it) }
        
        // Group by package
        val byPackage = events.groupBy { it.packageName }
        
        for ((packageName, packageEvents) in byPackage) {
            val appName = packageEvents.firstOrNull()?.appName ?: packageName
            
            // Check for excessive access (more than 100 in an hour)
            val hourlyGroups = packageEvents.groupBy { it.timestamp / (60 * 60 * 1000) }
            for ((_, hourEvents) in hourlyGroups) {
                if (hourEvents.size > 100) {
                    anomalies.add(
                        AnomalyReport(
                            packageName = packageName,
                            appName = appName,
                            permission = hourEvents.first().permission,
                            anomalyType = AnomalyType.EXCESSIVE_ACCESS,
                            description = "App accessed permission ${hourEvents.size} times in one hour",
                            severity = AnomalySeverity.HIGH
                        )
                    )
                }
            }
            
            // Check for unusual time access (between midnight and 5 AM)
            val nightEvents = packageEvents.filter { event ->
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = event.timestamp
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                hour in 0..4
            }
            
            if (nightEvents.size > 10) {
                anomalies.add(
                    AnomalyReport(
                        packageName = packageName,
                        appName = appName,
                        permission = nightEvents.first().permission,
                        anomalyType = AnomalyType.UNUSUAL_TIME,
                        description = "App accessed permissions ${nightEvents.size} times during night hours",
                        severity = AnomalySeverity.MEDIUM
                    )
                )
            }
            
            // Check for background access to sensitive permissions
            val backgroundSensitive = packageEvents.filter { event ->
                event.isBackground && event.permission in DangerousPermissions.LOCATION + 
                    DangerousPermissions.CAMERA + DangerousPermissions.MICROPHONE
            }
            
            if (backgroundSensitive.isNotEmpty()) {
                anomalies.add(
                    AnomalyReport(
                        packageName = packageName,
                        appName = appName,
                        permission = backgroundSensitive.first().permission,
                        anomalyType = AnomalyType.BACKGROUND_ACCESS,
                        description = "App accessed sensitive permission in background",
                        severity = AnomalySeverity.HIGH
                    )
                )
            }
        }
        
        anomalies.sortedByDescending { it.severity.ordinal }
    }

    /**
     * Gets the permission group for a permission.
     */
    private fun getPermissionGroup(permission: String): String? {
        return when {
            permission in DangerousPermissions.LOCATION -> "Location"
            permission in DangerousPermissions.CAMERA -> "Camera"
            permission in DangerousPermissions.MICROPHONE -> "Microphone"
            permission in DangerousPermissions.CONTACTS -> "Contacts"
            permission in DangerousPermissions.STORAGE -> "Storage"
            permission in DangerousPermissions.PHONE -> "Phone"
            permission in DangerousPermissions.SMS -> "SMS"
            permission in DangerousPermissions.CALENDAR -> "Calendar"
            else -> null
        }
    }

    /**
     * Checks if a permission access is suspicious.
     */
    private fun checkIfSuspicious(packageName: String, permission: String): Boolean {
        // Simple heuristic: system apps are generally not suspicious
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            !isSystemApp && permission in DangerousPermissions.ALL
        } catch (e: Exception) {
            false
        }
    }
}
