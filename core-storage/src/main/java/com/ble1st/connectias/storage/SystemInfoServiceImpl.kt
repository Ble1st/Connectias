package com.ble1st.connectias.storage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.ble1st.connectias.api.CpuInfo
import com.ble1st.connectias.api.DeviceInfo
import com.ble1st.connectias.api.MemoryInfo
import com.ble1st.connectias.api.NetworkInfo
import com.ble1st.connectias.api.SystemInfoService
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

class SystemInfoServiceImpl(
    private val context: Context
) : SystemInfoService {
    
    override fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            architecture = Build.SUPPORTED_ABIS.joinToString(", ")
        )
    }
    
    override fun getCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxFrequency = getMaxCpuFrequency()
        val currentFrequency = getCurrentCpuFrequency()
        
        return CpuInfo(
            cores = cores,
            maxFrequency = maxFrequency,
            currentFrequency = currentFrequency,
            architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        )
    }
    
    override fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        return MemoryInfo(
            totalMemory = totalMemory,
            availableMemory = freeMemory,
            usedMemory = usedMemory
        )
    }
    
    override fun getNetworkInfo(): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        val isConnected = capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
        
        val connectionType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Unknown"
        }
        
        val ipAddress = getLocalIpAddress()
        val macAddress = getMacAddress()
        
        return NetworkInfo(
            isConnected = isConnected,
            connectionType = connectionType,
            ipAddress = ipAddress,
            macAddress = macAddress
        )
    }
    
    private fun getMaxCpuFrequency(): Long {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (file.exists()) {
                file.readText().trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getCurrentCpuFrequency(): Long {
        return try {
            val file = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (file.exists()) {
                file.readText().trim().toLongOrNull() ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getMacAddress(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isLoopback && networkInterface.isUp) {
                    val macBytes = networkInterface.hardwareAddress
                    if (macBytes != null) {
                        val mac = macBytes.joinToString(":") { "%02x".format(it) }
                        return mac
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
