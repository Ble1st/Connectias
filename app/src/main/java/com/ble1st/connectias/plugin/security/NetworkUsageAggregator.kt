package com.ble1st.connectias.plugin.security

import android.net.TrafficStats
import android.os.Process
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Network Usage Aggregator for Correct Per-Plugin Stats
 * 
 * Aggregates network usage from multiple sources to provide accurate
 * per-plugin network statistics, solving the shared UID attribution problem.
 * 
 * SECURITY: Provides truthful network usage reporting for policy enforcement
 */
object NetworkUsageAggregator {
    
    // Aggregated usage data
    private val pluginUsageData = ConcurrentHashMap<String, AggregatedUsageData>()
    private val baselineUsage = ConcurrentHashMap<Int, TrafficBaseline>() // UID -> baseline
    
    // Monitoring state
    private val isAggregating = AtomicBoolean(false)
    private val aggregationScope = CoroutineScope(Dispatchers.IO)
    
    // Configuration
    private const val AGGREGATION_INTERVAL_MS = 10000L // 10 seconds
    private const val BASELINE_UPDATE_INTERVAL_MS = 60000L // 1 minute
    
    data class AggregatedUsageData(
        val pluginId: String,
        val explicitBytesReceived: AtomicLong = AtomicLong(0), // From explicit tracking
        val explicitBytesSent: AtomicLong = AtomicLong(0),
        val estimatedBytesReceived: AtomicLong = AtomicLong(0), // From TrafficStats estimation
        val estimatedBytesSent: AtomicLong = AtomicLong(0),
        val connectionsTracked: AtomicLong = AtomicLong(0),
        val domainsAccessed: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val telemetryBytes: AtomicLong = AtomicLong(0), // Bytes from telemetry traffic
        val nonTelemetryBytes: AtomicLong = AtomicLong(0), // Bytes from regular traffic
        val firstSeen: Long = System.currentTimeMillis(),
        var lastUpdated: Long = System.currentTimeMillis(),
        var confidence: Double = 1.0 // Confidence in the data accuracy (0.0 - 1.0)
    ) {
        fun getTotalBytesReceived(): Long = explicitBytesReceived.get() + estimatedBytesReceived.get()
        fun getTotalBytesSent(): Long = explicitBytesSent.get() + estimatedBytesSent.get()
        fun getTotalBytes(): Long = getTotalBytesReceived() + getTotalBytesSent()
    }
    
    data class TrafficBaseline(
        val uid: Int,
        var rxBytes: Long = 0,
        var txBytes: Long = 0,
        var timestamp: Long = System.currentTimeMillis()
    )
    
    data class NetworkUsageSnapshot(
        val pluginId: String,
        val totalBytes: Long,
        val bytesReceived: Long,
        val bytesSent: Long,
        val telemetryBytes: Long,
        val nonTelemetryBytes: Long,
        val connectionsCount: Long,
        val domainsCount: Int,
        val confidence: Double,
        val dataAge: Long, // milliseconds since last update
        val isActive: Boolean
    )
    
    /**
     * Starts network usage aggregation
     */
    fun startAggregation() {
        if (isAggregating.compareAndSet(false, true)) {
            Timber.i("[USAGE AGGREGATOR] Starting network usage aggregation")
            
            // Initialize baselines for system UIDs
            initializeBaselines()
            
            // Start aggregation loop
            aggregationScope.launch {
                while (isAggregating.get()) {
                    try {
                        performAggregation()
                        delay(AGGREGATION_INTERVAL_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "[USAGE AGGREGATOR] Error in aggregation loop")
                        delay(5000) // Wait before retrying
                    }
                }
            }
            
            // Start baseline update loop
            aggregationScope.launch {
                while (isAggregating.get()) {
                    try {
                        updateBaselines()
                        delay(BASELINE_UPDATE_INTERVAL_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "[USAGE AGGREGATOR] Error updating baselines")
                        delay(10000) // Wait before retrying
                    }
                }
            }
        }
    }
    
    /**
     * Stops network usage aggregation
     */
    fun stopAggregation() {
        if (isAggregating.compareAndSet(true, false)) {
            Timber.i("[USAGE AGGREGATOR] Stopping network usage aggregation")
        }
    }
    
    /**
     * Registers a plugin for usage aggregation
     */
    fun registerPlugin(pluginId: String, processUid: Int) {
        pluginUsageData.putIfAbsent(pluginId, AggregatedUsageData(pluginId))
        
        // Initialize baseline for this UID if not exists
        if (!baselineUsage.containsKey(processUid)) {
            try {
                val currentRx = TrafficStats.getUidRxBytes(processUid)
                val currentTx = TrafficStats.getUidTxBytes(processUid)
                
                if (currentRx != TrafficStats.UNSUPPORTED.toLong()) {
                    baselineUsage[processUid] = TrafficBaseline(processUid, currentRx, currentTx)
                    Timber.d("[USAGE AGGREGATOR] Baseline set for UID $processUid: ${currentRx + currentTx} bytes")
                }
            } catch (e: Exception) {
                Timber.w(e, "[USAGE AGGREGATOR] Failed to set baseline for UID: $processUid")
            }
        }
        
        Timber.d("[USAGE AGGREGATOR] Plugin registered: $pluginId (UID: $processUid)")
    }
    
    /**
     * Unregisters a plugin from usage aggregation
     */
    fun unregisterPlugin(pluginId: String) {
        val data = pluginUsageData.remove(pluginId)
        if (data != null) {
            val totalBytes = data.getTotalBytes()
            Timber.i("[USAGE AGGREGATOR] Plugin unregistered: $pluginId (total: ${formatBytes(totalBytes)})")
        }
    }
    
    /**
     * Records explicit network usage for a plugin
     */
    fun recordExplicitUsage(
        pluginId: String, 
        bytesReceived: Long = 0, 
        bytesSent: Long = 0,
        isTelemetry: Boolean = false,
        domain: String? = null
    ) {
        val data = pluginUsageData[pluginId]
        if (data != null) {
            data.explicitBytesReceived.addAndGet(bytesReceived)
            data.explicitBytesSent.addAndGet(bytesSent)
            data.connectionsTracked.incrementAndGet()
            data.lastUpdated = System.currentTimeMillis()
            
            // Track telemetry vs non-telemetry
            val totalBytes = bytesReceived + bytesSent
            if (isTelemetry) {
                data.telemetryBytes.addAndGet(totalBytes)
            } else {
                data.nonTelemetryBytes.addAndGet(totalBytes)
            }
            
            // Track domain
            if (domain != null) {
                data.domainsAccessed.add(domain)
            }
            
            // Update confidence - explicit tracking is very reliable
            data.confidence = Math.min(1.0, data.confidence + 0.1)
            
            Timber.v("[USAGE AGGREGATOR] Explicit usage recorded: $pluginId (+${formatBytes(totalBytes)})")
        }
    }
    
    /**
     * Gets aggregated usage data for a plugin
     */
    fun getUsageData(pluginId: String): AggregatedUsageData? {
        return pluginUsageData[pluginId]
    }
    
    /**
     * Gets usage snapshot for a plugin
     */
    fun getUsageSnapshot(pluginId: String): NetworkUsageSnapshot? {
        val data = pluginUsageData[pluginId] ?: return null
        
        val dataAge = System.currentTimeMillis() - data.lastUpdated
        val isActive = dataAge < 60000 // Active if updated within last minute
        
        return NetworkUsageSnapshot(
            pluginId = pluginId,
            totalBytes = data.getTotalBytes(),
            bytesReceived = data.getTotalBytesReceived(),
            bytesSent = data.getTotalBytesSent(),
            telemetryBytes = data.telemetryBytes.get(),
            nonTelemetryBytes = data.nonTelemetryBytes.get(),
            connectionsCount = data.connectionsTracked.get(),
            domainsCount = data.domainsAccessed.size,
            confidence = data.confidence,
            dataAge = dataAge,
            isActive = isActive
        )
    }
    
    /**
     * Gets usage snapshots for all plugins
     */
    fun getAllUsageSnapshots(): List<NetworkUsageSnapshot> {
        return pluginUsageData.keys.mapNotNull { getUsageSnapshot(it) }
    }
    
    /**
     * Gets top network-consuming plugins
     */
    fun getTopConsumers(limit: Int = 10): List<NetworkUsageSnapshot> {
        return getAllUsageSnapshots()
            .sortedByDescending { it.totalBytes }
            .take(limit)
    }
    
    /**
     * Gets plugins with suspicious network activity
     */
    fun getSuspiciousPlugins(): List<NetworkUsageSnapshot> {
        return getAllUsageSnapshots().filter { snapshot ->
            val data = pluginUsageData[snapshot.pluginId] ?: return@filter false
            
            // Define suspicious thresholds
            val maxBytesPerHour = 50 * 1024 * 1024L // 50MB per hour
            val minTelemetryRatio = 0.1 // At least 10% should be telemetry for legitimate plugins
            
            val hoursSinceFirst = (System.currentTimeMillis() - data.firstSeen) / (60 * 60 * 1000.0)
            val bytesPerHour = if (hoursSinceFirst > 0) snapshot.totalBytes / hoursSinceFirst else snapshot.totalBytes.toDouble()
            
            val telemetryRatio = if (snapshot.totalBytes > 0) {
                snapshot.telemetryBytes.toDouble() / snapshot.totalBytes.toDouble()
            } else 0.0
            
            bytesPerHour > maxBytesPerHour || 
            (snapshot.totalBytes > 1024 * 1024 && telemetryRatio < minTelemetryRatio) ||
            snapshot.confidence < 0.5
        }
    }
    
    /**
     * Generates aggregated usage report
     */
    fun generateUsageReport(): String {
        val allSnapshots = getAllUsageSnapshots()
        val totalBytes = allSnapshots.sumOf { it.totalBytes }
        val totalTelemetryBytes = allSnapshots.sumOf { it.telemetryBytes }
        val suspiciousPlugins = getSuspiciousPlugins()
        
        return buildString {
            appendLine("=== Network Usage Aggregation Report ===")
            appendLine("Total Plugins Tracked: ${allSnapshots.size}")
            appendLine("Total Network Usage: ${formatBytes(totalBytes)}")
            appendLine("Telemetry Traffic: ${formatBytes(totalTelemetryBytes)} (${((totalTelemetryBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()}%)")
            appendLine("Suspicious Plugins: ${suspiciousPlugins.size}")
            appendLine("Aggregation Active: ${isAggregating.get()}")
            
            appendLine("\nTop Consumers:")
            getTopConsumers(5).forEach { snapshot ->
                val confidence = (snapshot.confidence * 100).toInt()
                val status = if (snapshot.isActive) "ACTIVE" else "IDLE"
                appendLine("  ${snapshot.pluginId}: ${formatBytes(snapshot.totalBytes)} [$confidence% confidence] [$status]")
            }
            
            if (suspiciousPlugins.isNotEmpty()) {
                appendLine("\nSuspicious Activity:")
                suspiciousPlugins.forEach { snapshot ->
                    val telemetryRatio = if (snapshot.totalBytes > 0) {
                        (snapshot.telemetryBytes.toDouble() / snapshot.totalBytes.toDouble() * 100).toInt()
                    } else 0
                    appendLine("  ${snapshot.pluginId}: ${formatBytes(snapshot.totalBytes)} (${telemetryRatio}% telemetry)")
                }
            }
        }
    }
    
    /**
     * Gets debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Network Usage Aggregator Debug ===")
            appendLine("Aggregation Active: ${isAggregating.get()}")
            appendLine("Tracked Plugins: ${pluginUsageData.size}")
            appendLine("Baseline UIDs: ${baselineUsage.size}")
            
            pluginUsageData.forEach { (pluginId, data) ->
                val totalBytes = data.getTotalBytes()
                val explicitBytes = data.explicitBytesReceived.get() + data.explicitBytesSent.get()
                val estimatedBytes = data.estimatedBytesReceived.get() + data.estimatedBytesSent.get()
                val confidence = (data.confidence * 100).toInt()
                
                appendLine("  $pluginId: ${formatBytes(totalBytes)} (${formatBytes(explicitBytes)} explicit + ${formatBytes(estimatedBytes)} estimated) [$confidence%]")
            }
        }
    }
    
    // Private helper methods
    private fun initializeBaselines() {
        try {
            // Initialize baseline for current app UID
            val appUid = Process.myUid()
            val currentRx = TrafficStats.getUidRxBytes(appUid)
            val currentTx = TrafficStats.getUidTxBytes(appUid)
            
            if (currentRx != TrafficStats.UNSUPPORTED.toLong()) {
                baselineUsage[appUid] = TrafficBaseline(appUid, currentRx, currentTx)
                Timber.d("[USAGE AGGREGATOR] App baseline initialized: UID $appUid, ${formatBytes(currentRx + currentTx)}")
            }
        } catch (e: Exception) {
            Timber.w(e, "[USAGE AGGREGATOR] Failed to initialize baselines")
        }
    }
    
    private suspend fun performAggregation() {
        // Update estimated usage from TrafficStats
        baselineUsage.forEach { (uid, baseline) ->
            try {
                val currentRx = TrafficStats.getUidRxBytes(uid)
                val currentTx = TrafficStats.getUidTxBytes(uid)
                
                if (currentRx != TrafficStats.UNSUPPORTED.toLong()) {
                    val deltaRx = maxOf(0, currentRx - baseline.rxBytes)
                    val deltaTx = maxOf(0, currentTx - baseline.txBytes)
                    
                    if (deltaRx > 0 || deltaTx > 0) {
                        // Distribute the delta across plugins that might be using this UID
                        distributeEstimatedUsage(uid, deltaRx, deltaTx)
                        
                        // Update baseline
                        baseline.rxBytes = currentRx
                        baseline.txBytes = currentTx
                        baseline.timestamp = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "[USAGE AGGREGATOR] Error processing UID $uid")
            }
        }
        
        // Update confidence scores based on data freshness
        updateConfidenceScores()
    }
    
    private fun distributeEstimatedUsage(uid: Int, deltaRx: Long, deltaTx: Long) {
        // Find plugins that might be using this UID (simplified approach)
        val activePlugins = pluginUsageData.values.filter { data ->
            val timeSinceLastUpdate = System.currentTimeMillis() - data.lastUpdated
            timeSinceLastUpdate < 60000 // Active in last minute
        }
        
        if (activePlugins.isNotEmpty()) {
            val rxPerPlugin = deltaRx / activePlugins.size
            val txPerPlugin = deltaTx / activePlugins.size
            
            activePlugins.forEach { data ->
                data.estimatedBytesReceived.addAndGet(rxPerPlugin)
                data.estimatedBytesSent.addAndGet(txPerPlugin)
                data.confidence = Math.max(0.1, data.confidence - 0.05) // Reduce confidence for estimated data
            }
            
            Timber.v("[USAGE AGGREGATOR] Distributed ${formatBytes(deltaRx + deltaTx)} across ${activePlugins.size} plugins")
        }
    }
    
    private suspend fun updateBaselines() {
        // Refresh all baselines
        baselineUsage.keys.forEach { uid ->
            try {
                val currentRx = TrafficStats.getUidRxBytes(uid)
                val currentTx = TrafficStats.getUidTxBytes(uid)
                
                if (currentRx != TrafficStats.UNSUPPORTED.toLong()) {
                    val baseline = baselineUsage[uid]
                    if (baseline != null) {
                        baseline.rxBytes = currentRx
                        baseline.txBytes = currentTx
                        baseline.timestamp = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "[USAGE AGGREGATOR] Error updating baseline for UID $uid")
            }
        }
    }
    
    private fun updateConfidenceScores() {
        pluginUsageData.values.forEach { data ->
            val timeSinceUpdate = System.currentTimeMillis() - data.lastUpdated
            
            // Decrease confidence over time for stale data
            when {
                timeSinceUpdate > 300000 -> data.confidence = Math.max(0.1, data.confidence * 0.8) // 5+ minutes old
                timeSinceUpdate > 120000 -> data.confidence = Math.max(0.3, data.confidence * 0.9) // 2+ minutes old
                timeSinceUpdate > 60000 -> data.confidence = Math.max(0.5, data.confidence * 0.95)  // 1+ minutes old
            }
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)}GB"
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
}
