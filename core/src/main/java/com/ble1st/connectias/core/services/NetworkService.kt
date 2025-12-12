package com.ble1st.connectias.core.services

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import com.ble1st.connectias.core.models.ConnectionType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for network operations.
 * Provides common network APIs for use across features.
 * 
 * Required permissions:
 * - android.permission.ACCESS_NETWORK_STATE (declared in AndroidManifest.xml)
 * 
 * Note: This service does not require runtime permission requests as ACCESS_NETWORK_STATE
 * is a normal permission that is automatically granted at install time.
 */
@Singleton
class NetworkService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager: ConnectivityManager? by lazy {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            Timber.e("ConnectivityManager is null - Context.CONNECTIVITY_SERVICE unavailable. Network operations will fail.")
        }
        manager
    }
    
    /**
     * Gets the active network.
     * Low-level API for direct ConnectivityManager access.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getActiveNetwork(): Network? {
        return try {
            connectivityManager?.activeNetwork
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied: ACCESS_NETWORK_STATE required")
            null
        } catch (e: IllegalStateException) {
            Timber.e(e, "ConnectivityManager not available")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting active network")
            null
        }
    }
    
    /**
     * Gets network capabilities for the active network.
     * Low-level API for direct ConnectivityManager access.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getNetworkCapabilities(): NetworkCapabilities? {
        return try {
            val manager = connectivityManager
            manager?.activeNetwork?.let { network ->
                manager.getNetworkCapabilities(network)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied: ACCESS_NETWORK_STATE required")
            null
        } catch (e: IllegalStateException) {
            Timber.e(e, "ConnectivityManager or Network not available")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting network capabilities")
            null
        }
    }
    
    /**
     * Gets link properties for the active network.
     * Low-level API for direct ConnectivityManager access.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getLinkProperties(): LinkProperties? {
        return try {
            val manager = connectivityManager
            manager?.activeNetwork?.let { network ->
                manager.getLinkProperties(network)
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied: ACCESS_NETWORK_STATE required")
            null
        } catch (e: IllegalStateException) {
            Timber.e(e, "ConnectivityManager or Network not available")
            null
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error getting link properties")
            null
        }
    }
    
    /**
     * Checks if device is connected to a network.
     * High-level convenience API.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun isConnected(): Boolean {
        return try {
            getNetworkCapabilities() != null
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied: ACCESS_NETWORK_STATE required")
            false
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error checking connection status")
            false
        }
    }
    
    /**
     * Gets the connection type.
     * High-level convenience API.
     * @return Connection type enum
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getConnectionType(): ConnectionType {
        return try {
            val capabilities = getNetworkCapabilities() ?: return ConnectionType.NONE
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
                else -> ConnectionType.UNKNOWN
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get connection type")
            ConnectionType.UNKNOWN
        }
    }
    
    /**
     * Gets DNS servers for the active network.
     * High-level convenience API.
     * @return List of DNS server IP addresses
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getDnsServers(): List<String> {
        return try {
            getLinkProperties()?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get DNS servers")
            emptyList()
        }
    }
    
    /**
     * Gets the gateway IP address for the active network.
     * High-level convenience API.
     * @return Gateway IP address or null if not available
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    suspend fun getGateway(): String? {
        return try {
            getLinkProperties()?.routes?.firstOrNull()?.gateway?.hostAddress
        } catch (e: Exception) {
            Timber.e(e, "Failed to get gateway")
            null
        }
    }
    
    /**
     * Gets the ConnectivityManager instance.
     * Internal helper for providers that need direct access.
     */
    internal fun getConnectivityManager(): ConnectivityManager? = connectivityManager
}

