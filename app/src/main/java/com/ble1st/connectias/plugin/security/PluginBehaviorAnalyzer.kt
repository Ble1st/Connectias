package com.ble1st.connectias.plugin.security

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Behavioral analysis engine for plugins
 * Establishes baselines and detects anomalies
 */
@Singleton
class PluginBehaviorAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionMonitor: PluginPermissionMonitor,
    private val networkPolicy: PluginNetworkPolicy
) {
    
    data class BehaviorBaseline(
        val pluginId: String,
        val apiCallPattern: Map<String, Int>,
        val fileAccessPattern: List<String>,
        val networkEndpoints: Set<String>,
        val permissionUsage: List<String>,
        val averageMemoryMB: Int,
        val averageCpuPercent: Float,
        val createdAt: Long = System.currentTimeMillis()
    )
    
    data class BehaviorPattern(
        val pluginId: String,
        val apiCalls: Map<String, Int>,
        val fileAccesses: List<String>,
        val networkConnections: Set<String>,
        val permissionsUsed: List<String>,
        val memoryUsageMB: Int,
        val cpuUsagePercent: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class Anomaly(
        val pluginId: String,
        val type: AnomalyType,
        val severity: Severity,
        val description: String,
        val detectedAt: Long = System.currentTimeMillis()
    )
    
    enum class AnomalyType {
        UNUSUAL_API_CALLS,
        SUSPICIOUS_FILE_ACCESS,
        UNEXPECTED_NETWORK_ACTIVITY,
        EXCESSIVE_PERMISSIONS,
        MEMORY_SPIKE,
        CPU_SPIKE,
        BEHAVIORAL_CHANGE
    }
    
    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private val baselines = ConcurrentHashMap<String, BehaviorBaseline>()
    private val currentBehavior = ConcurrentHashMap<String, BehaviorPattern>()
    private val baselineSamples = ConcurrentHashMap<String, MutableList<BehaviorPattern>>()
    private val _anomalies = MutableSharedFlow<Anomaly>(replay = 50)
    val anomalies: Flow<Anomaly> = _anomalies.asSharedFlow()
    
    companion object {
        private const val BASELINE_SAMPLE_COUNT = 10 // Samples needed before establishing baseline
    }
    
    /**
     * Establish behavior baseline for a plugin
     * Uses collected samples if available, otherwise creates initial baseline
     */
    suspend fun establishBaseline(pluginId: String): BehaviorBaseline = withContext(Dispatchers.Default) {
        val samples = baselineSamples[pluginId]
        val permStats = permissionMonitor.getPermissionStats(pluginId)
        
        val baseline = if (samples != null && samples.size >= BASELINE_SAMPLE_COUNT) {
            // Calculate baseline from collected samples (dynamic)
            val avgMemory = samples.map { it.memoryUsageMB }.average().toInt()
            val avgCpu = samples.map { it.cpuUsagePercent.toDouble() }.average().toFloat()
            val allApiCalls = samples.flatMap { it.apiCalls.entries }
                .groupBy { it.key }
                .mapValues { (_, entries) -> entries.sumOf { it.value } / samples.size }
            val allNetworkEndpoints = samples.flatMap { it.networkConnections }.toSet()
            val allFileAccesses = samples.flatMap { it.fileAccesses }.distinct()
            
            BehaviorBaseline(
                pluginId = pluginId,
                apiCallPattern = allApiCalls,
                fileAccessPattern = allFileAccesses,
                networkEndpoints = allNetworkEndpoints,
                permissionUsage = permStats.recentPermissions,
                averageMemoryMB = maxOf(avgMemory, 20), // Minimum 20MB baseline
                averageCpuPercent = maxOf(avgCpu, 5f) // Minimum 5% baseline
            )
        } else {
            // Initial baseline with conservative defaults
            BehaviorBaseline(
                pluginId = pluginId,
                apiCallPattern = emptyMap(),
                fileAccessPattern = emptyList(),
                networkEndpoints = emptySet(),
                permissionUsage = permStats.recentPermissions,
                averageMemoryMB = 50, // Conservative default
                averageCpuPercent = 10f
            )
        }
        
        baselines[pluginId] = baseline
        Timber.d("Baseline established for $pluginId (samples: ${samples?.size ?: 0})")
        
        return@withContext baseline
    }
    
    /**
     * Add a behavior sample for baseline calculation
     */
    fun addBaselineSample(pattern: BehaviorPattern) {
        val samples = baselineSamples.getOrPut(pattern.pluginId) { mutableListOf() }
        samples.add(pattern)
        
        // Keep only recent samples
        while (samples.size > BASELINE_SAMPLE_COUNT * 2) {
            samples.removeAt(0)
        }
        
        Timber.d("Added baseline sample for ${pattern.pluginId} (total: ${samples.size})")
    }
    
    /**
     * Record current behavior pattern
     */
    fun recordBehavior(pattern: BehaviorPattern) {
        currentBehavior[pattern.pluginId] = pattern
    }
    
    /**
     * Analyze deviations from baseline
     */
    suspend fun analyzeDeviations(current: BehaviorPattern): List<Anomaly> {
        val baseline = baselines[current.pluginId] ?: run {
            Timber.w("No baseline found for ${current.pluginId}, establishing...")
            establishBaseline(current.pluginId)
            return emptyList()
        }
        
        val anomalies = mutableListOf<Anomaly>()
        
        // Check API call patterns
        val apiAnomalies = detectApiAnomalies(baseline, current)
        anomalies.addAll(apiAnomalies)
        
        // Check file access patterns
        val fileAnomalies = detectFileAnomalies(baseline, current)
        anomalies.addAll(fileAnomalies)
        
        // Check network activity
        val networkAnomalies = detectNetworkAnomalies(baseline, current)
        anomalies.addAll(networkAnomalies)
        
        // Check resource usage
        val resourceAnomalies = detectResourceAnomalies(baseline, current)
        anomalies.addAll(resourceAnomalies)
        
        // Emit anomalies
        anomalies.forEach { _anomalies.emit(it) }
        
        return anomalies
    }
    
    /**
     * Detect API call anomalies
     */
    private fun detectApiAnomalies(baseline: BehaviorBaseline, current: BehaviorPattern): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check for new API calls not in baseline
        val newApiCalls = current.apiCalls.keys - baseline.apiCallPattern.keys
        if (newApiCalls.isNotEmpty()) {
            anomalies.add(
                Anomaly(
                    pluginId = current.pluginId,
                    type = AnomalyType.UNUSUAL_API_CALLS,
                    severity = Severity.MEDIUM,
                    description = "New API calls detected: ${newApiCalls.take(5).joinToString()}"
                )
            )
        }
        
        // Check for excessive API calls
        current.apiCalls.forEach { (api, count) ->
            val baselineCount = baseline.apiCallPattern[api] ?: 0
            if (count > baselineCount * 3) {
                anomalies.add(
                    Anomaly(
                        pluginId = current.pluginId,
                        type = AnomalyType.UNUSUAL_API_CALLS,
                        severity = Severity.HIGH,
                        description = "Excessive API calls to $api: $count (baseline: $baselineCount)"
                    )
                )
            }
        }
        
        return anomalies
    }
    
    /**
     * Detect file access anomalies
     */
    private fun detectFileAnomalies(baseline: BehaviorBaseline, current: BehaviorPattern): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Suspicious file paths
        val externalStorageDcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path
        val suspiciousPaths = listOf("/system", "/data/data", externalStorageDcimPath)
        val suspiciousAccess = current.fileAccesses.filter { path ->
            suspiciousPaths.any { path.startsWith(it) }
        }
        
        if (suspiciousAccess.isNotEmpty()) {
            anomalies.add(
                Anomaly(
                    pluginId = current.pluginId,
                    type = AnomalyType.SUSPICIOUS_FILE_ACCESS,
                    severity = Severity.HIGH,
                    description = "Suspicious file access: ${suspiciousAccess.take(3).joinToString()}"
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detect network anomalies
     */
    private fun detectNetworkAnomalies(baseline: BehaviorBaseline, current: BehaviorPattern): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Check for new network endpoints
        val newEndpoints = current.networkConnections - baseline.networkEndpoints
        if (newEndpoints.isNotEmpty()) {
            anomalies.add(
                Anomaly(
                    pluginId = current.pluginId,
                    type = AnomalyType.UNEXPECTED_NETWORK_ACTIVITY,
                    severity = Severity.MEDIUM,
                    description = "New network endpoints: ${newEndpoints.take(3).joinToString()}"
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detect resource usage anomalies
     */
    private fun detectResourceAnomalies(baseline: BehaviorBaseline, current: BehaviorPattern): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        // Memory spike
        if (current.memoryUsageMB > baseline.averageMemoryMB * 2) {
            anomalies.add(
                Anomaly(
                    pluginId = current.pluginId,
                    type = AnomalyType.MEMORY_SPIKE,
                    severity = Severity.HIGH,
                    description = "Memory spike: ${current.memoryUsageMB}MB (baseline: ${baseline.averageMemoryMB}MB)"
                )
            )
        }
        
        // CPU spike
        if (current.cpuUsagePercent > baseline.averageCpuPercent * 3) {
            anomalies.add(
                Anomaly(
                    pluginId = current.pluginId,
                    type = AnomalyType.CPU_SPIKE,
                    severity = Severity.MEDIUM,
                    description = "CPU spike: ${current.cpuUsagePercent}% (baseline: ${baseline.averageCpuPercent}%)"
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Get baseline for a plugin
     */
    fun getBaseline(pluginId: String): BehaviorBaseline? {
        return baselines[pluginId]
    }
    
    /**
     * Clear baseline for a plugin
     */
    fun clearBaseline(pluginId: String) {
        baselines.remove(pluginId)
        currentBehavior.remove(pluginId)
        baselineSamples.remove(pluginId)
    }
}
