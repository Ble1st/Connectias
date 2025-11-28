package com.ble1st.connectias.feature.network.monitor

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for network traffic monitoring.
 * Tracks Rx/Tx bytes per interface and connection status.
 */
@Singleton
class NetworkMonitorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    } else {
        null
    }

    /**
     * Gets current network traffic statistics.
     */
    suspend fun getCurrentTraffic(): NetworkTraffic = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        
        val connectionType = when {
            capabilities == null -> ConnectionType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.UNKNOWN
        }
        
        NetworkTraffic(
            rxBytes = rxBytes,
            txBytes = txBytes,
            connectionType = connectionType,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Monitors network traffic continuously.
     * Emits updates at specified interval.
     */
    fun monitorTraffic(intervalMs: Long = 1000): Flow<NetworkTraffic> = flow {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastTimestamp = System.currentTimeMillis()
        
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()
            val currentTimestamp = System.currentTimeMillis()
            
            val timeDelta = (currentTimestamp - lastTimestamp) / 1000.0 // seconds
            
            val rxRate = if (timeDelta > 0) {
                (currentRx - lastRx) / timeDelta
            } else {
                0.0
            }
            
            val txRate = if (timeDelta > 0) {
                (currentTx - lastTx) / timeDelta
            } else {
                0.0
            }
            
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            
            val connectionType = when {
                capabilities == null -> ConnectionType.NONE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
            
            emit(
                NetworkTraffic(
                    rxBytes = currentRx,
                    txBytes = currentTx,
                    rxRate = rxRate,
                    txRate = txRate,
                    connectionType = connectionType,
                    timestamp = currentTimestamp
                )
            )
            
            lastRx = currentRx
            lastTx = currentTx
            lastTimestamp = currentTimestamp
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gets list of active network interfaces.
     */
    suspend fun getActiveInterfaces(): List<NetworkInterface> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            val result = mutableListOf<NetworkInterface>()
            
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    val addressList = mutableListOf<String>()
                    while (addresses.hasMoreElements()) {
                        addressList.add(addresses.nextElement().hostAddress ?: "")
                    }
                    
                    result.add(
                        NetworkInterface(
                            name = networkInterface.name,
                            displayName = networkInterface.displayName,
                            addresses = addressList,
                            isUp = networkInterface.isUp,
                            mtu = networkInterface.mtu
                        )
                    )
                }
            }
            
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to get network interfaces")
            emptyList()
        }
    }
}

/**
 * Network traffic data.
 */
data class NetworkTraffic(
    val rxBytes: Long,
    val txBytes: Long,
    val rxRate: Double = 0.0, // bytes per second
    val txRate: Double = 0.0, // bytes per second
    val connectionType: ConnectionType,
    val timestamp: Long
)

/**
 * Network interface information.
 */
data class NetworkInterface(
    val name: String,
    val displayName: String,
    val addresses: List<String>,
    val isUp: Boolean,
    val mtu: Int
)

/**
 * Connection type.
 */
enum class ConnectionType {
    NONE,
    WIFI,
    MOBILE,
    ETHERNET,
    UNKNOWN
}

