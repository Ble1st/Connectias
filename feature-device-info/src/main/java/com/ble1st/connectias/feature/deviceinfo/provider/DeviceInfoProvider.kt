package com.ble1st.connectias.feature.deviceinfo.provider

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceInfo(
    val osInfo: OSInfo,
    val cpuInfo: CPUInfo,
    val ramInfo: RAMInfo,
    val storageInfo: StorageInfo,
    val networkInfo: NetworkInfo
)

data class OSInfo(
    val version: String,
    val sdkVersion: Int,
    val manufacturer: String,
    val model: String,
    val brand: String
)

data class CPUInfo(
    val cores: Int,
    val architecture: String,
    val frequency: Long // MHz
)

data class RAMInfo(
    val total: Long, // bytes
    val available: Long, // bytes
    val used: Long, // bytes
    val percentageUsed: Float
)

data class StorageInfo(
    val total: Long, // bytes
    val available: Long, // bytes
    val used: Long, // bytes
    val percentageUsed: Float
)

data class NetworkInfo(
    val ipAddress: String?,
    val macAddress: String?
)

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            osInfo = getOSInfo(),
            cpuInfo = getCPUInfo(),
            ramInfo = getRAMInfo(),
            storageInfo = getStorageInfo(),
            networkInfo = getNetworkInfo()
        )
    }

    private fun getOSInfo(): OSInfo {
        return OSInfo(
            version = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            brand = Build.BRAND
        )
    }

    private fun getCPUInfo(): CPUInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val frequency = getCPUFrequency()

        return CPUInfo(
            cores = cores,
            architecture = architecture,
            frequency = frequency
        )
    }

    private fun getCPUFrequency(): Long {
        return try {
            val file = java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (file.exists()) {
                file.readText().trim().toLongOrNull()?.div(1000) ?: 0L
            } else {
                0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun getRAMInfo(): RAMInfo {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMemory = memInfo.totalMem
        val availableMemory = memInfo.availMem
        val usedMemory = totalMemory - availableMemory
        val percentageUsed = if (totalMemory > 0) {
            (usedMemory.toFloat() / totalMemory.toFloat()) * 100f
        } else {
            0f
        }

        return RAMInfo(
            total = totalMemory,
            available = availableMemory,
            used = usedMemory,
            percentageUsed = percentageUsed
        )
    }

    private fun getStorageInfo(): StorageInfo {
        val stat = StatFs(Environment.getDataDirectory().path)

        val totalSpace = stat.blockCountLong * stat.blockSizeLong
        val availableSpace = stat.availableBlocksLong * stat.blockSizeLong
        val usedSpace = totalSpace - availableSpace
        val percentageUsed = if (totalSpace > 0) {
            (usedSpace.toFloat() / totalSpace.toFloat()) * 100f
        } else {
            0f
        }

        return StorageInfo(
            total = totalSpace,
            available = availableSpace,
            used = usedSpace,
            percentageUsed = percentageUsed
        )
    }

    private fun getNetworkInfo(): NetworkInfo {
        val ipAddress = getLocalIPAddress()
        val macAddress = getMACAddress()

        return NetworkInfo(
            ipAddress = ipAddress,
            macAddress = macAddress
        )
    }

    private fun getLocalIPAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
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

    private fun getMACAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null) {
                    val sb = StringBuilder()
                    for (i in mac.indices) {
                        sb.append(String.format("%02X%s", mac[i], if (i < mac.size - 1) ":" else ""))
                    }
                    return sb.toString()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

