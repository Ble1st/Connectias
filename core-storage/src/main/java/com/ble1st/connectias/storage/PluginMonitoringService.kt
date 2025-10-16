package com.ble1st.connectias.storage

import com.ble1st.connectias.storage.database.PluginDatabase
import com.ble1st.connectias.storage.database.entity.PluginAlertEntity
import kotlinx.coroutines.delay
import timber.log.Timber

class PluginMonitoringService(
    private val pluginId: String,
    private val database: PluginDatabase
) {
    private val runtime = Runtime.getRuntime()
    private var baseline: PluginBaseline? = null
    private var baselineStartTime: Long = 0
    
    companion object {
        // Hard Limits
        const val MAX_MEMORY_MB = 256L
        const val MAX_CPU_PERCENTAGE = 30
        const val MAX_THREADS = 10
        const val MAX_STORAGE_MB = 50L
        const val MAX_STORAGE_ENTRIES = 10000
        
        // Spike Detection (200% Anstieg)
        const val SPIKE_THRESHOLD = 2.0
        
        // Verdächtige Muster
        const val MAX_NETWORK_REQUESTS_PER_MINUTE = 100
        const val MAX_API_CALLS_PER_SECOND = 50
        
        // Baseline Learning
        const val BASELINE_DURATION_MS = 5 * 60 * 1000L // 5 Minuten
    }
    
    private val metricsHistory = mutableListOf<PluginMetrics>()
    
    suspend fun startMonitoring() {
        baselineStartTime = System.currentTimeMillis()
        
        // Monitoring Loop (jede Sekunde)
        while (true) {
            val metrics = collectMetrics()
            metricsHistory.add(metrics)
            
            // Baseline lernen (erste 5 Minuten)
            if (baseline == null && isBaselineLearningComplete()) {
                baseline = calculateBaseline()
                Timber.i("Baseline für Plugin $pluginId gelernt: $baseline")
            }
            
            // Alert-Checks
            checkAlerts(metrics)
            
            // Alte Metriken entfernen (nur letzte 10 Minuten behalten)
            if (metricsHistory.size > 600) {
                metricsHistory.removeAt(0)
            }
            
            delay(1000)
        }
    }
    
    private fun collectMetrics(): PluginMetrics {
        return PluginMetrics(
            timestamp = System.currentTimeMillis(),
            memoryMb = getMemoryUsage(),
            cpuPercentage = getCpuUsage(),
            threadCount = getThreadCount(),
            networkRequests = getNetworkRequestCount(),
            networkTrafficBytes = getNetworkTrafficBytes(),
            networkDomains = getAccessedDomains(),
            storageWrites = getStorageWriteCount(),
            storageReads = getStorageReadCount(),
            storageSizeMb = getStorageSizeUsage(),
            apiCallCount = getApiCallCount(),
            permissionRequests = getPermissionRequestCount()
        )
    }
    
    private fun isBaselineLearningComplete(): Boolean {
        return (System.currentTimeMillis() - baselineStartTime) >= BASELINE_DURATION_MS
    }
    
    private fun calculateBaseline(): PluginBaseline {
        val recentMetrics = metricsHistory.takeLast(300) // Letzte 5 Minuten
        
        return PluginBaseline(
            avgMemoryMb = recentMetrics.map { it.memoryMb }.average(),
            avgCpuPercentage = recentMetrics.map { it.cpuPercentage }.average(),
            avgThreadCount = recentMetrics.map { it.threadCount }.average(),
            avgNetworkRequestsPerMinute = recentMetrics.map { it.networkRequests }.average(),
            avgApiCallsPerSecond = recentMetrics.map { it.apiCallCount }.average()
        )
    }
    
    private suspend fun checkAlerts(current: PluginMetrics) {
        val alerts = mutableListOf<MonitoringAlert>()
        
        // 1. Hard Limit Checks
        if (current.memoryMb > MAX_MEMORY_MB) {
            alerts.add(MonitoringAlert(
                type = AlertType.MEMORY_LIMIT_EXCEEDED,
                severity = AlertSeverity.CRITICAL,
                message = "Memory limit exceeded: ${current.memoryMb}MB > ${MAX_MEMORY_MB}MB",
                action = AlertAction.STOP_PLUGIN
            ))
        }
        
        if (current.cpuPercentage > MAX_CPU_PERCENTAGE) {
            alerts.add(MonitoringAlert(
                type = AlertType.CPU_LIMIT_EXCEEDED,
                severity = AlertSeverity.HIGH,
                message = "CPU limit exceeded: ${current.cpuPercentage}% > ${MAX_CPU_PERCENTAGE}%",
                action = AlertAction.STOP_PLUGIN
            ))
        }
        
        if (current.threadCount > MAX_THREADS) {
            alerts.add(MonitoringAlert(
                type = AlertType.THREAD_LIMIT_EXCEEDED,
                severity = AlertSeverity.MEDIUM,
                message = "Thread limit exceeded: ${current.threadCount} > $MAX_THREADS",
                action = AlertAction.STOP_PLUGIN
            ))
        }
        
        if (current.storageSizeMb > MAX_STORAGE_MB) {
            alerts.add(MonitoringAlert(
                type = AlertType.STORAGE_SIZE_EXCEEDED,
                severity = AlertSeverity.HIGH,
                message = "Storage size exceeded: ${current.storageSizeMb}MB > ${MAX_STORAGE_MB}MB",
                action = AlertAction.STOP_PLUGIN
            ))
        }
        
        // 2. Spike Detection (wenn Baseline existiert)
        baseline?.let { base ->
            if (current.memoryMb > base.avgMemoryMb * SPIKE_THRESHOLD) {
                alerts.add(MonitoringAlert(
                    type = AlertType.MEMORY_SPIKE,
                    severity = AlertSeverity.MEDIUM,
                    message = "Memory spike detected: ${current.memoryMb}MB (baseline: ${base.avgMemoryMb}MB)",
                    action = AlertAction.PAUSE_PLUGIN
                ))
            }
            
            if (current.cpuPercentage > base.avgCpuPercentage * SPIKE_THRESHOLD) {
                alerts.add(MonitoringAlert(
                    type = AlertType.CPU_SPIKE,
                    severity = AlertSeverity.MEDIUM,
                    message = "CPU spike detected: ${current.cpuPercentage}% (baseline: ${base.avgCpuPercentage}%)",
                    action = AlertAction.PAUSE_PLUGIN
                ))
            }
        }
        
        // 3. Verdächtige Muster
        if (current.networkRequests > MAX_NETWORK_REQUESTS_PER_MINUTE) {
            alerts.add(MonitoringAlert(
                type = AlertType.SUSPICIOUS_NETWORK_PATTERN,
                severity = AlertSeverity.HIGH,
                message = "Suspicious network activity: ${current.networkRequests} requests/min",
                action = AlertAction.STOP_PLUGIN
            ))
        }
        
        if (current.apiCallCount > MAX_API_CALLS_PER_SECOND) {
            alerts.add(MonitoringAlert(
                type = AlertType.SUSPICIOUS_API_PATTERN,
                severity = AlertSeverity.MEDIUM,
                message = "High API call frequency: ${current.apiCallCount} calls/sec",
                action = AlertAction.PAUSE_PLUGIN
            ))
        }
        
        // 4. Alerts verarbeiten
        if (alerts.isNotEmpty()) {
            handleAlerts(alerts, current)
        }
    }
    
    private suspend fun handleAlerts(alerts: List<MonitoringAlert>, metrics: PluginMetrics) {
        alerts.forEach { alert ->
            // 1. Logging
            Timber.w("Plugin $pluginId: ${alert.message}")
            saveAlertToDatabase(alert, metrics)
            
            // 2. Action ausführen
            when (alert.action) {
                AlertAction.STOP_PLUGIN -> stopPlugin()
                AlertAction.PAUSE_PLUGIN -> pausePlugin()
                AlertAction.LOG_ONLY -> { /* Nur loggen */ }
            }
        }
    }
    
    private suspend fun saveAlertToDatabase(alert: MonitoringAlert, metrics: PluginMetrics) {
        try {
            database.pluginAlertDao().insert(
                PluginAlertEntity(
                    pluginId = pluginId,
                    timestamp = System.currentTimeMillis(),
                    alertType = alert.type.name,
                    severity = alert.severity.name,
                    message = alert.message,
                    metricsSnapshot = metrics.toString() // JSON serialization would be better
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save alert to database")
        }
    }
    
    private fun getMemoryUsage(): Long {
        return (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    }
    
    private fun getCpuUsage(): Int {
        // CPU-Usage über /proc/stat berechnen
        return 0 // Placeholder
    }
    
    private fun getThreadCount(): Int {
        return Thread.getAllStackTraces().keys.count { 
            it.name.startsWith("Plugin-$pluginId") 
        }
    }
    
    private fun getNetworkRequestCount(): Int = 0 // Placeholder
    private fun getNetworkTrafficBytes(): Long = 0 // Placeholder
    private fun getAccessedDomains(): Set<String> = emptySet() // Placeholder
    private fun getStorageWriteCount(): Int = 0 // Placeholder
    private fun getStorageReadCount(): Int = 0 // Placeholder
    private fun getStorageSizeUsage(): Long = 0 // Placeholder
    private fun getApiCallCount(): Int = 0 // Placeholder
    private fun getPermissionRequestCount(): Int = 0 // Placeholder
    
    private fun stopPlugin() {
        // Implementation to stop plugin
    }
    
    private fun pausePlugin() {
        // Implementation to pause plugin
    }
}

data class PluginMetrics(
    val timestamp: Long,
    val memoryMb: Long,
    val cpuPercentage: Int,
    val threadCount: Int,
    val networkRequests: Int,
    val networkTrafficBytes: Long,
    val networkDomains: Set<String>,
    val storageWrites: Int,
    val storageReads: Int,
    val storageSizeMb: Long,
    val apiCallCount: Int,
    val permissionRequests: Int
)

data class PluginBaseline(
    val avgMemoryMb: Double,
    val avgCpuPercentage: Double,
    val avgThreadCount: Double,
    val avgNetworkRequestsPerMinute: Double,
    val avgApiCallsPerSecond: Double
)

data class MonitoringAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val action: AlertAction
)

enum class AlertType {
    MEMORY_LIMIT_EXCEEDED,
    CPU_LIMIT_EXCEEDED,
    THREAD_LIMIT_EXCEEDED,
    STORAGE_SIZE_EXCEEDED,
    MEMORY_SPIKE,
    CPU_SPIKE,
    SUSPICIOUS_NETWORK_PATTERN,
    SUSPICIOUS_API_PATTERN
}

enum class AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }

enum class AlertAction {
    LOG_ONLY,
    PAUSE_PLUGIN,
    STOP_PLUGIN
}
