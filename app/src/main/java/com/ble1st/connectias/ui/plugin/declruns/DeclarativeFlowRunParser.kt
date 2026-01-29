package com.ble1st.connectias.ui.plugin.declruns

import com.ble1st.connectias.core.model.LogEntry

/**
 * Parses DB log entries into declarative run events.
 *
 * Sources:
 * - Flow engine emits messages with "[DECL_FLOW]" / "[DECL_AUDIT]" prefixes.
 * - PluginLogBridgeImpl stores pluginId in the DB tag: "PLUGIN/<pluginId>:<tag>"
 */
object DeclarativeFlowRunParser {

    private val pluginTagRegex = Regex("""^PLUGIN/([a-zA-Z0-9_.-]+):""")

    // Example:
    // [DECL_FLOW][demo.counter] flowId=main trigger=OnClick ok=true steps=3 durationMs=2 error=null
    private val flowRegex = Regex(
        """\[(DECL_FLOW)]\[(?<pid>[a-zA-Z0-9_.-]+)]\s+flowId=(?<flow>\S+)\s+trigger=(?<trg>\S+)\s+ok=(?<ok>true|false)\s+steps=(?<steps>\d+)\s+durationMs=(?<dur>\d+)\s+error=(?<err>.*)$"""
    )

    // Example:
    // [DECL_AUDIT][demo.counter] rate_limited flowId=main trigger=OnClick
    private val auditRegex = Regex(
        """\[(DECL_AUDIT)]\[(?<pid>[a-zA-Z0-9_.-]+)]\s+(?<type>[a-zA-Z0-9_.-]+)(?<rest>.*)$"""
    )

    fun parse(entry: LogEntry): DeclarativeRunEvent? {
        val pluginIdFromTag = pluginTagRegex.find(entry.tag)?.groupValues?.getOrNull(1)

        val msg = entry.message
        val flowMatch = flowRegex.find(msg)
        if (flowMatch != null) {
            val pid = flowMatch.groups["pid"]?.value ?: pluginIdFromTag ?: return null
            return DeclarativeRunEvent.FlowRun(
                pluginId = pid,
                timestamp = entry.timestamp,
                flowId = flowMatch.groups["flow"]?.value ?: "unknown",
                triggerType = flowMatch.groups["trg"]?.value ?: "unknown",
                ok = flowMatch.groups["ok"]?.value == "true",
                steps = flowMatch.groups["steps"]?.value?.toIntOrNull() ?: 0,
                durationMs = flowMatch.groups["dur"]?.value?.toLongOrNull() ?: 0L,
                error = flowMatch.groups["err"]?.value?.takeIf { it != "null" }
            )
        }

        val auditMatch = auditRegex.find(msg)
        if (auditMatch != null) {
            val pid = auditMatch.groups["pid"]?.value ?: pluginIdFromTag ?: return null
            val type = auditMatch.groups["type"]?.value ?: "audit"
            val rest = auditMatch.groups["rest"]?.value ?: ""
            val details = parseKeyValuePairs(rest)
            return DeclarativeRunEvent.AuditEvent(
                pluginId = pid,
                timestamp = entry.timestamp,
                type = type,
                details = details
            )
        }

        return null
    }

    fun isDeclarativeRelevant(entry: LogEntry): Boolean {
        // Fast check before regex:
        if (!entry.tag.startsWith("PLUGIN/")) return false
        val m = entry.message
        return m.contains("[DECL_FLOW]") || m.contains("[DECL_AUDIT]")
    }

    private fun parseKeyValuePairs(input: String): Map<String, String> {
        // naive parser for " key=value key=value"
        val out = LinkedHashMap<String, String>()
        val tokens = input.trim().split(Regex("\\s+"))
        for (t in tokens) {
            val idx = t.indexOf('=')
            if (idx <= 0 || idx >= t.length - 1) continue
            val k = t.substring(0, idx)
            val v = t.substring(idx + 1)
            out[k] = v
        }
        return out
    }
}

