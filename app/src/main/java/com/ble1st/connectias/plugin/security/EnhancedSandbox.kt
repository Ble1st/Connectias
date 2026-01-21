package com.ble1st.connectias.plugin.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced sandbox with integrated security features
 * Combines all Phase 5 security components
 */
@Singleton
class EnhancedSandbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resourceLimiter: PluginResourceLimiter,
    private val networkPolicy: PluginNetworkPolicy,
    private val permissionMonitor: PluginPermissionMonitor,
    private val zeroTrustVerifier: ZeroTrustVerifier,
    private val behaviorAnalyzer: PluginBehaviorAnalyzer,
    private val anomalyDetector: AnomalyDetector
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pluginMonitoringJobs = ConcurrentHashMap<String, Job>()
    
    // Callback for critical anomalies that require plugin termination
    private var onCriticalAnomalyCallback: ((pluginId: String, reason: String) -> Unit)? = null
    
    /**
     * Set callback for critical anomalies that require plugin termination
     * This should be called by PluginManagerSandbox to handle plugin disabling
     */
    fun setOnCriticalAnomalyCallback(callback: (pluginId: String, reason: String) -> Unit) {
        onCriticalAnomalyCallback = callback
    }
    
    /**
     * Initialize enhanced sandbox for a plugin
     */
    suspend fun initializePlugin(pluginId: String, pid: Int, uid: Int) {
        Timber.i("Initializing enhanced sandbox for plugin $pluginId (PID: $pid, UID: $uid)")
        
        // Set default resource limits
        setResourceLimits(pluginId, PluginResourceLimiter.ResourceLimits())
        
        // Configure default network policy
        configureNetworkAccess(pluginId, PluginNetworkPolicy.NetworkPolicy())
        
        // Register plugin UID for network tracking
        networkPolicy.registerPluginUid(pluginId, uid)
        
        // Establish behavior baseline
        behaviorAnalyzer.establishBaseline(pluginId)
        
        // Verify plugin
        val verificationResult = zeroTrustVerifier.verifyOnExecution(pluginId)
        if (verificationResult is ZeroTrustVerifier.VerificationResult.Failed) {
            Timber.e("Plugin $pluginId failed verification: ${verificationResult.reason}")
            throw SecurityException("Plugin verification failed: ${verificationResult.reason}")
        }
        
        // Start monitoring
        startMonitoring(pluginId, pid)
        
        Timber.i("Enhanced sandbox initialized for plugin $pluginId")
    }
    
    /**
     * Set resource limits for a plugin
     */
    fun setResourceLimits(pluginId: String, limits: PluginResourceLimiter.ResourceLimits) {
        resourceLimiter.setResourceLimits(pluginId, limits)
        Timber.d("Resource limits set for $pluginId")
    }
    
    /**
     * Configure network access policy
     */
    fun configureNetworkAccess(pluginId: String, policy: PluginNetworkPolicy.NetworkPolicy) {
        networkPolicy.configureNetworkAccess(pluginId, policy)
        Timber.d("Network policy configured for $pluginId")
    }
    
    /**
     * Monitor permission usage
     */
    fun monitorPermissionUsage(pluginId: String): Flow<PluginPermissionMonitor.PermissionUsageEvent> {
        return permissionMonitor.permissionEvents
    }
    
    /**
     * Start continuous monitoring with proper job tracking for cleanup
     */
    private fun startMonitoring(pluginId: String, pid: Int) {
        // Cancel any existing monitoring job for this plugin
        pluginMonitoringJobs[pluginId]?.cancel()
        
        val monitoringJob = scope.launch {
            // Monitor resource usage
            launch {
                while (true) {
                    try {
                        resourceLimiter.monitorResourceUsage(pluginId, pid)
                        val limits = resourceLimiter.getResourceLimits(pluginId)
                        resourceLimiter.enforceMemoryLimits(pluginId, pid, limits)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e // Propagate cancellation
                    } catch (e: Exception) {
                        Timber.w(e, "Resource monitoring error for $pluginId")
                    }
                    kotlinx.coroutines.delay(5000) // Every 5 seconds
                }
            }
            
            // Monitor network usage
            launch {
                while (true) {
                    try {
                        networkPolicy.monitorNetworkUsage(pluginId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e // Propagate cancellation
                    } catch (e: Exception) {
                        Timber.w(e, "Network monitoring error for $pluginId")
                    }
                    kotlinx.coroutines.delay(10000) // Every 10 seconds
                }
            }
            
            // Analyze behavior
            launch {
                behaviorAnalyzer.anomalies.collect { anomaly ->
                    if (anomaly.pluginId == pluginId) {
                        handleAnomaly(anomaly)
                    }
                }
            }
        }
        
        pluginMonitoringJobs[pluginId] = monitoringJob
        Timber.d("Started monitoring for plugin: $pluginId")
    }
    
    /**
     * Stop monitoring for a specific plugin
     */
    private fun stopMonitoring(pluginId: String) {
        pluginMonitoringJobs.remove(pluginId)?.cancel()
        Timber.d("Stopped monitoring for plugin: $pluginId")
    }
    
    /**
     * Handle detected anomaly
     */
    private fun handleAnomaly(anomaly: PluginBehaviorAnalyzer.Anomaly) {
        Timber.w("Anomaly detected for ${anomaly.pluginId}: ${anomaly.description} (${anomaly.severity})")
        
        when (anomaly.severity) {
            PluginBehaviorAnalyzer.Severity.CRITICAL -> {
                // Take immediate action - disable plugin via callback
                Timber.e("CRITICAL anomaly detected - Disabling plugin ${anomaly.pluginId}")
                val reason = "Critical security anomaly: ${anomaly.description}"
                onCriticalAnomalyCallback?.invoke(anomaly.pluginId, reason)
                    ?: Timber.e("No critical anomaly callback registered - cannot disable plugin ${anomaly.pluginId}")
            }
            PluginBehaviorAnalyzer.Severity.HIGH -> {
                // Alert and increase monitoring
                Timber.w("HIGH severity anomaly - Increased monitoring for ${anomaly.pluginId}")
            }
            PluginBehaviorAnalyzer.Severity.MEDIUM,
            PluginBehaviorAnalyzer.Severity.LOW -> {
                // Log for analysis
                Timber.i("Anomaly logged for analysis: ${anomaly.description}")
            }
        }
    }
    
    /**
     * Verify plugin before execution
     */
    suspend fun verifyPluginExecution(pluginId: String): Boolean {
        val result = zeroTrustVerifier.verifyOnExecution(pluginId)
        
        return when (result) {
            is ZeroTrustVerifier.VerificationResult.Success -> {
                Timber.d("Plugin $pluginId verified successfully")
                true
            }
            is ZeroTrustVerifier.VerificationResult.Suspicious -> {
                Timber.w("Plugin $pluginId has suspicious indicators: ${result.warnings}")
                true // Allow but monitor closely
            }
            is ZeroTrustVerifier.VerificationResult.Failed -> {
                Timber.e("Plugin $pluginId verification failed: ${result.reason}")
                false
            }
        }
    }
    
    /**
     * Check if domain access is allowed
     */
    fun isDomainAllowed(pluginId: String, domain: String): Boolean {
        return networkPolicy.isDomainAllowed(pluginId, domain)
    }
    
    /**
     * Check if port access is allowed
     */
    fun isPortAllowed(pluginId: String, port: Int): Boolean {
        return networkPolicy.isPortAllowed(pluginId, port)
    }
    
    /**
     * Track permission usage
     */
    suspend fun trackPermissionUsage(pluginId: String, permission: String, granted: Boolean) {
        permissionMonitor.trackPermissionUsage(pluginId, permission, granted)
    }
    
    /**
     * Cleanup resources for a plugin
     */
    fun cleanupPlugin(pluginId: String) {
        // Stop monitoring coroutines first
        stopMonitoring(pluginId)
        
        resourceLimiter.clearPluginResources(pluginId)
        networkPolicy.clearPluginPolicy(pluginId)
        permissionMonitor.clearPluginHistory(pluginId)
        behaviorAnalyzer.clearBaseline(pluginId)
        zeroTrustVerifier.clearCache()
        
        Timber.i("Enhanced sandbox cleaned up for plugin $pluginId")
    }
    
    /**
     * Get security status for a plugin
     */
    fun getSecurityStatus(pluginId: String): SecurityStatus {
        val exceedingLimits = resourceLimiter.getPluginsExceedingLimits()
        val exceedingBandwidth = networkPolicy.getPluginsExceedingBandwidth()
        val suspiciousPatterns = permissionMonitor.getSuspiciousPatterns(pluginId)
        
        return when {
            exceedingLimits.contains(pluginId) -> {
                SecurityStatus.Warning("Resource limits exceeded")
            }
            exceedingBandwidth.contains(pluginId) -> {
                SecurityStatus.Warning("Bandwidth limits exceeded")
            }
            suspiciousPatterns.isNotEmpty() -> {
                SecurityStatus.Critical("Suspicious behavior: ${suspiciousPatterns.first()}")
            }
            else -> {
                SecurityStatus.Secure()
            }
        }
    }
    
    data class SecurityStatus(
        val status: Status,
        val message: String = ""
    ) {
        enum class Status {
            SECURE, WARNING, CRITICAL
        }
        
        companion object {
            fun Secure(message: String = "All checks passed") = SecurityStatus(Status.SECURE, message)
            fun Warning(message: String) = SecurityStatus(Status.WARNING, message)
            fun Critical(message: String) = SecurityStatus(Status.CRITICAL, message)
        }
    }
}
