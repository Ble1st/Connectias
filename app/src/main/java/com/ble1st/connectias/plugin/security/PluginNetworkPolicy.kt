package com.ble1st.connectias.plugin.security

import android.content.Context
import android.net.TrafficStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network access control and monitoring for plugins
 */
@Singleton
class PluginNetworkPolicy @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    data class NetworkPolicy(
        val allowedDomains: List<String> = listOf("*"), // * means all
        val blockedDomains: List<String> = emptyList(),
        val allowedPorts: List<Int> = listOf(80, 443),
        val blockedPorts: List<Int> = emptyList(),
        val maxBandwidthKBps: Long = 1024, // 1MB/s
        val allowHttps: Boolean = true,
        val allowHttp: Boolean = false
    )
    
    data class NetworkUsage(
        val pluginId: String,
        val bytesSent: Long,
        val bytesReceived: Long,
        val packetsTransmitted: Long,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val pluginPolicies = ConcurrentHashMap<String, NetworkPolicy>()
    private val pluginUids = ConcurrentHashMap<String, Int>()
    private val _networkUsage = MutableStateFlow<Map<String, NetworkUsage>>(emptyMap())
    val networkUsage: Flow<Map<String, NetworkUsage>> = _networkUsage.asStateFlow()
    
    /**
     * Configure network access policy for a plugin
     */
    fun configureNetworkAccess(pluginId: String, policy: NetworkPolicy) {
        pluginPolicies[pluginId] = policy
        Timber.d("Network policy configured for $pluginId: $policy")
    }
    
    /**
     * Get network policy for a plugin
     */
    fun getNetworkPolicy(pluginId: String): NetworkPolicy {
        return pluginPolicies[pluginId] ?: NetworkPolicy()
    }
    
    /**
     * Register plugin UID for network tracking
     */
    fun registerPluginUid(pluginId: String, uid: Int) {
        pluginUids[pluginId] = uid
        Timber.d("Registered UID $uid for plugin $pluginId")
    }
    
    /**
     * Check if a domain is allowed for a plugin
     */
    fun isDomainAllowed(pluginId: String, domain: String): Boolean {
        val policy = getNetworkPolicy(pluginId)
        
        // Check blocked domains first
        if (policy.blockedDomains.any { domain.contains(it, ignoreCase = true) }) {
            Timber.w("Domain $domain is blocked for plugin $pluginId")
            return false
        }
        
        // Check allowed domains
        if (policy.allowedDomains.contains("*")) {
            return true
        }
        
        val allowed = policy.allowedDomains.any { domain.contains(it, ignoreCase = true) }
        if (!allowed) {
            Timber.w("Domain $domain not in allowlist for plugin $pluginId")
        }
        
        return allowed
    }
    
    /**
     * Check if a port is allowed for a plugin
     */
    fun isPortAllowed(pluginId: String, port: Int): Boolean {
        val policy = getNetworkPolicy(pluginId)
        
        // Check blocked ports first
        if (policy.blockedPorts.contains(port)) {
            Timber.w("Port $port is blocked for plugin $pluginId")
            return false
        }
        
        // Check allowed ports
        val allowed = policy.allowedPorts.isEmpty() || policy.allowedPorts.contains(port)
        if (!allowed) {
            Timber.w("Port $port not in allowlist for plugin $pluginId")
        }
        
        return allowed
    }
    
    /**
     * Monitor network usage for a plugin
     */
    fun monitorNetworkUsage(pluginId: String) {
        val uid = pluginUids[pluginId] ?: return
        
        try {
            val bytesSent = TrafficStats.getUidTxBytes(uid)
            val bytesReceived = TrafficStats.getUidRxBytes(uid)
            val packetsTransmitted = TrafficStats.getUidTxPackets(uid)
            
            val usage = NetworkUsage(
                pluginId = pluginId,
                bytesSent = bytesSent,
                bytesReceived = bytesReceived,
                packetsTransmitted = packetsTransmitted
            )
            
            val currentUsage = _networkUsage.value.toMutableMap()
            currentUsage[pluginId] = usage
            _networkUsage.value = currentUsage
            
            // Check bandwidth limits
            val policy = getNetworkPolicy(pluginId)
            val totalBytes = bytesSent + bytesReceived
            val bandwidthKBps = totalBytes / 1024
            
            if (bandwidthKBps > policy.maxBandwidthKBps) {
                Timber.w("Plugin $pluginId exceeds bandwidth limit: ${bandwidthKBps}KB/s")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to monitor network usage for $pluginId")
        }
    }
    
    /**
     * Clear network policy for a plugin
     */
    fun clearPluginPolicy(pluginId: String) {
        pluginPolicies.remove(pluginId)
        pluginUids.remove(pluginId)
        val currentUsage = _networkUsage.value.toMutableMap()
        currentUsage.remove(pluginId)
        _networkUsage.value = currentUsage
    }
    
    /**
     * Get plugins exceeding bandwidth limits
     */
    fun getPluginsExceedingBandwidth(): List<String> {
        return _networkUsage.value.filter { (pluginId, usage) ->
            val policy = getNetworkPolicy(pluginId)
            val totalBytes = usage.bytesSent + usage.bytesReceived
            val bandwidthKBps = totalBytes / 1024
            bandwidthKBps > policy.maxBandwidthKBps
        }.keys.toList()
    }
}
