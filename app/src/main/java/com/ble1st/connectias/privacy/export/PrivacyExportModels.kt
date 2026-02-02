@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.ble1st.connectias.privacy.export

import kotlinx.serialization.Serializable

/**
 * Export schema for GDPR (DSGVO) data export.
 *
 * This file defines stable DTOs for export and should be kept backwards compatible.
 */
@Serializable
data class PrivacyExportBundle(
    val metadata: PrivacyExportMetadata,
    val auditEvents: List<AuditEventRecord> = emptyList(),
    val permissionUsage: List<PermissionUsageRecord> = emptyList(),
    val networkUsage: List<NetworkUsageRecord> = emptyList(),
    val dataLeakageEvents: List<DataLeakageRecord> = emptyList()
)

@Serializable
data class PrivacyExportMetadata(
    val schemaVersion: Int = 1,
    val createdAtEpochMillis: Long,
    val timeWindow: ExportTimeWindow,
    val appVersionName: String? = null,
    val deviceInfo: ExportDeviceInfo? = null,
    val notes: Map<String, String> = emptyMap()
)

@Serializable
data class ExportTimeWindow(
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

@Serializable
data class ExportDeviceInfo(
    val manufacturer: String? = null,
    val model: String? = null,
    val androidVersion: String? = null,
    val securityPatchLevel: String? = null
)

/**
 * Normalized representation of security audit events for export.
 *
 * We don't export the full in-memory model to avoid tight coupling and to keep the schema stable.
 */
@Serializable
data class AuditEventRecord(
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val severity: String,
    val source: String,
    val pluginId: String? = null,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class PermissionUsageRecord(
    val pluginId: String,
    val permission: String,
    val granted: Boolean,
    val timestamp: Long,
    val context: String? = null
)

@Serializable
data class NetworkUsageRecord(
    val pluginId: String,
    val bytesReceived: Long,
    val bytesSent: Long,
    val connectionsOpened: Long,
    val connectionsFailed: Long,
    val domainsAccessed: List<String> = emptyList(),
    val portsUsed: List<Int> = emptyList(),
    val firstActivity: Long,
    val lastActivity: Long
)

@Serializable
data class DataLeakageRecord(
    val pluginId: String,
    val timestamp: Long,
    val dataType: String,
    val operation: String,
    val suspicious: Boolean,
    val dataPattern: String? = null
)

