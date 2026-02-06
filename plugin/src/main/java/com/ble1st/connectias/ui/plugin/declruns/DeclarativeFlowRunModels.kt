package com.ble1st.connectias.ui.plugin.declruns

/**
 * Parsed representation of declarative flow execution logs.
 */
sealed class DeclarativeRunEvent {
    abstract val pluginId: String
    abstract val timestamp: Long

    data class FlowRun(
        override val pluginId: String,
        override val timestamp: Long,
        val flowId: String,
        val triggerType: String,
        val ok: Boolean,
        val steps: Int,
        val durationMs: Long,
        val error: String?
    ) : DeclarativeRunEvent()

    data class AuditEvent(
        override val pluginId: String,
        override val timestamp: Long,
        val type: String,
        val details: Map<String, String>
    ) : DeclarativeRunEvent()
}

data class DeclarativeRunStats(
    val total: Int,
    val ok: Int,
    val failed: Int,
    val rateLimited: Int
)

