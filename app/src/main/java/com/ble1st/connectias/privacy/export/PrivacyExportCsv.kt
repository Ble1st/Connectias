package com.ble1st.connectias.privacy.export

/**
 * CSV mappers for GDPR export.
 *
 * Comments and documentation are in English by project convention.
 */
object PrivacyExportCsv {

    fun auditEventsCsv(rows: List<AuditEventRecord>): String =
        buildCsv(
            header = listOf(
                "id",
                "timestamp",
                "eventType",
                "severity",
                "source",
                "pluginId",
                "message",
                "detailsJson"
            ),
            rows = rows.map { r ->
                listOf(
                    r.id,
                    r.timestamp.toString(),
                    r.eventType,
                    r.severity,
                    r.source,
                    r.pluginId ?: "",
                    r.message,
                    r.details.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                        "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
                    }
                )
            }
        )

    fun permissionUsageCsv(rows: List<PermissionUsageRecord>): String =
        buildCsv(
            header = listOf("pluginId", "permission", "granted", "timestamp", "context"),
            rows = rows.map { r ->
                listOf(
                    r.pluginId,
                    r.permission,
                    r.granted.toString(),
                    r.timestamp.toString(),
                    r.context ?: ""
                )
            }
        )

    fun networkUsageCsv(rows: List<NetworkUsageRecord>): String =
        buildCsv(
            header = listOf(
                "pluginId",
                "bytesReceived",
                "bytesSent",
                "connectionsOpened",
                "connectionsFailed",
                "domainsAccessed",
                "portsUsed",
                "firstActivity",
                "lastActivity"
            ),
            rows = rows.map { r ->
                listOf(
                    r.pluginId,
                    r.bytesReceived.toString(),
                    r.bytesSent.toString(),
                    r.connectionsOpened.toString(),
                    r.connectionsFailed.toString(),
                    r.domainsAccessed.joinToString(separator = ";"),
                    r.portsUsed.joinToString(separator = ";"),
                    r.firstActivity.toString(),
                    r.lastActivity.toString()
                )
            }
        )

    fun dataLeakageCsv(rows: List<DataLeakageRecord>): String =
        buildCsv(
            header = listOf("pluginId", "timestamp", "dataType", "operation", "suspicious", "dataPattern"),
            rows = rows.map { r ->
                listOf(
                    r.pluginId,
                    r.timestamp.toString(),
                    r.dataType,
                    r.operation,
                    r.suspicious.toString(),
                    r.dataPattern ?: ""
                )
            }
        )

    private fun buildCsv(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.appendLine(header.joinToString(",") { escapeCsv(it) })
        for (row in rows) {
            sb.appendLine(row.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return buildString {
            append('"')
            for (c in value) {
                if (c == '"') append("\"\"") else append(c)
            }
            append('"')
        }
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}

