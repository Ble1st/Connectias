package com.ble1st.connectias.feature.privacy.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.ble1st.connectias.feature.privacy.models.DNSStatus
import com.ble1st.connectias.feature.privacy.models.NetworkPrivacyInfo
import com.ble1st.connectias.feature.privacy.models.NetworkType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides network privacy information including DNS, VPN, and connection status.
 */
@Singleton
class NetworkPrivacyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Gets current network privacy information.
     * This method performs blocking I/O and should be called from a background thread.
     */
    suspend fun getNetworkPrivacyInfo(): NetworkPrivacyInfo = withContext(Dispatchers.IO) {
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }

            val isConnected = networkCapabilities != null
            val vpnActive = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val networkType = determineNetworkType(networkCapabilities)
            val dnsStatus = determineDNSStatus()
            val privateDnsEnabled = isPrivateDnsEnabled()

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

    private fun determineDNSStatus(): DNSStatus {
        return try {
            if (isPrivateDnsEnabled()) {
                DNSStatus.PRIVATE
            } else {
                DNSStatus.STANDARD
            }
        } catch (e: Exception) {
            Timber.w(e, "Error determining DNS status")
            DNSStatus.UNKNOWN
        }
    }

    private fun isPrivateDnsEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java)
                val privateDnsMode = getMethod.invoke(null, "net.dns_mode") as? String
                privateDnsMode != null && privateDnsMode != "off"
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.w(e, "Error checking private DNS status")
            false
        }
    }
}

