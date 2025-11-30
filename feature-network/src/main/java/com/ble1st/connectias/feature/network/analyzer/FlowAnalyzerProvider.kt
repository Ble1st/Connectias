package com.ble1st.connectias.feature.network.analyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.models.NetworkFlow
import com.ble1st.connectias.feature.network.models.FlowStats
import com.ble1st.connectias.feature.network.models.TopTalker
import com.ble1st.connectias.feature.network.monitor.NetworkMonitorProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for network flow analysis.
 * Analyzes connection patterns and traffic statistics.
 */
@Singleton
class FlowAnalyzerProvider @Inject constructor(
    private val networkMonitorProvider: NetworkMonitorProvider
) {
    private val flowHistory = mutableListOf<NetworkFlow>()
    private val flowHistoryMutex = Mutex()

    /**
     * Tracks network flows based on traffic monitoring.
     * Since Android doesn't provide direct connection tracking, this is inferred from traffic patterns.
     */
    suspend fun trackFlows(devices: List<com.ble1st.connectias.feature.network.models.NetworkDevice>): List<NetworkFlow> = withContext(Dispatchers.IO) {
        try {

            val flows = mutableListOf<NetworkFlow>()

            // Create flows based on device discovery and traffic
            // This is a simplified implementation - real flow tracking would require lower-level network access
            devices.forEach { device ->
                flows.add(
                    NetworkFlow(
                        sourceIp = device.ipAddress,
                        destinationIp = "unknown", // Would require connection tracking
                        protocol = null,
                        bytesTransferred = 0L, // Would require per-connection stats
                        packetsTransferred = 0L,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // Add to history with thread-safe access
            flowHistoryMutex.withLock {
                flowHistory.addAll(flows)
                
                // Keep only last 1000 flows
                if (flowHistory.size > 1000) {
                    val excess = flowHistory.size - 1000
                    repeat(excess) { flowHistory.removeAt(0) }
                }
            }

            flows
        } catch (e: Exception) {
            Timber.e(e, "Failed to track network flows")
            emptyList()
        }
    }

    /**
     * Analyzes flow statistics.
     */
    suspend fun analyzeFlowStats(devices: List<com.ble1st.connectias.feature.network.models.NetworkDevice>): FlowStats = withContext(Dispatchers.IO) {
        try {
            val currentTraffic = networkMonitorProvider.getCurrentTraffic()
            
            // Calculate top talkers based on estimated traffic distribution
            val deviceCount = devices.size
            val bytesPerDevice = if (deviceCount > 0) {
                (currentTraffic.rxBytes + currentTraffic.txBytes) / deviceCount
            } else {
                0L
            }

            val flowHistorySnapshot = flowHistoryMutex.withLock {
                flowHistory.toList()
            }
            
            val topTalkers = devices.map { device ->
                TopTalker(
                    ipAddress = device.ipAddress,
                    hostname = device.hostname,
                    bytesTransferred = bytesPerDevice,
                    flowCount = flowHistorySnapshot.count { it.sourceIp == device.ipAddress }
                )
            }.sortedByDescending { it.bytesTransferred }
                .take(10)

            // Protocol distribution (simplified - would require actual packet inspection)
            val protocolDistribution = mapOf(
                "HTTP/HTTPS" to (currentTraffic.rxBytes + currentTraffic.txBytes) / 2,
                "Other" to (currentTraffic.rxBytes + currentTraffic.txBytes) / 2
            )

            FlowStats(
                topTalkers = topTalkers,
                protocolDistribution = protocolDistribution,
                totalFlows = flowHistorySnapshot.size,
                totalBytes = currentTraffic.rxBytes + currentTraffic.txBytes
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to analyze flow stats")
            FlowStats(
                topTalkers = emptyList(),
                protocolDistribution = emptyMap(),
                totalFlows = 0,
                totalBytes = 0L
            )
        }
    }

    /**
     * Gets flow history.
     */
    suspend fun getFlowHistory(): List<NetworkFlow> = flowHistoryMutex.withLock {
        return@withLock flowHistory.toList()
    }
}
