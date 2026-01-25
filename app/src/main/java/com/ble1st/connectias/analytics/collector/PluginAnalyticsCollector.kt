package com.ble1st.connectias.analytics.collector

import android.content.Context
import com.ble1st.connectias.analytics.model.PluginPerformanceSample
import com.ble1st.connectias.analytics.model.SecurityEventCounterSample
import com.ble1st.connectias.analytics.store.PluginAnalyticsStore
import com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter
import com.ble1st.connectias.plugin.security.PluginNetworkTracker
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects performance analytics in the main process:
 * - Resource limiter usage (CPU/RAM/etc.)
 * - Network tracker deltas
 * - Selected security events (rate limiting, etc.)
 */
@Singleton
class PluginAnalyticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceLimiter: EnhancedPluginResourceLimiter,
    private val securityAuditManager: SecurityAuditManager,
    private val store: PluginAnalyticsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    private val lastNetIn = HashMap<String, Long>()
    private val lastNetOut = HashMap<String, Long>()

    fun start() {
        if (started) return
        if (isIsolatedSandboxProcess()) {
            Timber.i("[ANALYTICS] Skipping collector start in isolated sandbox")
            return
        }
        started = true

        // Retention compaction (best-effort, periodic)
        scope.launch {
            while (true) {
                try {
                    store.compactRetention(retentionDays = 30)
                } catch (e: Exception) {
                    Timber.w(e, "[ANALYTICS] Retention compaction failed")
                }
                delay(6 * 60 * 60 * 1000L) // 6h
            }
        }

        // Collect resource usage and write samples periodically.
        scope.launch {
            resourceLimiter.resourceUsage.collectLatest { map ->
                val now = System.currentTimeMillis()
                map.values.forEach { usage ->
                    val net = PluginNetworkTracker.getNetworkUsage(usage.pluginId)
                    val totalIn = net?.bytesReceived?.get() ?: 0L
                    val totalOut = net?.bytesSent?.get() ?: 0L

                    val prevIn = lastNetIn[usage.pluginId] ?: totalIn
                    val prevOut = lastNetOut[usage.pluginId] ?: totalOut

                    val deltaIn = (totalIn - prevIn).coerceAtLeast(0L)
                    val deltaOut = (totalOut - prevOut).coerceAtLeast(0L)

                    lastNetIn[usage.pluginId] = totalIn
                    lastNetOut[usage.pluginId] = totalOut

                    val sample = PluginPerformanceSample(
                        timestamp = now,
                        pluginId = usage.pluginId,
                        memoryUsedMB = usage.memoryUsedMB,
                        memoryPeakMB = usage.memoryPeakMB,
                        cpuPercent = usage.cpuPercent,
                        activeThreads = usage.activeThreads,
                        diskUsageMB = usage.diskUsageMB,
                        netBytesIn = deltaIn,
                        netBytesOut = deltaOut
                    )
                    store.appendPerformance(sample)
                }
            }
        }

        // Collect security events for counting (e.g., rate limiting).
        scope.launch {
            securityAuditManager.eventStream.collectLatest { ev ->
                val pluginId = ev.pluginId ?: return@collectLatest
                val sample = SecurityEventCounterSample(
                    timestamp = ev.timestamp,
                    pluginId = pluginId,
                    eventType = ev.eventType.name,
                    severity = ev.severity.name
                )
                store.appendSecurityEvent(sample)
            }
        }

        Timber.i("[ANALYTICS] Collector started")
    }

    private fun isIsolatedSandboxProcess(): Boolean {
        val processName = getCurrentProcessName()
        return processName.contains(":plugin_sandbox")
    }

    private fun getCurrentProcessName(): String {
        return try {
            android.app.Application.getProcessName()
        } catch (_: Exception) {
            ""
        }
    }
}

