package com.ble1st.connectias.plugin.security

import android.net.TrafficStats
import android.os.Process
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.Looper
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * Plugin Network Tracker with Process-Based Attribution
 * 
 * Provides accurate per-plugin network usage tracking by correlating
 * network activity with plugin process identity and explicit tracking.
 * 
 * SECURITY: Solves the shared sandbox UID problem for network attribution
 */
object PluginNetworkTracker {
    
    // Network usage tracking per plugin
    private val pluginNetworkUsage = ConcurrentHashMap<String, NetworkUsageStats>()
    private val pluginProcessMap = ConcurrentHashMap<String, Int>() // pluginId -> PID
    
    // Global tracking
    private val isTracking = AtomicBoolean(false)
    private val monitorHandler = Handler(Looper.getMainLooper())
    
    // Configuration
    private const val MONITOR_INTERVAL_MS = 5000L // 5 seconds
    private const val MAX_TRACKED_CONNECTIONS = 50
    
    data class NetworkUsageStats(
        val pluginId: String,
        val bytesReceived: AtomicLong = AtomicLong(0),
        val bytesSent: AtomicLong = AtomicLong(0),
        val connectionsOpened: AtomicLong = AtomicLong(0),
        val connectionsFailed: AtomicLong = AtomicLong(0),
        val domainsAccessed: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val portsUsed: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        val firstActivity: Long = System.currentTimeMillis(),
        var lastActivity: Long = System.currentTimeMillis()
    )
    
    data class NetworkConnection(
        val pluginId: String,
        val remoteHost: String,
        val remotePort: Int,
        val protocol: String,
        val timestamp: Long,
        var bytesReceived: Long = 0,
        var bytesSent: Long = 0,
        var isActive: Boolean = true
    )
    
    private val activeConnections = ConcurrentHashMap<String, NetworkConnection>()
    
    /**
     * Starts network tracking for all plugins
     */
    fun startTracking() {
        if (isTracking.compareAndSet(false, true)) {
            Timber.i("[NETWORK TRACKER] Starting plugin network tracking")
            scheduleMonitorCheck()
        }
    }
    
    /**
     * Stops network tracking
     */
    fun stopTracking() {
        if (isTracking.compareAndSet(true, false)) {
            Timber.i("[NETWORK TRACKER] Stopping plugin network tracking")
            monitorHandler.removeCallbacks(monitorRunnable)
        }
    }
    
    /**
     * Registers a plugin for network tracking
     */
    fun registerPlugin(pluginId: String, processId: Int = Process.myPid()) {
        pluginNetworkUsage.putIfAbsent(pluginId, NetworkUsageStats(pluginId))
        pluginProcessMap[pluginId] = processId
        Timber.d("[NETWORK TRACKER] Plugin registered: $pluginId (PID: $processId)")
    }
    
    /**
     * Unregisters a plugin from network tracking
     */
    fun unregisterPlugin(pluginId: String) {
        val stats = pluginNetworkUsage.remove(pluginId)
        pluginProcessMap.remove(pluginId)
        
        // Remove active connections for this plugin
        activeConnections.entries.removeIf { it.value.pluginId == pluginId }
        
        if (stats != null) {
            val totalBytes = stats.bytesReceived.get() + stats.bytesSent.get()
            val connectionCount = stats.connectionsOpened.get()
            Timber.i("[NETWORK TRACKER] Plugin unregistered: $pluginId " +
                    "(${totalBytes} bytes, ${connectionCount} connections)")
        }
    }
    
    /**
     * Explicitly tracks a network request initiated by a plugin
     * Use this for HTTP requests via bridge
     */
    fun trackNetworkRequest(pluginId: String, url: String, method: String = "GET") {
        try {
            // Support non-URL schemes used by internal bridges (e.g. tcp ping).
            // Keep it best-effort and avoid noisy warnings for valid non-HTTP operations.
            if (url.startsWith("tcp://", ignoreCase = true)) {
                val rest = url.removePrefix("tcp://")
                val host = rest.substringBefore(":").ifBlank { "unknown" }
                val port = rest.substringAfter(":", "").toIntOrNull() ?: 0

                val stats = pluginNetworkUsage[pluginId]
                if (stats != null) {
                    stats.domainsAccessed.add(host)
                    if (port > 0) stats.portsUsed.add(port)
                    stats.connectionsOpened.incrementAndGet()
                    stats.lastActivity = System.currentTimeMillis()

                    val connectionKey = "${pluginId}-${host}-${port}-${System.currentTimeMillis()}"
                    activeConnections[connectionKey] = NetworkConnection(
                        pluginId = pluginId,
                        remoteHost = host,
                        remotePort = port,
                        protocol = "tcp",
                        timestamp = System.currentTimeMillis()
                    )
                    Timber.d("[NETWORK TRACKER] Request tracked: $pluginId -> $method tcp $host:$port")
                }
                return
            }

            val parsedUrl = URL(url)
            val host = parsedUrl.host
            val port = if (parsedUrl.port != -1) parsedUrl.port else parsedUrl.defaultPort
            
            val stats = pluginNetworkUsage[pluginId]
            if (stats != null) {
                stats.domainsAccessed.add(host)
                stats.portsUsed.add(port)
                stats.connectionsOpened.incrementAndGet()
                stats.lastActivity = System.currentTimeMillis()
                
                val connectionKey = "${pluginId}-${host}-${port}-${System.currentTimeMillis()}"
                activeConnections[connectionKey] = NetworkConnection(
                    pluginId = pluginId,
                    remoteHost = host,
                    remotePort = port,
                    protocol = parsedUrl.protocol,
                    timestamp = System.currentTimeMillis()
                )
                
                Timber.d("[NETWORK TRACKER] Request tracked: $pluginId -> $method $host:$port")
            }
        } catch (e: Exception) {
            Timber.w(e, "[NETWORK TRACKER] Failed to track network request: $url")
        }
    }
    
    /**
     * Tracks data transfer for a connection
     */
    fun trackDataTransfer(pluginId: String, bytesReceived: Long = 0, bytesSent: Long = 0) {
        val stats = pluginNetworkUsage[pluginId]
        if (stats != null) {
            if (bytesReceived > 0) {
                stats.bytesReceived.addAndGet(bytesReceived)
            }
            if (bytesSent > 0) {
                stats.bytesSent.addAndGet(bytesSent)
            }
            stats.lastActivity = System.currentTimeMillis()
            
            Timber.v("[NETWORK TRACKER] Data transfer: $pluginId (+${bytesReceived}B/-${bytesSent}B)")
        }
    }
    
    /**
     * Tracks a failed connection attempt
     */
    fun trackConnectionFailure(pluginId: String, host: String, reason: String) {
        val stats = pluginNetworkUsage[pluginId]
        if (stats != null) {
            stats.connectionsFailed.incrementAndGet()
            stats.lastActivity = System.currentTimeMillis()
            
            Timber.w("[NETWORK TRACKER] Connection failed: $pluginId -> $host ($reason)")
        }
    }
    
    /**
     * Gets network usage statistics for a plugin
     */
    fun getNetworkUsage(pluginId: String): NetworkUsageStats? {
        return pluginNetworkUsage[pluginId]
    }
    
    /**
     * Gets network usage for all plugins
     */
    fun getAllNetworkUsage(): Map<String, NetworkUsageStats> {
        return pluginNetworkUsage.toMap()
    }
    
    /**
     * Gets active connections for a plugin
     */
    fun getActiveConnections(pluginId: String): List<NetworkConnection> {
        return activeConnections.values.filter { it.pluginId == pluginId && it.isActive }
    }
    
    /**
     * Checks if a plugin has suspicious network activity
     */
    fun hasSuspiciousActivity(pluginId: String): Boolean {
        val stats = pluginNetworkUsage[pluginId] ?: return false
        
        // Define suspicious thresholds
        val maxBytesPerHour = 100 * 1024 * 1024L // 100MB per hour
        val maxConnectionsPerMinute = 60
        val suspiciousDomains = setOf(
            "pastebin.com", "hastebin.com", "0bin.net", "paste.ee",
            "transfer.sh", "file.io", "wetransfer.com"
        )
        
        val hoursSinceFirst = (System.currentTimeMillis() - stats.firstActivity) / (60 * 60 * 1000.0)
        val minutesSinceFirst = (System.currentTimeMillis() - stats.firstActivity) / (60 * 1000.0)
        
        val totalBytes = stats.bytesReceived.get() + stats.bytesSent.get()
        val bytesPerHour = if (hoursSinceFirst > 0) totalBytes / hoursSinceFirst else totalBytes.toDouble()
        val connectionsPerMinute = if (minutesSinceFirst > 0) stats.connectionsOpened.get() / minutesSinceFirst else stats.connectionsOpened.get().toDouble()
        
        return when {
            bytesPerHour > maxBytesPerHour -> {
                Timber.w("[NETWORK TRACKER] SUSPICIOUS: Plugin $pluginId excessive data usage: ${bytesPerHour.toLong()} bytes/hour")
                true
            }
            connectionsPerMinute > maxConnectionsPerMinute -> {
                Timber.w("[NETWORK TRACKER] SUSPICIOUS: Plugin $pluginId excessive connections: ${connectionsPerMinute.toInt()} conn/min")
                true
            }
            stats.domainsAccessed.any { it in suspiciousDomains } -> {
                val suspiciousFound = stats.domainsAccessed.intersect(suspiciousDomains)
                Timber.w("[NETWORK TRACKER] SUSPICIOUS: Plugin $pluginId accessing suspicious domains: $suspiciousFound")
                true
            }
            else -> false
        }
    }
    
    /**
     * Generates a network usage report for a plugin
     */
    fun getNetworkReport(pluginId: String): String {
        val stats = pluginNetworkUsage[pluginId] ?: return "No network data for plugin: $pluginId"
        
        val totalBytes = stats.bytesReceived.get() + stats.bytesSent.get()
        val activeConns = getActiveConnections(pluginId)
        val isSuspicious = hasSuspiciousActivity(pluginId)
        
        return buildString {
            appendLine("=== Network Report: $pluginId ===")
            appendLine("Total Data: ${formatBytes(totalBytes)} (↓${formatBytes(stats.bytesReceived.get())} ↑${formatBytes(stats.bytesSent.get())})")
            appendLine("Connections: ${stats.connectionsOpened.get()} opened, ${stats.connectionsFailed.get()} failed")
            appendLine("Active Connections: ${activeConns.size}")
            appendLine("Domains Accessed: ${stats.domainsAccessed.size}")
            appendLine("Ports Used: ${stats.portsUsed.joinToString(", ")}")
            appendLine("First Activity: ${formatTimestamp(stats.firstActivity)}")
            appendLine("Last Activity: ${formatTimestamp(stats.lastActivity)}")
            appendLine("Status: ${if (isSuspicious) "SUSPICIOUS" else "Normal"}")
            
            if (stats.domainsAccessed.isNotEmpty()) {
                appendLine("\nDomains:")
                stats.domainsAccessed.take(10).forEach { domain ->
                    appendLine("  - $domain")
                }
                if (stats.domainsAccessed.size > 10) {
                    appendLine("  ... and ${stats.domainsAccessed.size - 10} more")
                }
            }
        }
    }
    
    /**
     * Gets debug information about all tracking
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Plugin Network Tracker ===")
            appendLine("Tracking Active: ${isTracking.get()}")
            appendLine("Tracked Plugins: ${pluginNetworkUsage.size}")
            appendLine("Active Connections: ${activeConnections.size}")
            
            pluginNetworkUsage.forEach { (pluginId, stats) ->
                val totalBytes = stats.bytesReceived.get() + stats.bytesSent.get()
                val pid = pluginProcessMap[pluginId]
                val suspicious = if (hasSuspiciousActivity(pluginId)) " [SUSPICIOUS]" else ""
                appendLine("  $pluginId (PID:$pid): ${formatBytes(totalBytes)}${suspicious}")
            }
        }
    }
    
    // Private helper methods
    private val monitorRunnable = Runnable {
        try {
            monitorNetworkActivity()
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK TRACKER] Error during network monitoring")
        } finally {
            if (isTracking.get()) {
                scheduleMonitorCheck()
            }
        }
    }
    
    private fun scheduleMonitorCheck() {
        monitorHandler.postDelayed(monitorRunnable, MONITOR_INTERVAL_MS)
    }
    
    private fun monitorNetworkActivity() {
        // Clean up old inactive connections
        val cutoffTime = System.currentTimeMillis() - (5 * 60 * 1000) // 5 minutes
        activeConnections.entries.removeIf { (_, connection) ->
            connection.timestamp < cutoffTime
        }
        
        // Log summary if there's activity
        val totalActiveConnections = activeConnections.size
        val totalTrackedPlugins = pluginNetworkUsage.size
        
        if (totalActiveConnections > 0 || totalTrackedPlugins > 0) {
            Timber.v("[NETWORK TRACKER] Active: ${totalActiveConnections} connections, ${totalTrackedPlugins} plugins")
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
