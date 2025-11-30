package com.ble1st.connectias.feature.network.monitor

import android.content.Context
import android.net.TrafficStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.models.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for extended bandwidth monitoring with per-interface and per-device tracking.
 */
@Singleton
class BandwidthMonitorProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkMonitorProvider: NetworkMonitorProvider
) {

    private val _bandwidthHistory = MutableStateFlow<List<BandwidthStats>>(emptyList())
    val bandwidthHistory: StateFlow<List<BandwidthStats>> = _bandwidthHistory.asStateFlow()

    /**
     * Gets bandwidth statistics for all active interfaces.
     * 
     * Note: This method returns stub data (all zeros) because per-interface statistics
     * require root access on Android, which is not available in standard applications.
     * The function signature and KDoc do not indicate this limitation clearly.
     * 
     * @return List of interface statistics with zero values (stub data)
     */
    /**
     * Gets interface metadata. Note: Traffic statistics (rx/tx bytes and rates)
     * are unavailable without root access and will always be zero.
     */
    suspend fun getInterfaceStats(): List<InterfaceStats> = withContext(Dispatchers.IO) {
        try {
            val interfaces = networkMonitorProvider.getActiveInterfaces()
            interfaces.map { networkInterface ->
                // Note: Per-interface stats require root access on Android
                // This is a simplified implementation that estimates based on total stats
                InterfaceStats(
                    interfaceName = networkInterface.name,
                    displayName = networkInterface.displayName,
                    rxBytes = 0L, // Would require root to get per-interface stats
                    txBytes = 0L,
                    rxRate = 0.0,
                    txRate = 0.0,
                    isActive = networkInterface.isUp
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get interface stats")
            emptyList()
        }
    }

    suspend fun getDeviceBandwidthStats(devices: List<NetworkDevice>): List<DeviceBandwidthStats> = withContext(Dispatchers.IO) {
        try {
            val totalTraffic = networkMonitorProvider.getCurrentTraffic()
            val deviceCount = devices.size

            // Distribute total bandwidth evenly among devices (simplified approach)
            // In a real implementation, this would require network monitoring at a lower level
            devices.map { device ->
                DeviceBandwidthStats(
                    deviceId = device.ipAddress,
                    deviceName = device.hostname,
                    estimatedRxBytes = totalTraffic.rxBytes / deviceCount,
                    estimatedTxBytes = totalTraffic.txBytes / deviceCount,
                    estimatedRxRate = totalTraffic.rxRate / deviceCount,
                    estimatedTxRate = totalTraffic.txRate / deviceCount
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate device bandwidth stats")
            emptyList()
        }
    }
            Timber.e(e, "Failed to calculate device bandwidth stats")
            emptyList()
        }
    }

    /**
     * Records bandwidth statistics to history.
     */
    fun recordBandwidthStats(stats: BandwidthStats) {
        _bandwidthHistory.update { currentHistory ->
            val newHistory = currentHistory.toMutableList()
            newHistory.add(stats)
            
            // Keep only last 100 entries
            if (newHistory.size > 100) {
                newHistory.removeAt(0)
            }
            
            newHistory
        }
    }

    /**
     * Gets bandwidth history for analysis.
     */
    fun getBandwidthHistory(): List<BandwidthStats> {
        return _bandwidthHistory.value
    }

    /**
     * Analyzes traffic patterns from history.
     * Computes statistics in a single pass for efficiency.
     */
    suspend fun analyzeTrafficPatterns(): TrafficPattern = withContext(Dispatchers.IO) {
        val history = _bandwidthHistory.value
        if (history.isEmpty()) {
            return@withContext TrafficPattern(
                averageRxRate = 0.0,
                averageTxRate = 0.0,
                peakRxRate = 0.0,
                peakTxRate = 0.0,
                totalBytes = 0L
            )
        }

        // Compute all statistics in a single pass
        var sumRxRate = 0.0
        var sumTxRate = 0.0
        var peakRxRate = 0.0
        var peakTxRate = 0.0
        var totalBytes = 0L
        var count = 0

        history.forEach { entry ->
            sumRxRate += entry.rxRate
            sumTxRate += entry.txRate
            peakRxRate = maxOf(peakRxRate, entry.rxRate)
            peakTxRate = maxOf(peakTxRate, entry.txRate)
            // Sum bytes across all history entries (rxBytes/txBytes are per-interval deltas)
            totalBytes += entry.rxBytes + entry.txBytes
            count++
        }

        TrafficPattern(
            averageRxRate = if (count > 0) sumRxRate / count else 0.0,
            averageTxRate = if (count > 0) sumTxRate / count else 0.0,
            peakRxRate = peakRxRate,
            peakTxRate = peakTxRate,
            totalBytes = totalBytes
        )
    }
}

/**
 * Interface bandwidth statistics.
 */
data class InterfaceStats(
    val interfaceName: String,
    val displayName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val rxRate: Double,
    val txRate: Double,
    val isActive: Boolean
)

/**
 * Device bandwidth statistics.
 */
data class DeviceBandwidthStats(
    val deviceId: String,
    val deviceName: String,
    val estimatedRxBytes: Long,
    val estimatedTxBytes: Long,
    val estimatedRxRate: Double,
    val estimatedTxRate: Double
)

/**
 * Bandwidth statistics snapshot.
 */
data class BandwidthStats(
    val rxBytes: Long,
    val txBytes: Long,
    val rxRate: Double,
    val txRate: Double,
    val timestamp: Long
)

/**
 * Traffic pattern analysis.
 */
data class TrafficPattern(
    val averageRxRate: Double,
    val averageTxRate: Double,
    val peakRxRate: Double,
    val peakTxRate: Double,
    val totalBytes: Long
)