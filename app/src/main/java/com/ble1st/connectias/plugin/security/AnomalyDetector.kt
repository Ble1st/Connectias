package com.ble1st.connectias.plugin.security

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced anomaly detection engine
 * Uses pattern matching and statistical analysis
 */
@Singleton
class AnomalyDetector @Inject constructor() {
    
    data class DetectionConfig(
        val sensitivityLevel: Float = 0.7f, // 0.0 = low, 1.0 = high
        val minSampleSize: Int = 10,
        val anomalyThreshold: Float = 2.0f // Standard deviations
    )
    
    private val config = DetectionConfig()
    
    /**
     * Detect anomalies using statistical analysis
     */
    fun detectAnomalies(
        baseline: PluginBehaviorAnalyzer.BehaviorBaseline,
        current: PluginBehaviorAnalyzer.BehaviorPattern
    ): List<PluginBehaviorAnalyzer.Anomaly> {
        val anomalies = mutableListOf<PluginBehaviorAnalyzer.Anomaly>()
        
        // Statistical analysis of numeric values
        anomalies.addAll(detectStatisticalAnomalies(baseline, current))
        
        // Pattern-based detection
        anomalies.addAll(detectPatternAnomalies(baseline, current))
        
        // Sequence-based detection
        anomalies.addAll(detectSequenceAnomalies(baseline, current))
        
        Timber.d("Detected ${anomalies.size} anomalies for ${current.pluginId}")
        return anomalies
    }
    
    /**
     * Detect statistical anomalies (Z-score based)
     */
    private fun detectStatisticalAnomalies(
        baseline: PluginBehaviorAnalyzer.BehaviorBaseline,
        current: PluginBehaviorAnalyzer.BehaviorPattern
    ): List<PluginBehaviorAnalyzer.Anomaly> {
        val anomalies = mutableListOf<PluginBehaviorAnalyzer.Anomaly>()
        
        // Memory usage Z-score with minimum stdDev to prevent division by zero
        val memoryStdDev = maxOf(baseline.averageMemoryMB * 0.2f, 10f) // Min 10MB stdDev
        val memoryZScore = calculateZScore(
            current.memoryUsageMB.toFloat(),
            baseline.averageMemoryMB.toFloat(),
            memoryStdDev
        )
        
        if (memoryZScore > config.anomalyThreshold) {
            anomalies.add(
                PluginBehaviorAnalyzer.Anomaly(
                    pluginId = current.pluginId,
                    type = PluginBehaviorAnalyzer.AnomalyType.MEMORY_SPIKE,
                    severity = determineSeverity(memoryZScore),
                    description = "Memory usage anomaly (Z-score: %.2f)".format(memoryZScore)
                )
            )
        }
        
        // CPU usage Z-score with minimum stdDev to prevent division by zero
        val cpuStdDev = maxOf(baseline.averageCpuPercent * 0.3f, 5f) // Min 5% stdDev
        val cpuZScore = calculateZScore(
            current.cpuUsagePercent,
            baseline.averageCpuPercent,
            cpuStdDev
        )
        
        if (cpuZScore > config.anomalyThreshold) {
            anomalies.add(
                PluginBehaviorAnalyzer.Anomaly(
                    pluginId = current.pluginId,
                    type = PluginBehaviorAnalyzer.AnomalyType.CPU_SPIKE,
                    severity = determineSeverity(cpuZScore),
                    description = "CPU usage anomaly (Z-score: %.2f)".format(cpuZScore)
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detect pattern-based anomalies
     */
    private fun detectPatternAnomalies(
        baseline: PluginBehaviorAnalyzer.BehaviorBaseline,
        current: PluginBehaviorAnalyzer.BehaviorPattern
    ): List<PluginBehaviorAnalyzer.Anomaly> {
        val anomalies = mutableListOf<PluginBehaviorAnalyzer.Anomaly>()
        
        // Check for completely new behavior patterns
        val newApiCalls = current.apiCalls.keys - baseline.apiCallPattern.keys
        val newBehaviorRatio = newApiCalls.size.toFloat() / maxOf(baseline.apiCallPattern.size, 1)
        
        if (newBehaviorRatio > 0.5f) {
            anomalies.add(
                PluginBehaviorAnalyzer.Anomaly(
                    pluginId = current.pluginId,
                    type = PluginBehaviorAnalyzer.AnomalyType.BEHAVIORAL_CHANGE,
                    severity = PluginBehaviorAnalyzer.Severity.HIGH,
                    description = "Significant behavior change: ${(newBehaviorRatio * 100).toInt()}% new patterns"
                )
            )
        }
        
        // Check for permission escalation
        val newPermissions = current.permissionsUsed - baseline.permissionUsage.toSet()
        if (newPermissions.isNotEmpty()) {
            val severity = if (newPermissions.size > 3) {
                PluginBehaviorAnalyzer.Severity.CRITICAL
            } else {
                PluginBehaviorAnalyzer.Severity.HIGH
            }
            
            anomalies.add(
                PluginBehaviorAnalyzer.Anomaly(
                    pluginId = current.pluginId,
                    type = PluginBehaviorAnalyzer.AnomalyType.EXCESSIVE_PERMISSIONS,
                    severity = severity,
                    description = "Permission escalation: ${newPermissions.take(3).joinToString()}"
                )
            )
        }
        
        return anomalies
    }
    
    /**
     * Detect sequence-based anomalies
     */
    private fun detectSequenceAnomalies(
        baseline: PluginBehaviorAnalyzer.BehaviorBaseline,
        current: PluginBehaviorAnalyzer.BehaviorPattern
    ): List<PluginBehaviorAnalyzer.Anomaly> {
        val anomalies = mutableListOf<PluginBehaviorAnalyzer.Anomaly>()
        
        // Check for unusual file access sequences
        val suspiciousSequences = listOf(
            listOf("/system", "/data"),
            listOf("/sdcard", "/data/data"),
            listOf("/proc", "/system")
        )
        
        for (sequence in suspiciousSequences) {
            if (containsSequence(current.fileAccesses, sequence)) {
                anomalies.add(
                    PluginBehaviorAnalyzer.Anomaly(
                        pluginId = current.pluginId,
                        type = PluginBehaviorAnalyzer.AnomalyType.SUSPICIOUS_FILE_ACCESS,
                        severity = PluginBehaviorAnalyzer.Severity.CRITICAL,
                        description = "Suspicious file access sequence: ${sequence.joinToString(" -> ")}"
                    )
                )
            }
        }
        
        return anomalies
    }
    
    /**
     * Calculate Z-score (standard score)
     */
    private fun calculateZScore(value: Float, mean: Float, stdDev: Float): Float {
        if (stdDev == 0f) return 0f
        return (value - mean) / stdDev
    }
    
    /**
     * Determine severity based on Z-score
     */
    private fun determineSeverity(zScore: Float): PluginBehaviorAnalyzer.Severity {
        return when {
            zScore > 4.0f -> PluginBehaviorAnalyzer.Severity.CRITICAL
            zScore > 3.0f -> PluginBehaviorAnalyzer.Severity.HIGH
            zScore > 2.0f -> PluginBehaviorAnalyzer.Severity.MEDIUM
            else -> PluginBehaviorAnalyzer.Severity.LOW
        }
    }
    
    /**
     * Check if list contains a sequence of items
     */
    private fun containsSequence(list: List<String>, sequence: List<String>): Boolean {
        if (sequence.isEmpty() || list.size < sequence.size) return false
        
        for (i in 0..list.size - sequence.size) {
            var match = true
            for (j in sequence.indices) {
                if (!list[i + j].contains(sequence[j])) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        
        return false
    }
}
