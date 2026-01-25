package com.ble1st.connectias.analytics.repo

import com.ble1st.connectias.analytics.model.PluginPerformanceSample
import com.ble1st.connectias.analytics.model.PluginUiActionEvent
import com.ble1st.connectias.analytics.model.SecurityEventCounterSample
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
        val rateLimitHits: Int
    )

    suspend fun getPerfStats(window: TimeWindow): List<PluginPerfStats> = withContext(Dispatchers.Default) {
        val since = System.currentTimeMillis() - window.durationMillis

        val perf: List<PluginPerformanceSample> = store.readPerformanceSince(since).filter { it.timestamp >= since }
        val ui: List<PluginUiActionEvent> = store.readUiActionsSince(since).filter { it.timestamp >= since }
        val sec: List<SecurityEventCounterSample> = store.readSecurityEventsSince(since).filter { it.timestamp >= since }

        val uiCounts = ui.groupingBy { it.pluginId }.eachCount()
        val rateLimitCounts = sec
            .asSequence()
            .filter { it.eventType == "API_RATE_LIMITING" }
            .groupingBy { it.pluginId }
            .eachCount()

        val byPlugin = perf.groupBy { it.pluginId }
        val stats = byPlugin.map { (pluginId, samples) ->
            val cpuValues = samples.map { it.cpuPercent }
            val memValues = samples.map { it.memoryUsedMB }
            val peakCpu = cpuValues.maxOrNull() ?: 0f
            val avgCpu = if (cpuValues.isEmpty()) 0f else cpuValues.sum() / cpuValues.size
            val peakMem = memValues.maxOrNull() ?: 0
            val avgMem = if (memValues.isEmpty()) 0f else memValues.sum().toFloat() / memValues.size
            val netIn = samples.sumOf { it.netBytesIn }
            val netOut = samples.sumOf { it.netBytesOut }

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
                rateLimitHits = rateLimitCounts[pluginId] ?: 0
            )
        }

        stats.sortedWith(
            compareByDescending<PluginPerfStats> { it.peakCpu }
                .thenByDescending { it.peakMemMB }
                .thenByDescending { it.netOutBytes }
        )
    }
}

