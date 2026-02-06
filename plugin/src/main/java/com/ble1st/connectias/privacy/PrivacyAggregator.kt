package com.ble1st.connectias.privacy

import com.ble1st.connectias.plugin.security.PluginDataLeakageProtector
import com.ble1st.connectias.plugin.security.PluginNetworkTracker
import com.ble1st.connectias.plugin.security.PluginPermissionMonitor
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import com.ble1st.connectias.privacy.export.AuditEventRecord
import com.ble1st.connectias.privacy.export.DataLeakageRecord
import com.ble1st.connectias.privacy.export.ExportTimeWindow
import com.ble1st.connectias.privacy.export.NetworkUsageRecord
import com.ble1st.connectias.privacy.export.PermissionUsageRecord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates privacy-relevant data across all plugins for admin dashboards and GDPR export.
 */
@Singleton
class PrivacyAggregator @Inject constructor(
    private val securityAuditManager: SecurityAuditManager,
    private val permissionMonitor: PluginPermissionMonitor
) {

    data class PrivacySummary(
        val timeWindow: ExportTimeWindow,
        val trackedPluginIds: Set<String>,
        val auditEventCount: Int,
        val permissionEventCount: Int,
        val networkPluginCount: Int,
        val dataLeakageEventCount: Int
    )

    data class PrivacySnapshot(
        val timeWindow: ExportTimeWindow,
        val auditEvents: List<AuditEventRecord>,
        val permissionUsage: List<PermissionUsageRecord>,
        val networkUsage: List<NetworkUsageRecord>,
        val dataLeakageEvents: List<DataLeakageRecord>
    )

    fun getPrivacySnapshot(timeWindow: ExportTimeWindow): PrivacySnapshot {
        val auditEvents = securityAuditManager
            .getEventsInTimeRange(timeWindow.startEpochMillis, timeWindow.endEpochMillis)
            .map { it.toExportRecord() }

        val permissionUsage = permissionMonitor
            .getPermissionUsageInTimeRange(timeWindow.startEpochMillis, timeWindow.endEpochMillis)
            .map {
                PermissionUsageRecord(
                    pluginId = it.pluginId,
                    permission = it.permission,
                    granted = it.granted,
                    timestamp = it.timestamp,
                    context = it.context
                )
            }

        val networkUsage = PluginNetworkTracker
            .getAllNetworkUsage()
            .values
            .asSequence()
            .filter { stats ->
                // Include if network activity overlaps the time window.
                stats.firstActivity <= timeWindow.endEpochMillis && stats.lastActivity >= timeWindow.startEpochMillis
            }
            .map { stats ->
                NetworkUsageRecord(
                    pluginId = stats.pluginId,
                    bytesReceived = stats.bytesReceived.get(),
                    bytesSent = stats.bytesSent.get(),
                    connectionsOpened = stats.connectionsOpened.get(),
                    connectionsFailed = stats.connectionsFailed.get(),
                    domainsAccessed = stats.domainsAccessed.toList(),
                    portsUsed = stats.portsUsed.toList(),
                    firstActivity = stats.firstActivity,
                    lastActivity = stats.lastActivity
                )
            }
            .toList()

        val dataLeakageEvents = PluginDataLeakageProtector
            .getDataAccessInTimeRange(timeWindow.startEpochMillis, timeWindow.endEpochMillis)
            .map { pair ->
                val pluginId = pair.first
                val event = pair.second
                DataLeakageRecord(
                    pluginId = pluginId,
                    timestamp = event.timestamp,
                    dataType = event.dataType,
                    operation = event.operation,
                    suspicious = event.suspicious,
                    dataPattern = event.dataPattern
                )
            }

        return PrivacySnapshot(
            timeWindow = timeWindow,
            auditEvents = auditEvents,
            permissionUsage = permissionUsage,
            networkUsage = networkUsage,
            dataLeakageEvents = dataLeakageEvents
        )
    }

    fun getPrivacySummary(timeWindow: ExportTimeWindow): PrivacySummary {
        val snapshot = getPrivacySnapshot(timeWindow)
        val pluginIds = buildSet {
            snapshot.auditEvents.mapNotNullTo(this) { it.pluginId }
            snapshot.permissionUsage.mapTo(this) { it.pluginId }
            snapshot.networkUsage.mapTo(this) { it.pluginId }
            snapshot.dataLeakageEvents.mapTo(this) { it.pluginId }
            addAll(permissionMonitor.getTrackedPluginIds())
        }

        return PrivacySummary(
            timeWindow = timeWindow,
            trackedPluginIds = pluginIds,
            auditEventCount = snapshot.auditEvents.size,
            permissionEventCount = snapshot.permissionUsage.size,
            networkPluginCount = snapshot.networkUsage.size,
            dataLeakageEventCount = snapshot.dataLeakageEvents.size
        )
    }

    private fun SecurityAuditManager.SecurityAuditEvent.toExportRecord(): AuditEventRecord {
        return AuditEventRecord(
            id = id,
            timestamp = timestamp,
            eventType = eventType.name,
            severity = severity.name,
            source = source,
            pluginId = pluginId,
            message = message,
            details = details
        )
    }
}

