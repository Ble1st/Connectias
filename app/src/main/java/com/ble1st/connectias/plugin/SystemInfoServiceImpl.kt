package com.ble1st.connectias.plugin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.ble1st.connectias.api.SystemInfoService
import com.ble1st.connectias.api.DeviceInfo
import com.ble1st.connectias.api.CpuInfo
import com.ble1st.connectias.api.MemoryInfo
import com.ble1st.connectias.api.NetworkInfo
import timber.log.Timber
import java.net.NetworkInterface
import java.util.Collections

class SystemInfoServiceImpl(
    private val context: Context,
    private val pluginId: String
) : SystemInfoService {
    
    override fun getDeviceInfo(): DeviceInfo {
        return try {
            DeviceInfo(
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                sdkVersion = Build.VERSION.SDK_INT,
                architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            )
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting device info")
            DeviceInfo("unknown", "unknown", "unknown", 0, "unknown")
        }
    }
    
    override fun getCpuInfo(): CpuInfo {
        return try {
            val runtime = Runtime.getRuntime()
            val cores = runtime.availableProcessors()
            
            CpuInfo(
                cores = cores,
                maxFrequency = 0L, // Not easily accessible without native code
                currentFrequency = 0L, // Not easily accessible without native code
                architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            )
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting CPU info")
            CpuInfo(0, 0L, 0L, "unknown")
        }
    }
    
    override fun getMemoryInfo(): MemoryInfo {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            MemoryInfo(
                totalMemory = totalMemory,
                availableMemory = freeMemory,
                usedMemory = usedMemory
            )
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting memory info")
            MemoryInfo(0L, 0L, 0L)
        }
    }
    
    override fun getNetworkInfo(): NetworkInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            val isConnected = capabilities != null
            val connectionType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }
            
            val ipAddress = getLocalIpAddress()
            val macAddress = getMacAddress()
            
            NetworkInfo(
                isConnected = isConnected,
                connectionType = connectionType,
                ipAddress = ipAddress,
                macAddress = macAddress
            )
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting network info")
            NetworkInfo(false, "Unknown", null, null)
        }
    }
    
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting IP address")
            null
        }
    }
    
    private fun getMacAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.name.equals("wlan0", ignoreCase = true)) {
                    val macBytes = networkInterface.hardwareAddress
                    if (macBytes != null) {
                        val macString = macBytes.joinToString(":") { "%02x".format(it) }
                        return macString
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error getting MAC address")
            null
        }
    }
}