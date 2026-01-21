package com.ble1st.connectias.plugin.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * Plugin Data Leakage Protector for Main Process UI Security
 * 
 * Monitors and prevents sensitive data leakage from plugin UI fragments
 * running in the main process.
 * 
 * SECURITY: Plugins in Main Process can access sensitive app data
 */
object PluginDataLeakageProtector {
    
    // Sensitive data patterns
    private val SENSITIVE_PATTERNS = mapOf(
        "EMAIL" to Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
        "PHONE" to Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"),
        "CREDIT_CARD" to Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b"),
        "SSN" to Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        "API_KEY" to Pattern.compile("(?i)(api[_-]?key|token|secret)[\"'\\s]*[:=][\"'\\s]*([a-zA-Z0-9]{20,})"),
        "PASSWORD" to Pattern.compile("(?i)(password|passwd|pwd)[\"'\\s]*[:=][\"'\\s]*([^\\s\"']{8,})")
    )
    
    // Plugin data access tracking (thread-safe)
    private val pluginDataAccess = ConcurrentHashMap<String, CopyOnWriteArrayList<DataAccessEvent>>()
    private val pluginClipboardAccess = ConcurrentHashMap<String, AtomicLong>()
    
    // Thresholds
    private const val MAX_CLIPBOARD_ACCESS_PER_MINUTE = 5
    private const val SUSPICIOUS_DATA_ACCESS_THRESHOLD = 10
    
    data class DataAccessEvent(
        val timestamp: Long,
        val dataType: String,
        val operation: String,
        val dataPattern: String? = null,
        val suspicious: Boolean = false
    )
    
    /**
     * Registers a plugin for data leakage monitoring
     */
    fun registerPlugin(pluginId: String) {
        pluginDataAccess.putIfAbsent(pluginId, CopyOnWriteArrayList())
        pluginClipboardAccess.putIfAbsent(pluginId, AtomicLong(0))
        Timber.d("[DATA PROTECTOR] Plugin registered for monitoring: $pluginId")
    }
    
    /**
     * Unregisters a plugin and clears monitoring data
     */
    fun unregisterPlugin(pluginId: String) {
        val events = pluginDataAccess.remove(pluginId)
        pluginClipboardAccess.remove(pluginId)
        
        if (events != null && events.isNotEmpty()) {
            val suspiciousEvents = events.count { it.suspicious }
            if (suspiciousEvents > 0) {
                Timber.w("[DATA PROTECTOR] Plugin $pluginId had $suspiciousEvents suspicious data access events")
            }
        }
        
        Timber.i("[DATA PROTECTOR] Plugin unregistered: $pluginId")
    }
    
    /**
     * Monitors clipboard access by plugin
     */
    fun monitorClipboardAccess(pluginId: String, context: Context): Boolean {
        val currentMinute = System.currentTimeMillis() / (60 * 1000)
        val accessCount = pluginClipboardAccess[pluginId]?.incrementAndGet() ?: 0
        
        // Reset counter every minute (simple implementation)
        if (accessCount == 1L) {
            Handler(Looper.getMainLooper()).postDelayed({
                pluginClipboardAccess[pluginId]?.set(0)
            }, 60000)
        }
        
        val allowed = accessCount <= MAX_CLIPBOARD_ACCESS_PER_MINUTE
        
        if (!allowed) {
            Timber.w("[DATA PROTECTOR] BLOCKED: Plugin $pluginId exceeded clipboard access limit: $accessCount")
            logDataAccessEvent(
                pluginId, 
                DataAccessEvent(
                    System.currentTimeMillis(),
                    "CLIPBOARD",
                    "READ_BLOCKED", 
                    null,
                    true
                )
            )
        } else {
            logDataAccessEvent(
                pluginId,
                DataAccessEvent(
                    System.currentTimeMillis(),
                    "CLIPBOARD",
                    "READ_ALLOWED"
                )
            )
        }
        
        return allowed
    }
    
    /**
     * Scans text for sensitive data patterns
     */
    fun scanForSensitiveData(pluginId: String, text: String, operation: String): List<String> {
        val detectedPatterns = mutableListOf<String>()
        
        SENSITIVE_PATTERNS.forEach { (patternName, pattern) ->
            if (pattern.matcher(text).find()) {
                detectedPatterns.add(patternName)
                
                logDataAccessEvent(
                    pluginId,
                    DataAccessEvent(
                        System.currentTimeMillis(),
                        patternName,
                        operation,
                        "*** REDACTED ***",
                        true // Accessing sensitive data is always suspicious
                    )
                )
                
                Timber.w("[DATA PROTECTOR] SENSITIVE DATA DETECTED: Plugin $pluginId accessed $patternName via $operation")
            }
        }
        
        return detectedPatterns
    }
    
    /**
     * Monitors file system access for sensitive patterns
     */
    fun monitorFileAccess(pluginId: String, filePath: String, operation: String): Boolean {
        val suspiciousFiles = listOf(
            "/data/data/", // App private data
            "/system/", // System files
            "/proc/", // Process information
            "password", "key", "token", "secret", ".pem", ".p12"
        )
        
        val isSuspicious = suspiciousFiles.any { pattern ->
            filePath.contains(pattern, ignoreCase = true)
        }
        
        if (isSuspicious) {
            logDataAccessEvent(
                pluginId,
                DataAccessEvent(
                    System.currentTimeMillis(),
                    "FILE_SYSTEM",
                    operation,
                    filePath,
                    true
                )
            )
            
            Timber.w("[DATA PROTECTOR] SUSPICIOUS FILE ACCESS: Plugin $pluginId tried $operation on $filePath")
            return false // Block access
        }
        
        logDataAccessEvent(
            pluginId,
            DataAccessEvent(
                System.currentTimeMillis(),
                "FILE_SYSTEM",
                operation,
                filePath
            )
        )
        
        return true // Allow access
    }
    
    /**
     * Monitors network requests for data exfiltration
     */
    fun monitorNetworkRequest(pluginId: String, url: String, hasData: Boolean) {
        val suspiciousDomains = listOf(
            "pastebin.com", "hastebin.com", "0bin.net", "paste.ee",
            "transfer.sh", "file.io", "wetransfer.com"
        )
        
        val isSuspicious = suspiciousDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        } || (hasData && isExternalDomain(url))
        
        logDataAccessEvent(
            pluginId,
            DataAccessEvent(
                System.currentTimeMillis(),
                "NETWORK",
                if (hasData) "POST_DATA" else "GET_REQUEST",
                url,
                isSuspicious
            )
        )
        
        if (isSuspicious) {
            Timber.w("[DATA PROTECTOR] SUSPICIOUS NETWORK REQUEST: Plugin $pluginId sending data to $url")
        }
    }
    
    /**
     * Gets security report for a plugin
     */
    fun getSecurityReport(pluginId: String): String {
        val events = pluginDataAccess[pluginId] ?: return "No data access recorded"
        
        val suspiciousEvents = events.filter { it.suspicious }
        val recentEvents = events.filter { 
            System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000 // Last 24h
        }
        
        return buildString {
            appendLine("=== Security Report: $pluginId ===")
            appendLine("Total events: ${events.size}")
            appendLine("Suspicious events: ${suspiciousEvents.size}")
            appendLine("Recent events (24h): ${recentEvents.size}")
            
            if (suspiciousEvents.isNotEmpty()) {
                appendLine("\nSUSPICIOUS ACTIVITIES:")
                suspiciousEvents.takeLast(5).forEach { event ->
                    appendLine("  [${formatTimestamp(event.timestamp)}] ${event.dataType} - ${event.operation}")
                }
            }
            
            // Risk assessment
            val riskLevel = when {
                suspiciousEvents.size >= 5 -> "HIGH"
                suspiciousEvents.size >= 2 -> "MEDIUM"
                else -> "LOW"
            }
            appendLine("\nRisk Level: $riskLevel")
        }
    }
    
    /**
     * Gets debug information about all monitoring
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Plugin Data Leakage Protector ===")
            appendLine("Monitored plugins: ${pluginDataAccess.size}")
            
            pluginDataAccess.forEach { (pluginId, events) ->
                val suspicious = events.count { it.suspicious }
                val recent = events.count { System.currentTimeMillis() - it.timestamp < 60000 } // Last minute
                appendLine("  $pluginId: ${events.size} total, $suspicious suspicious, $recent recent")
            }
        }
    }
    
    // Helper methods
    private fun logDataAccessEvent(pluginId: String, event: DataAccessEvent) {
        val events = pluginDataAccess[pluginId]
        if (events != null) {
            events.add(event)
            
            // Keep only last 100 events per plugin (thread-safe trim)
            while (events.size > 100) {
                try {
                    events.removeAt(0)
                } catch (e: IndexOutOfBoundsException) {
                    // Another thread may have already removed - this is fine
                    break
                }
            }
        }
    }
    
    private fun isExternalDomain(url: String): Boolean {
        return !url.contains("localhost") && 
               !url.contains("127.0.0.1") && 
               !url.contains("192.168.") &&
               !url.contains("10.0.0.")
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
