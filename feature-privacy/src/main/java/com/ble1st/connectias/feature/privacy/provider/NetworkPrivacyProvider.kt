package com.ble1st.connectias.feature.privacy.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.ble1st.connectias.feature.privacy.models.DNSStatus
import com.ble1st.connectias.feature.privacy.models.NetworkPrivacyInfo
import com.ble1st.connectias.feature.privacy.models.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides network privacy information including DNS, VPN, and connection status.
 * 
 * This provider uses ConnectivityManager APIs (getActiveNetwork, getNetworkCapabilities)
 * and requires the Android permission android.permission.ACCESS_NETWORK_STATE to avoid
 * SecurityException at runtime.
 */
@Singleton
class NetworkPrivacyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    /**
     * Gets current network privacy information.
     * This is a synchronous cached-state read that should be called off the main thread
     * if used from UI code.
     */
    suspend fun getNetworkPrivacyInfo(): NetworkPrivacyInfo {
        return try {
            val connectivityMgr = connectivityManager ?: return NetworkPrivacyInfo(
                dnsStatus = DNSStatus.UNKNOWN,
                vpnActive = false,
                networkType = NetworkType.UNKNOWN,
                isConnected = false,
                privateDnsEnabled = false
            )
            
            val activeNetwork = connectivityMgr.activeNetwork
            val networkCapabilities = activeNetwork?.let {
                connectivityMgr.getNetworkCapabilities(it)
            }

            val isConnected = networkCapabilities != null
            val vpnActive = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val networkType = determineNetworkType(networkCapabilities)
            val privateDnsEnabled = isPrivateDnsEnabled(activeNetwork, connectivityMgr)
            val dnsStatus = if (privateDnsEnabled) {
                DNSStatus.PRIVATE
            } else {
                DNSStatus.STANDARD
            }

            NetworkPrivacyInfo(
                dnsStatus = dnsStatus,
                vpnActive = vpnActive,
                networkType = networkType,
                isConnected = isConnected,
                privateDnsEnabled = privateDnsEnabled
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting network privacy info")
            NetworkPrivacyInfo(
                dnsStatus = DNSStatus.UNKNOWN,
                vpnActive = false,
                networkType = NetworkType.UNKNOWN,
                isConnected = false,
                privateDnsEnabled = false
            )
        }
    }

    private fun determineNetworkType(capabilities: NetworkCapabilities?): NetworkType {
        if (capabilities == null) {
            return NetworkType.NONE
        }

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }

    private fun isPrivateDnsEnabled(network: Network?, connectivityMgr: ConnectivityManager?): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activeNetwork = network ?: connectivityMgr?.activeNetwork
                if (activeNetwork != null) {
                    val linkProperties: LinkProperties? = connectivityMgr?.getLinkProperties(activeNetwork)
                    linkProperties?.isPrivateDnsActive == true
                } else {
                    // Fallback to false if network is not available
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "Error checking private DNS status")
            false
        }
    }
}

