package com.ble1st.connectias.plugin.security

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central Security Audit Manager
 * Collects, stores, and analyzes all security events across the plugin system
 */
@Singleton
class SecurityAuditManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // Check if we're running in the isolated sandbox process
    private val isIsolatedSandboxProcess: Boolean by lazy {
        val processName = getCurrentProcessName()
        processName.contains(":plugin_sandbox")
    }
    
    private fun getCurrentProcessName(): String {
        return try {
            android.app.Application.getProcessName()
        } catch (_: Exception) {
            ""
        }
    }
    
    // ════════════════════════════════════════════════════════
    // EVENT TYPES
    // ════════════════════════════════════════════════════════
    
    @Serializable
    data class SecurityAuditEvent(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val eventType: SecurityEventType,
        val severity: SecuritySeverity,
        val source: String, // Component that generated the event
        val pluginId: String?,
        val message: String,
        val details: Map<String, String> = emptyMap(),
        val stackTrace: String? = null,
        val deviceInfo: DeviceInfo = DeviceInfo(),
        val sessionId: String = ""
    )
    
    @Serializable
    enum class SecurityEventType {
        // Authentication & Authorization
        PLUGIN_IDENTITY_SPOOFING,
        PERMISSION_VIOLATION,
        UNAUTHORIZED_ACCESS,
        
        // Network Security
        NETWORK_POLICY_VIOLATION,
        SUSPICIOUS_NETWORK_REQUEST,
        BLOCKED_DOMAIN_ACCESS,
        BANDWIDTH_VIOLATION,
        
        // Resource Security
        RESOURCE_LIMIT_EXCEEDED,
        CPU_THROTTLING_APPLIED,
        MEMORY_EMERGENCY_KILL,
        DISK_USAGE_VIOLATION,
        
        // Plugin Security
        PLUGIN_VERIFICATION_FAILED,
        MALICIOUS_PLUGIN_DETECTED,
        PLUGIN_INTEGRITY_VIOLATION,
        PLUGIN_SIGNATURE_INVALID,
        
        // Bridge Security
        HARDWARE_BRIDGE_ABUSE,
        API_RATE_LIMITING,
        BRIDGE_PERMISSION_DENIED,
        
        // System Security
        ROOT_DETECTION_TRIGGERED,
        DEBUGGER_DETECTION_TRIGGERED,
        TAMPER_DETECTION_TRIGGERED,
        EMULATOR_DETECTION_TRIGGERED,
        
        // Data Security
        DATA_EXFILTRATION_ATTEMPT,
        SENSITIVE_DATA_ACCESS,
        ENCRYPTION_FAILURE,
        
        // General Security
        SECURITY_CONFIGURATION_CHANGE,
        AUDIT_LOG_TAMPERING,
        SUSPICIOUS_BEHAVIOR_DETECTED
    }
    
    @Serializable
    enum class SecuritySeverity {
        INFO,     // General information
        LOW,      // Minor security concern
        MEDIUM,   // Moderate security issue
        HIGH,     // Serious security threat
        CRITICAL  // Immediate security emergency
    }
    
    @Serializable
    data class DeviceInfo(
        val deviceId: String = Build.ID,
        val manufacturer: String = Build.MANUFACTURER,
        val model: String = Build.MODEL,
        val androidVersion: String = Build.VERSION.RELEASE,
        val securityPatchLevel: String = Build.VERSION.SECURITY_PATCH,
        val appVersion: String = "1.0.0" // Would be injected from BuildConfig
    )
    
    // ════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ════════════════════════════════════════════════════════
    
    private val eventQueue = ConcurrentLinkedQueue<SecurityAuditEvent>()
    private val eventCounter = AtomicLong(0)
    private val sessionId = UUID.randomUUID().toString()
    
    private val _recentEvents = MutableStateFlow<List<SecurityAuditEvent>>(emptyList())
    val recentEvents: Flow<List<SecurityAuditEvent>> = _recentEvents.asStateFlow()
    
    private val _eventStream = MutableSharedFlow<SecurityAuditEvent>(
        replay = 0,
        extraBufferCapacity = 256
    )
    /**
     * Stream of newly logged security events (main process only).
     *
     * Intended for analytics collectors; events are emitted best-effort.
     */
    val eventStream: Flow<SecurityAuditEvent> = _eventStream.asSharedFlow()

    private val _securityStats = MutableStateFlow(SecurityStatistics())
    val securityStats: Flow<SecurityStatistics> = _securityStats.asStateFlow()
    
    private val auditScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    private val auditLogFile: File by lazy {
        // In sandbox process, we can't access files, so return a dummy file
        if (isIsolatedSandboxProcess) {
            File("/dev/null")
        } else {
            val logDir = File(context.filesDir, "security")
            logDir.mkdirs() // Ensure directory exists
            File(logDir, "security_audit.jsonl").also { 
                try {
                    if (!it.exists()) {
                        it.createNewFile()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SECURITY AUDIT] Failed to create audit log file")
                }
            }
        }
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    companion object {
        private const val MAX_RECENT_EVENTS = 100
        private const val MAX_LOG_FILE_SIZE_MB = 10
        private const val LOG_ROTATION_CHECK_INTERVAL_MS = 60000L
    }
    
    @Serializable
    data class SecurityStatistics(
        val totalEvents: Long = 0,
        val criticalEvents: Long = 0,
        val highSeverityEvents: Long = 0,
        val pluginViolations: Long = 0,
        val networkViolations: Long = 0,
        val resourceViolations: Long = 0,
        val spoofingAttempts: Long = 0,
        val lastEventTimestamp: Long = 0,
        val sessionStartTime: Long = System.currentTimeMillis()
    )
    
    init {
        // Only start audit processing if not in sandbox
        if (!isIsolatedSandboxProcess) {
            startAuditProcessing()
            logSystemStartup()
        } else {
            Timber.d("[SECURITY AUDIT] Running in isolated sandbox - audit processing disabled")
        }
    }
    
    private fun getCurrentSessionId(): String = sessionId
    
    // ════════════════════════════════════════════════════════
    // EVENT LOGGING
    // ════════════════════════════════════════════════════════
    
    /**
     * Log a security event
     */
    fun logSecurityEvent(
        eventType: SecurityEventType,
        severity: SecuritySeverity,
        source: String,
        pluginId: String? = null,
        message: String,
        details: Map<String, String> = emptyMap(),
        exception: Throwable? = null
    ) {
        // Skip event processing in sandbox process
        if (isIsolatedSandboxProcess) {
            // Only log to Timber, don't queue events
            when (severity) {
                SecuritySeverity.CRITICAL -> Timber.e("[SECURITY AUDIT] CRITICAL: $message")
                SecuritySeverity.HIGH -> Timber.w("[SECURITY AUDIT] HIGH: $message")
                SecuritySeverity.MEDIUM -> Timber.i("[SECURITY AUDIT] MEDIUM: $message")
                SecuritySeverity.LOW -> Timber.d("[SECURITY AUDIT] LOW: $message")
                SecuritySeverity.INFO -> Timber.v("[SECURITY AUDIT] INFO: $message")
            }
            return
        }
        
        val event = SecurityAuditEvent(
            eventType = eventType,
            severity = severity,
            source = source,
            pluginId = pluginId,
            message = message,
            details = details,
            stackTrace = exception?.stackTraceToString()
        )
        
        eventQueue.offer(event)
        eventCounter.incrementAndGet()

        // Best-effort emit for analytics collectors.
        _eventStream.tryEmit(event)
        
        // Log to Timber based on severity
        when (severity) {
            SecuritySeverity.CRITICAL -> Timber.e("[SECURITY AUDIT] CRITICAL: $message")
            SecuritySeverity.HIGH -> Timber.w("[SECURITY AUDIT] HIGH: $message")
            SecuritySeverity.MEDIUM -> Timber.i("[SECURITY AUDIT] MEDIUM: $message")
            SecuritySeverity.LOW -> Timber.d("[SECURITY AUDIT] LOW: $message")
            SecuritySeverity.INFO -> Timber.v("[SECURITY AUDIT] INFO: $message")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // CONVENIENCE METHODS FOR DIFFERENT EVENT TYPES
    // ════════════════════════════════════════════════════════
    
    fun logPluginSpoofingAttempt(pluginId: String, claimedId: String, actualId: String, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.PLUGIN_IDENTITY_SPOOFING,
            severity = SecuritySeverity.HIGH,
            source = source,
            pluginId = pluginId,
            message = "Plugin identity spoofing detected: claimed '$claimedId' but verified as '$actualId'",
            details = mapOf(
                "claimed_plugin_id" to claimedId,
                "actual_plugin_id" to actualId,
                "detection_method" to "identity_verification"
            )
        )
    }
    
    fun logNetworkPolicyViolation(pluginId: String, url: String, reason: String, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.NETWORK_POLICY_VIOLATION,
            severity = SecuritySeverity.MEDIUM,
            source = source,
            pluginId = pluginId,
            message = "Network policy violation: $reason",
            details = mapOf(
                "blocked_url" to url,
                "violation_reason" to reason,
                "policy_type" to "domain_blocking"
            )
        )
    }
    
    fun logResourceLimitViolation(pluginId: String, resourceType: String, usage: String, limit: String, action: String, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.RESOURCE_LIMIT_EXCEEDED,
            severity = if (action == "killed") SecuritySeverity.HIGH else SecuritySeverity.MEDIUM,
            source = source,
            pluginId = pluginId,
            message = "Resource limit exceeded: $resourceType usage $usage > limit $limit",
            details = mapOf(
                "resource_type" to resourceType,
                "current_usage" to usage,
                "configured_limit" to limit,
                "enforcement_action" to action
            )
        )
    }
    
    fun logPluginVerificationFailure(pluginId: String, reason: String, source: String, exception: Throwable? = null) {
        logSecurityEvent(
            eventType = SecurityEventType.PLUGIN_VERIFICATION_FAILED,
            severity = SecuritySeverity.HIGH,
            source = source,
            pluginId = pluginId,
            message = "Plugin verification failed: $reason",
            details = mapOf(
                "verification_failure" to reason,
                "verification_method" to "zero_trust"
            ),
            exception = exception
        )
    }
    
    fun logMaliciousPluginDetected(pluginId: String, threats: List<String>, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.MALICIOUS_PLUGIN_DETECTED,
            severity = SecuritySeverity.CRITICAL,
            source = source,
            pluginId = pluginId,
            message = "Malicious plugin detected with threats: ${threats.joinToString(", ")}",
            details = mapOf(
                "detected_threats" to threats.joinToString(","),
                "threat_count" to threats.size.toString(),
                "detection_confidence" to "high"
            )
        )
    }
    
    fun logPermissionViolation(pluginId: String, permission: String, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.PERMISSION_VIOLATION,
            severity = SecuritySeverity.MEDIUM,
            source = source,
            pluginId = pluginId,
            message = "Permission violation: plugin attempted to use $permission without authorization",
            details = mapOf(
                "denied_permission" to permission,
                "permission_type" to "android_permission"
            )
        )
    }
    
    fun logRootDetection(detectionMethods: List<String>, source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.ROOT_DETECTION_TRIGGERED,
            severity = SecuritySeverity.HIGH,
            source = source,
            pluginId = null,
            message = "Root detection triggered: ${detectionMethods.joinToString(", ")}",
            details = mapOf(
                "detection_methods" to detectionMethods.joinToString(","),
                "method_count" to detectionMethods.size.toString()
            )
        )
    }
    
    fun logDebuggerDetection(source: String) {
        logSecurityEvent(
            eventType = SecurityEventType.DEBUGGER_DETECTION_TRIGGERED,
            severity = SecuritySeverity.HIGH,
            source = source,
            pluginId = null,
            message = "Debugger attachment detected",
            details = mapOf(
                "detection_method" to "native_debugger_check"
            )
        )
    }
    
    // ════════════════════════════════════════════════════════
    // EVENT PROCESSING
    // ════════════════════════════════════════════════════════
    
    private fun startAuditProcessing() {
        // Event processing loop
        auditScope.launch {
            while (true) {
                try {
                    processEventQueue()
                    delay(1000) // Process events every second
                } catch (e: Exception) {
                    Timber.e(e, "[SECURITY AUDIT] Error processing event queue")
                    delay(5000) // Wait longer on error
                }
            }
        }
        
        // Log rotation check
        auditScope.launch {
            while (true) {
                try {
                    checkLogRotation()
                    delay(LOG_ROTATION_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "[SECURITY AUDIT] Error checking log rotation")
                    delay(30000)
                }
            }
        }
        
        // Statistics update
        auditScope.launch {
            while (true) {
                try {
                    updateSecurityStatistics()
                    delay(5000) // Update stats every 5 seconds
                } catch (e: Exception) {
                    Timber.e(e, "[SECURITY AUDIT] Error updating statistics")
                    delay(10000)
                }
            }
        }
    }
    
    private suspend fun processEventQueue() {
        val events = mutableListOf<SecurityAuditEvent>()
        
        // Drain the queue
        while (eventQueue.isNotEmpty()) {
            eventQueue.poll()?.let { events.add(it) }
        }
        
        if (events.isNotEmpty()) {
            // Write to file
            writeEventsToFile(events)
            
            // Update recent events
            val recentList = (_recentEvents.value + events).takeLast(MAX_RECENT_EVENTS)
            _recentEvents.value = recentList
            
            // Handle critical events immediately
            events.filter { it.severity == SecuritySeverity.CRITICAL }
                .forEach { handleCriticalEvent(it) }
        }
    }
    
    private suspend fun writeEventsToFile(events: List<SecurityAuditEvent>) {
        // Skip file operations in sandbox process
        if (isIsolatedSandboxProcess) {
            Timber.d("[SECURITY AUDIT] Skipping file write in isolated sandbox process")
            return
        }
        
        try {
            // Ensure file and directory exist
            auditLogFile.parentFile?.mkdirs()
            if (!auditLogFile.exists()) {
                auditLogFile.createNewFile()
            }
            
            auditLogFile.appendText(
                events.joinToString("\n") { json.encodeToString(it) } + "\n"
            )
        } catch (e: Exception) {
            Timber.e(e, "[SECURITY AUDIT] Failed to write events to file: ${auditLogFile.absolutePath}")
            // Try to create the file in a different location as fallback
            try {
                val fallbackFile = File(context.cacheDir, "security_audit_fallback.jsonl")
                // Ensure parent directory exists
                fallbackFile.parentFile?.mkdirs()
                if (!fallbackFile.exists()) {
                    fallbackFile.createNewFile()
                }
                fallbackFile.appendText(
                    events.joinToString("\n") { json.encodeToString(it) } + "\n"
                )
                Timber.w("[SECURITY AUDIT] Using fallback file: ${fallbackFile.absolutePath}")
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "[SECURITY AUDIT] Fallback file write also failed")
            }
        }
    }
    
    private fun handleCriticalEvent(event: SecurityAuditEvent) {
        // For critical events, we might want to:
        // 1. Send immediate notifications
        // 2. Trigger emergency responses
        // 3. Alert administrators
        
        Timber.e("[SECURITY AUDIT] CRITICAL EVENT: ${event.message}")
        
        // Example: If malicious plugin detected, immediately disable all plugins
        if (event.eventType == SecurityEventType.MALICIOUS_PLUGIN_DETECTED) {
            // Would trigger emergency plugin shutdown
            Timber.e("[SECURITY AUDIT] EMERGENCY: Malicious plugin detected - triggering emergency response")
        }
    }
    
    private suspend fun updateSecurityStatistics() {
        val events = _recentEvents.value
        
        val stats = SecurityStatistics(
            totalEvents = eventCounter.get(),
            criticalEvents = events.count { it.severity == SecuritySeverity.CRITICAL }.toLong(),
            highSeverityEvents = events.count { it.severity == SecuritySeverity.HIGH }.toLong(),
            pluginViolations = events.count { 
                it.eventType in listOf(
                    SecurityEventType.PLUGIN_IDENTITY_SPOOFING,
                    SecurityEventType.PLUGIN_VERIFICATION_FAILED,
                    SecurityEventType.MALICIOUS_PLUGIN_DETECTED
                )
            }.toLong(),
            networkViolations = events.count {
                it.eventType in listOf(
                    SecurityEventType.NETWORK_POLICY_VIOLATION,
                    SecurityEventType.SUSPICIOUS_NETWORK_REQUEST,
                    SecurityEventType.BLOCKED_DOMAIN_ACCESS
                )
            }.toLong(),
            resourceViolations = events.count {
                it.eventType in listOf(
                    SecurityEventType.RESOURCE_LIMIT_EXCEEDED,
                    SecurityEventType.CPU_THROTTLING_APPLIED,
                    SecurityEventType.MEMORY_EMERGENCY_KILL
                )
            }.toLong(),
            spoofingAttempts = events.count { 
                it.eventType == SecurityEventType.PLUGIN_IDENTITY_SPOOFING 
            }.toLong(),
            lastEventTimestamp = events.maxOfOrNull { it.timestamp } ?: 0L
        )
        
        _securityStats.value = stats
    }
    
    private suspend fun checkLogRotation() {
        // Skip log rotation in sandbox process
        if (isIsolatedSandboxProcess) {
            return
        }
        
        try {
            if (auditLogFile.exists() && auditLogFile.length() > MAX_LOG_FILE_SIZE_MB * 1024 * 1024) {
                // Rotate log file
                val rotatedFile = File(auditLogFile.parent, "${auditLogFile.nameWithoutExtension}_${System.currentTimeMillis()}.jsonl")
                auditLogFile.renameTo(rotatedFile)
                
                // Compress old log (simplified - would use proper compression)
                Timber.i("[SECURITY AUDIT] Log file rotated: ${rotatedFile.name}")
                
                logSecurityEvent(
                    eventType = SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
                    severity = SecuritySeverity.INFO,
                    source = "SecurityAuditManager",
                    message = "Audit log rotated due to size limit",
                    details = mapOf(
                        "old_file_size_mb" to (rotatedFile.length() / 1024 / 1024).toString(),
                        "rotation_reason" to "size_limit_exceeded"
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[SECURITY AUDIT] Failed to rotate log file")
        }
    }
    
    private fun logSystemStartup() {
        logSecurityEvent(
            eventType = SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
            severity = SecuritySeverity.INFO,
            source = "SecurityAuditManager",
            message = "Security audit system initialized",
            details = mapOf(
                "session_id" to sessionId,
                "device_model" to Build.MODEL,
                "android_version" to Build.VERSION.RELEASE,
                "security_patch" to Build.VERSION.SECURITY_PATCH
            )
        )
    }
    
    // ════════════════════════════════════════════════════════
    // QUERY METHODS
    // ════════════════════════════════════════════════════════
    
    /**
     * Get events for a specific plugin
     */
    fun getEventsForPlugin(pluginId: String): List<SecurityAuditEvent> {
        return _recentEvents.value.filter { it.pluginId == pluginId }
    }
    
    /**
     * Get events by severity
     */
    fun getEventsBySeverity(severity: SecuritySeverity): List<SecurityAuditEvent> {
        return _recentEvents.value.filter { it.severity == severity }
    }
    
    /**
     * Get events by type
     */
    fun getEventsByType(eventType: SecurityEventType): List<SecurityAuditEvent> {
        return _recentEvents.value.filter { it.eventType == eventType }
    }
    
    /**
     * Get events within time range
     */
    fun getEventsInTimeRange(startTime: Long, endTime: Long): List<SecurityAuditEvent> {
        return _recentEvents.value.filter { it.timestamp in startTime..endTime }
    }
    
    /**
     * Get security summary for plugin
     */
    fun getPluginSecuritySummary(pluginId: String): PluginSecuritySummary {
        val events = getEventsForPlugin(pluginId)
        return PluginSecuritySummary(
            pluginId = pluginId,
            totalViolations = events.size,
            criticalViolations = events.count { it.severity == SecuritySeverity.CRITICAL },
            highViolations = events.count { it.severity == SecuritySeverity.HIGH },
            spoofingAttempts = events.count { it.eventType == SecurityEventType.PLUGIN_IDENTITY_SPOOFING },
            networkViolations = events.count { it.eventType == SecurityEventType.NETWORK_POLICY_VIOLATION },
            resourceViolations = events.count { it.eventType == SecurityEventType.RESOURCE_LIMIT_EXCEEDED },
            lastViolation = events.maxByOrNull { it.timestamp }?.timestamp ?: 0L,
            riskLevel = calculateRiskLevel(events)
        )
    }
    
    data class PluginSecuritySummary(
        val pluginId: String,
        val totalViolations: Int,
        val criticalViolations: Int,
        val highViolations: Int,
        val spoofingAttempts: Int,
        val networkViolations: Int,
        val resourceViolations: Int,
        val lastViolation: Long,
        val riskLevel: RiskLevel
    )
    
    enum class RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    private fun calculateRiskLevel(events: List<SecurityAuditEvent>): RiskLevel {
        val critical = events.count { it.severity == SecuritySeverity.CRITICAL }
        val high = events.count { it.severity == SecuritySeverity.HIGH }
        val spoofing = events.count { it.eventType == SecurityEventType.PLUGIN_IDENTITY_SPOOFING }
        
        return when {
            critical > 0 || spoofing > 2 -> RiskLevel.CRITICAL
            high > 3 || spoofing > 0 -> RiskLevel.HIGH
            events.size > 10 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
    
    // ════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════
    
    fun shutdown() {
        if (!isIsolatedSandboxProcess) {
            auditScope.cancel()
            
            // Process remaining events
            runBlocking {
                processEventQueue()
            }
            
            logSecurityEvent(
                eventType = SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
                severity = SecuritySeverity.INFO,
                source = "SecurityAuditManager",
                message = "Security audit system shutdown"
            )
        } else {
            Timber.d("[SECURITY AUDIT] Shutdown called in sandbox - no action needed")
        }
    }
}
