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
    private val leaseHistory = mutableMapOf<String, MutableList<LeaseHistoryEntry>>()
    private val mutex = Mutex()

    /**
     * Infers DHCP leases from discovered devices.
     * Tracks IP assignments over time to identify static vs. dynamic IPs.
     * 
     * @param devices Currently discovered devices
     * @return List of inferred DHCP leases
     */
    suspend fun inferLeases(devices: List<NetworkDevice>): List<DhcpLease> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val leases = mutableListOf<DhcpLease>()

        devices.forEach { device ->
            val ipAddress = device.ipAddress
            
            // Use current time as lease start instead of first history entry
            val estimatedLeaseDuration = 24 * 60 * 60 * 1000L // 24 hours default
            val leaseStartTime = currentTime
            val leaseExpiryTime = currentTime + estimatedLeaseDuration
            
            val isStatic = mutex.withLock {
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

                // Determine if IP is static (same MAC address over time) or dynamic
                history.size >= 2 && history.all { 
                    it.macAddress == device.macAddress && it.macAddress != null 
                }
            }

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
     */
    suspend fun getReservedIps(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            leaseHistory.filter { (_, history) ->
                history.size >= 3 && history.all { entry ->
                    entry.macAddress == history.first().macAddress && entry.macAddress != null
                }
            }.keys.toList()
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
