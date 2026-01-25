package com.ble1st.connectias.plugin.security

import android.net.TrafficStats
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.net.URI
import java.net.URL
import java.util.regex.Pattern

/**
 * Enhanced Plugin Network Policy with Honest Telemetry Marking
 * 
 * Extends the existing PluginNetworkPolicy with better attribution
 * and honest telemetry-only traffic marking for accurate monitoring.
 * 
 * SECURITY: Provides truthful network usage reporting and policy enforcement
 */
class EnhancedPluginNetworkPolicy {
    
    // Network policy per plugin
    private val pluginPolicies = ConcurrentHashMap<String, NetworkPolicyConfig>()
    private val pluginTelemetryMode = ConcurrentHashMap<String, Boolean>() // true = telemetry-only mode
    
    // Global policy settings
    private val globalPolicyEnabled = AtomicBoolean(true)
    private val telemetryOnlyBypass = AtomicBoolean(true) // Allow telemetry traffic to bypass some restrictions
    
    data class NetworkPolicyConfig(
        val pluginId: String,
        val allowedDomains: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val blockedDomains: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        val allowedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        val blockedPorts: MutableSet<Int> = ConcurrentHashMap.newKeySet(),
        val maxBandwidthBytesPerSecond: AtomicLong = AtomicLong(0), // 0 = unlimited
        val maxConnectionsPerMinute: Int = 60,
        var allowTelemetryOnly: Boolean = false,
        val enabled: Boolean = true
    )
    
    companion object {
        // Well-known telemetry domains that are generally safe
        private val TELEMETRY_DOMAINS = setOf(
            "google-analytics.com", "googleanalytics.com", "analytics.google.com",
            "firebase.google.com", "firebaselogging.googleapis.com",
            "crashlytics.com", "fabric.io",
            "bugsnag.com", "sentry.io",
            "amplitude.com", "mixpanel.com",
            "flurry.com", "localytics.com"
        )
        
        // Suspicious domains that should always be blocked or flagged
        private val SUSPICIOUS_DOMAINS = setOf(
            "pastebin.com", "hastebin.com", "0bin.net", "paste.ee",
            "transfer.sh", "file.io", "wetransfer.com", "dropbox.com",
            "mega.nz", "mediafire.com", "4shared.com",
            "bit.ly", "tinyurl.com", "t.co", "goo.gl" // URL shorteners
        )
        
        // Default safe ports
        private val SAFE_PORTS = setOf(80, 443, 8080, 8443)
        
        // Suspicious ports (commonly used for malware, C&C, etc.)
        private val SUSPICIOUS_PORTS = setOf(
            1337, 31337, // Leet speak ports
            6667, 6668, 6669, // IRC
            4444, 5555, 7777, 8888, 9999, // Common backdoor ports
            1234, 12345, 54321, // Simple backdoor ports
            3389, 5900, // Remote desktop
            22, 23, 21 // SSH, Telnet, FTP (suspicious for plugins)
        )
    }
    
    /**
     * Registers a plugin with network policy
     */
    fun registerPlugin(pluginId: String, telemetryOnly: Boolean = false) {
        val policy = NetworkPolicyConfig(
            pluginId = pluginId,
            allowTelemetryOnly = telemetryOnly
        )
        
        // Set default allowed domains based on mode
        if (telemetryOnly) {
            policy.allowedDomains.addAll(TELEMETRY_DOMAINS)
            policy.allowedPorts.addAll(SAFE_PORTS)
            pluginTelemetryMode[pluginId] = true
        } else {
            policy.allowedPorts.addAll(SAFE_PORTS)
            pluginTelemetryMode[pluginId] = false
        }
        
        // Always block suspicious domains and ports
        policy.blockedDomains.addAll(SUSPICIOUS_DOMAINS)
        policy.blockedPorts.addAll(SUSPICIOUS_PORTS)
        
        pluginPolicies[pluginId] = policy
        
        Timber.i("[NETWORK POLICY] Plugin registered: $pluginId (telemetry-only: $telemetryOnly)")
    }
    
    /**
     * Unregisters a plugin from network policy
     */
    fun unregisterPlugin(pluginId: String) {
        pluginPolicies.remove(pluginId)
        pluginTelemetryMode.remove(pluginId)
        Timber.i("[NETWORK POLICY] Plugin unregistered: $pluginId")
    }
    
    /**
     * Checks if a network request is allowed for a plugin
     */
    fun isRequestAllowed(pluginId: String, url: String, isTelemetry: Boolean = false): NetworkPolicyResult {
        val policy = pluginPolicies[pluginId]
        
        if (policy == null) {
            return NetworkPolicyResult.BLOCKED("Plugin not registered: $pluginId")
        }
        
        if (!policy.enabled) {
            return NetworkPolicyResult.BLOCKED("Network access disabled for plugin: $pluginId")
        }
        
        try {
            val (domain, port) = parseEndpoint(url)
            
            // Check if plugin is in telemetry-only mode
            val isTelemetryOnlyPlugin = pluginTelemetryMode[pluginId] == true
            
            if (isTelemetryOnlyPlugin && !isTelemetry) {
                return NetworkPolicyResult.BLOCKED("Plugin $pluginId is in telemetry-only mode but request is not marked as telemetry")
            }
            
            // Check blocked domains first (highest priority)
            if (policy.blockedDomains.any { blockedDomain -> 
                domain == blockedDomain || domain.endsWith(".$blockedDomain")
            }) {
                return NetworkPolicyResult.BLOCKED("Domain blocked: $domain")
            }
            
            // Check blocked ports
            if (port in policy.blockedPorts) {
                return NetworkPolicyResult.BLOCKED("Port blocked: $port")
            }
            
            // For telemetry requests, be more lenient
            if (isTelemetry && telemetryOnlyBypass.get()) {
                // Allow known telemetry domains
                if (TELEMETRY_DOMAINS.any { telemetryDomain ->
                    domain == telemetryDomain || domain.endsWith(".$telemetryDomain")
                }) {
                    return NetworkPolicyResult.ALLOWED("Telemetry request to known safe domain")
                }
            }
            
            // Check allowed domains
            if (policy.allowedDomains.isNotEmpty()) {
                val isDomainAllowed = policy.allowedDomains.any { allowedDomain ->
                    domain == allowedDomain || domain.endsWith(".$allowedDomain")
                }
                if (!isDomainAllowed) {
                    return NetworkPolicyResult.BLOCKED("Domain not in allowlist: $domain")
                }
            }
            
            // Check allowed ports
            if (policy.allowedPorts.isNotEmpty() && port !in policy.allowedPorts) {
                return NetworkPolicyResult.BLOCKED("Port not in allowlist: $port")
            }
            
            // Additional security checks
            val securityResult = performSecurityChecks(domain, port, isTelemetry)
            if (!securityResult.allowed) {
                return securityResult
            }
            
            return NetworkPolicyResult.ALLOWED("Request approved")
            
        } catch (e: Exception) {
            Timber.w(e, "[NETWORK POLICY] Failed to parse URL: $url")
            return NetworkPolicyResult.BLOCKED("Invalid URL format: ${e.message}")
        }
    }
    
    /**
     * Updates network policy for a plugin
     */
    fun updatePolicy(pluginId: String, updater: (NetworkPolicyConfig) -> Unit) {
        val policy = pluginPolicies[pluginId]
        if (policy != null) {
            updater(policy)
            Timber.d("[NETWORK POLICY] Policy updated for plugin: $pluginId")
        } else {
            Timber.w("[NETWORK POLICY] Cannot update policy - plugin not found: $pluginId")
        }
    }
    
    /**
     * Sets telemetry-only mode for a plugin
     */
    fun setTelemetryOnlyMode(pluginId: String, enabled: Boolean) {
        pluginTelemetryMode[pluginId] = enabled
        
        val policy = pluginPolicies[pluginId]
        if (policy != null) {
            policy.allowTelemetryOnly = enabled
            
            if (enabled) {
                // Restrict to only telemetry domains
                policy.allowedDomains.clear()
                policy.allowedDomains.addAll(TELEMETRY_DOMAINS)
            }
        }
        
        Timber.i("[NETWORK POLICY] Telemetry-only mode for $pluginId: $enabled")
    }
    
    /**
     * Gets network policy for a plugin
     */
    fun getPolicy(pluginId: String): NetworkPolicyConfig? {
        return pluginPolicies[pluginId]
    }
    
    /**
     * Checks if a plugin is in telemetry-only mode
     */
    fun isTelemetryOnlyMode(pluginId: String): Boolean {
        return pluginTelemetryMode[pluginId] == true
    }
    
    /**
     * Gets policy report for a plugin
     */
    fun getPolicyReport(pluginId: String): String {
        val policy = pluginPolicies[pluginId] ?: return "No policy found for plugin: $pluginId"
        val isTelemetryOnly = pluginTelemetryMode[pluginId] == true
        
        return buildString {
            appendLine("=== Network Policy: $pluginId ===")
            appendLine("Status: ${if (policy.enabled) "Enabled" else "Disabled"}")
            appendLine("Mode: ${if (isTelemetryOnly) "Telemetry-Only" else "Full Network Access"}")
            appendLine("Max Bandwidth: ${if (policy.maxBandwidthBytesPerSecond.get() == 0L) "Unlimited" else "${policy.maxBandwidthBytesPerSecond.get()} bytes/sec"}")
            appendLine("Max Connections: ${policy.maxConnectionsPerMinute}/minute")
            
            appendLine("\nAllowed Domains (${policy.allowedDomains.size}):")
            policy.allowedDomains.take(10).forEach { domain ->
                appendLine("  ✓ $domain")
            }
            if (policy.allowedDomains.size > 10) {
                appendLine("  ... and ${policy.allowedDomains.size - 10} more")
            }
            
            appendLine("\nBlocked Domains (${policy.blockedDomains.size}):")
            policy.blockedDomains.take(5).forEach { domain ->
                appendLine("  ✗ $domain")
            }
            if (policy.blockedDomains.size > 5) {
                appendLine("  ... and ${policy.blockedDomains.size - 5} more")
            }
            
            appendLine("\nAllowed Ports: ${policy.allowedPorts.joinToString(", ")}")
            appendLine("Blocked Ports: ${policy.blockedPorts.joinToString(", ")}")
        }
    }
    
    /**
     * Gets debug information about all policies
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Enhanced Plugin Network Policy ===")
            appendLine("Global Policy Enabled: ${globalPolicyEnabled.get()}")
            appendLine("Telemetry Bypass Enabled: ${telemetryOnlyBypass.get()}")
            appendLine("Registered Plugins: ${pluginPolicies.size}")
            
            pluginPolicies.forEach { (pluginId, policy) ->
                val mode = if (pluginTelemetryMode[pluginId] == true) "T" else "F"
                val status = if (policy.enabled) "ON" else "OFF"
                appendLine("  $pluginId [$mode][$status]: ${policy.allowedDomains.size} allowed, ${policy.blockedDomains.size} blocked")
            }
        }
    }
    
    // Private helper methods
    private fun performSecurityChecks(domain: String, port: Int, isTelemetry: Boolean): NetworkPolicyResult {
        // Check for suspicious patterns
        if (domain.matches(Regex(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*"))) {
            // Direct IP access (potentially suspicious)
            if (!isTelemetry) {
                return NetworkPolicyResult.BLOCKED("Direct IP access not allowed for non-telemetry requests: $domain")
            }
        }
        
        // Check for suspicious TLDs
        val suspiciousTlds = setOf(".tk", ".ml", ".ga", ".cf", ".onion")
        if (suspiciousTlds.any { domain.endsWith(it) }) {
            return NetworkPolicyResult.BLOCKED("Suspicious TLD detected: $domain")
        }
        
        // Check for URL shortener patterns
        val shortenerPatterns = listOf("bit\\.ly", "tinyurl", "goo\\.gl", "t\\.co", "short")
        if (shortenerPatterns.any { Pattern.compile(it).matcher(domain).find() }) {
            return NetworkPolicyResult.BLOCKED("URL shortener detected: $domain")
        }
        
        // Check for suspicious port usage
        if (port in SUSPICIOUS_PORTS) {
            return NetworkPolicyResult.BLOCKED("Suspicious port detected: $port")
        }
        
        return NetworkPolicyResult.ALLOWED("Security checks passed")
    }

    /**
     * Parse request endpoint for both http(s) URLs and pseudo-schemes like tcp://host:port.
     *
     * SECURITY NOTE:
     * We intentionally support tcp:// for socket-like operations (e.g., tcpPing/openSocket).
     * java.net.URL does not know the "tcp" scheme, so we use URI for scheme-aware parsing.
     */
    private fun parseEndpoint(raw: String): Pair<String, Int> {
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            // Fallback to URL for legacy callers; will throw if invalid
            val u = URL(raw)
            val host = u.host?.lowercase()?.trim().orEmpty()
            val port = if (u.port != -1) u.port else u.defaultPort
            if (host.isBlank()) throw IllegalArgumentException("Missing host")
            return host to port
        }

        val scheme = (uri.scheme ?: "").lowercase()
        val host = uri.host?.lowercase()?.trim()

        // URI("tcp://example.com:443") has host+port populated.
        if (!host.isNullOrBlank()) {
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                scheme == "http" -> 80
                scheme == "tcp" -> 443 // default for TCP reachability checks
                else -> -1
            }
            if (port <= 0) throw IllegalArgumentException("Missing port")
            return host to port
        }

        // Handle cases like "example.com:443" (no scheme, treated as host:port).
        val s = raw.trim()
        val hostPort = s.removePrefix("tcp://").removePrefix("http://").removePrefix("https://")
        val idx = hostPort.lastIndexOf(':')
        if (idx <= 0 || idx == hostPort.length - 1) {
            throw IllegalArgumentException("Invalid endpoint (expected host:port)")
        }
        val h = hostPort.substring(0, idx).lowercase()
        val p = hostPort.substring(idx + 1).toIntOrNull() ?: throw IllegalArgumentException("Invalid port")
        return h to p
    }
    
    /**
     * Result of network policy check
     */
    data class NetworkPolicyResult(
        val allowed: Boolean,
        val reason: String,
        val isTelemetryTraffic: Boolean = false
    ) {
        companion object {
            fun ALLOWED(reason: String, isTelemetry: Boolean = false) = 
                NetworkPolicyResult(true, reason, isTelemetry)
                
            fun BLOCKED(reason: String) = 
                NetworkPolicyResult(false, reason, false)
        }
    }
}
