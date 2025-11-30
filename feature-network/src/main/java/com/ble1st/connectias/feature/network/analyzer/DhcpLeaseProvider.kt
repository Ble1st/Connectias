package com.ble1st.connectias.feature.network.analyzer

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.models.DhcpLease
import com.ble1st.connectias.feature.network.models.NetworkDevice
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for DHCP lease information.
 * Since Android doesn't provide direct DHCP access, leases are inferred from device discovery
 * and tracked over time to identify static vs. dynamic IPs.
 */
@Singleton
class DhcpLeaseProvider @Inject constructor() {
    /**
     * Minimum number of observations required to classify an IP as static.
     * Static IPs require lower confidence (2+ observations) since they're more common.
     */
    private val STATIC_IP_MIN_OBSERVATIONS = 2

    /**
     * Minimum number of observations required to classify an IP as reserved.
     * Reserved IPs require higher confidence (3+ observations) since they're less common
     * and need more evidence to distinguish from regular static IPs.
     */
    private val RESERVED_IP_MIN_OBSERVATIONS = 3

    /**
     * Number of days after which inactive IPs are removed from history.
     * Prevents unbounded memory growth in environments with many transient devices.
     */
    private val INACTIVE_IP_CLEANUP_DAYS = 7L

    private val leaseHistory = mutableMapOf<String, MutableList<LeaseHistoryEntry>>()
    private val mutex = Mutex()

    /**
     * Infers DHCP leases from discovered devices.
     * Tracks IP assignments over time to identify static vs. dynamic IPs.
     * Automatically cleans up inactive IPs that haven't been seen in the last N days.
     * 
     * @param devices Currently discovered devices
     * @return List of inferred DHCP leases
     */
    suspend fun inferLeases(devices: List<NetworkDevice>): List<DhcpLease> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        
        // Clean up inactive IPs before processing
        cleanupInactiveIps(currentTime)
        
        val leases = mutableListOf<DhcpLease>()

        devices.forEach { device ->
            val ipAddress = device.ipAddress
            
            val estimatedLeaseDuration = 24 * 60 * 60 * 1000L // 24 hours default
            
            val (isStatic, leaseStartTime) = mutex.withLock {
                val history = leaseHistory.getOrPut(ipAddress) { mutableListOf() }

                // Record current sighting
                history.add(
                    LeaseHistoryEntry(
                        ipAddress = ipAddress,
                        hostname = device.hostname,
                        macAddress = device.macAddress,
                        timestamp = currentTime
                    )
                )

                // Keep only last 10 entries per IP
                if (history.size > 10) {
                    history.removeAt(0)
                }

                // Use the timestamp of the first history entry as lease start time
                // This represents when the IP was first observed, not the current time
                val firstEntryTimestamp = history.firstOrNull()?.timestamp ?: currentTime
                val leaseStart = firstEntryTimestamp
                
                // Determine if IP is static (same MAC address over time) or dynamic
                val isStaticIp = history.size >= STATIC_IP_MIN_OBSERVATIONS && history.all { 
                    it.macAddress == device.macAddress && it.macAddress != null 
                }
                
                Pair(isStaticIp, leaseStart)
            }
            
            val leaseExpiryTime = leaseStartTime + estimatedLeaseDuration

            leases.add(
                DhcpLease(
                    ipAddress = ipAddress,
                    hostname = device.hostname,
                    macAddress = device.macAddress,
                    leaseStartTime = leaseStartTime,
                    leaseExpiryTime = leaseExpiryTime,
                    isStatic = isStatic,
                    lastSeen = currentTime
                )
            )
        }

        Timber.d("Inferred ${leases.size} DHCP leases from ${devices.size} devices")
        leases
    }

    /**
     * Gets lease history for a specific IP address.
     */
    suspend fun getLeaseHistory(ipAddress: String): List<LeaseHistoryEntry> = mutex.withLock {
        return@withLock leaseHistory[ipAddress]?.toList() ?: emptyList()
    }

    /**
     * Gets all lease history.
     */
    suspend fun getAllLeaseHistory(): Map<String, List<LeaseHistoryEntry>> = mutex.withLock {
        return@withLock leaseHistory.mapValues { it.value.toList() }
    }

    /**
     * Identifies reserved IPs (likely static IPs based on history).
     * Reserved IPs require higher confidence (3+ observations) compared to static IPs (2+ observations).
     */
    suspend fun getReservedIps(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            leaseHistory.filter { (_, history) ->
                history.size >= RESERVED_IP_MIN_OBSERVATIONS && history.all { entry ->
                    entry.macAddress == history.first().macAddress && entry.macAddress != null
                }
            }.keys.toList()
        }
    }

    /**
     * Removes IPs from history that haven't been seen in the last N days.
     * This prevents unbounded memory growth in environments with many transient devices.
     * 
     * @param currentTime Current timestamp in milliseconds
     */
    private suspend fun cleanupInactiveIps(currentTime: Long) = mutex.withLock {
        val cutoffTime = currentTime - (INACTIVE_IP_CLEANUP_DAYS * 24 * 60 * 60 * 1000L)
        val ipsToRemove = mutableListOf<String>()
        
        leaseHistory.forEach { (ip, history) ->
            // Check if the most recent entry is older than the cutoff
            val mostRecentEntry = history.maxByOrNull { it.timestamp }
            if (mostRecentEntry == null || mostRecentEntry.timestamp < cutoffTime) {
                ipsToRemove.add(ip)
            }
        }
        
        // Remove inactive IPs
        ipsToRemove.forEach { ip ->
            leaseHistory.remove(ip)
        }
        
        if (ipsToRemove.isNotEmpty()) {
            Timber.d("Cleaned up ${ipsToRemove.size} inactive IPs from lease history")
        }
    }
}

/**
 * History entry for IP address assignments.
 */
data class LeaseHistoryEntry(
    val ipAddress: String,
    val hostname: String,
    val macAddress: String?,
    val timestamp: Long
)
