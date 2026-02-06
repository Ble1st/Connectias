package com.ble1st.connectias.analytics.repo

import com.ble1st.connectias.analytics.model.PluginPerformanceSample
import com.ble1st.connectias.analytics.model.PluginUiActionEvent
import com.ble1st.connectias.analytics.model.SecurityEventCounterSample
import com.ble1st.connectias.analytics.model.PluginSessionEvent
import com.ble1st.connectias.analytics.model.SessionEventType
import com.ble1st.connectias.analytics.model.PluginLifecycleEvent
import com.ble1st.connectias.analytics.store.PluginAnalyticsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginAnalyticsRepository @Inject constructor(
    private val store: PluginAnalyticsStore
) {
    data class TimeWindow(val label: String, val durationMillis: Long) {
        companion object {
            val LAST_24H = TimeWindow("Last 24h", 24L * 60 * 60 * 1000)
            val LAST_7D = TimeWindow("Last 7d", 7L * 24 * 60 * 60 * 1000)
            val LAST_30D = TimeWindow("Last 30d", 30L * 24 * 60 * 60 * 1000)
        }
    }

    data class PluginPerfStats(
        val pluginId: String,
        val samples: Int,
        val avgCpu: Float,
        val peakCpu: Float,
        val avgMemMB: Float,
        val peakMemMB: Int,
        val netInBytes: Long,
        val netOutBytes: Long,
        val uiActions: Int,
        val rateLimitHits: Int,
        val sessionDurationMinutes: Long = 0,
        val sessionCount: Int = 0,
        val enableCount: Int = 0,
        val disableCount: Int = 0
    )

    suspend fun getPerfStats(window: TimeWindow): List<PluginPerfStats> = withContext(Dispatchers.Default) {
        val since = System.currentTimeMillis() - window.durationMillis

        val perf: List<PluginPerformanceSample> = store.readPerformanceSince(since).filter { it.timestamp >= since }
        val ui: List<PluginUiActionEvent> = store.readUiActionsSince(since).filter { it.timestamp >= since }
        val sec: List<SecurityEventCounterSample> = store.readSecurityEventsSince(since).filter { it.timestamp >= since }
        val sessions: List<PluginSessionEvent> = store.readSessionsSince(since).filter { it.timestamp >= since }
        val lifecycle: List<PluginLifecycleEvent> = store.readLifecycleEventsSince(since).filter { it.timestamp >= since }

        val uiCounts = ui.groupingBy { it.pluginId }.eachCount()
        val rateLimitCounts = sec
            .asSequence()
            .filter { it.eventType == "API_RATE_LIMITING" }
            .groupingBy { it.pluginId }
            .eachCount()

        // Calculate session durations by pairing START and END events
        val sessionStats = calculateSessionStats(sessions)

        // Count lifecycle events
        val lifecycleStats = calculateLifecycleStats(lifecycle)

        val byPlugin = perf.groupBy { it.pluginId }

        // Collect all plugin IDs that have any analytics data
        val allPluginIds = (byPlugin.keys + sessionStats.keys + lifecycleStats.keys).toSet()

        val stats = allPluginIds.map { pluginId ->
            val samples = byPlugin[pluginId] ?: emptyList()
            val cpuValues = samples.map { it.cpuPercent }
            val memValues = samples.map { it.memoryUsedMB }
            val peakCpu = cpuValues.maxOrNull() ?: 0f
            val avgCpu = if (cpuValues.isEmpty()) 0f else cpuValues.sum() / cpuValues.size
            val peakMem = memValues.maxOrNull() ?: 0
            val avgMem = if (memValues.isEmpty()) 0f else memValues.sum().toFloat() / memValues.size
            val netIn = samples.sumOf { it.netBytesIn }
            val netOut = samples.sumOf { it.netBytesOut }

            val sessionData = sessionStats[pluginId] ?: SessionData(0, 0)
            val lifecycleData = lifecycleStats[pluginId] ?: LifecycleData(0, 0)

            PluginPerfStats(
                pluginId = pluginId,
                samples = samples.size,
                avgCpu = avgCpu,
                peakCpu = peakCpu,
                avgMemMB = avgMem,
                peakMemMB = peakMem,
                netInBytes = netIn,
                netOutBytes = netOut,
                uiActions = uiCounts[pluginId] ?: 0,
                rateLimitHits = rateLimitCounts[pluginId] ?: 0,
                sessionDurationMinutes = sessionData.durationMinutes,
                sessionCount = sessionData.sessionCount,
                enableCount = lifecycleData.enableCount,
                disableCount = lifecycleData.disableCount
            )
        }

        stats.sortedWith(
            compareByDescending<PluginPerfStats> { it.peakCpu }
                .thenByDescending { it.peakMemMB }
                .thenByDescending { it.netOutBytes }
        )
    }

    private data class SessionData(val durationMinutes: Long, val sessionCount: Int)
    private data class LifecycleData(val enableCount: Int, val disableCount: Int)

    /**
     * Calculates session statistics by pairing FOREGROUND_START and FOREGROUND_END events.
     */
    private fun calculateSessionStats(sessions: List<PluginSessionEvent>): Map<String, SessionData> {
        val stats = mutableMapOf<String, SessionData>()

        sessions.groupBy { it.pluginId }.forEach { (pluginId, events) ->
            val sorted = events.sortedBy { it.timestamp }
            var totalDurationMs = 0L
            var sessionCount = 0
            var lastStart: Long? = null

            for (event in sorted) {
                when (event.eventType) {
                    SessionEventType.FOREGROUND_START -> {
                        lastStart = event.timestamp
                    }
                    SessionEventType.FOREGROUND_END -> {
                        if (lastStart != null) {
                            totalDurationMs += (event.timestamp - lastStart)
                            sessionCount++
                            lastStart = null
                        }
                    }
                }
            }

            val durationMinutes = totalDurationMs / (60 * 1000)
            stats[pluginId] = SessionData(durationMinutes, sessionCount)
        }

        return stats
    }

    /**
     * Calculates lifecycle statistics by counting enable and disable events.
     */
    private fun calculateLifecycleStats(lifecycle: List<PluginLifecycleEvent>): Map<String, LifecycleData> {
        return lifecycle.groupBy { it.pluginId }.mapValues { (_, events) ->
            val enableCount = events.count { it.eventType == com.ble1st.connectias.analytics.model.LifecycleEventType.ENABLED }
            val disableCount = events.count { it.eventType == com.ble1st.connectias.analytics.model.LifecycleEventType.DISABLED }
            LifecycleData(enableCount, disableCount)
        }
    }
}

