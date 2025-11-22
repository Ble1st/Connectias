package com.ble1st.connectias.feature.deviceinfo.provider

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import android.os.StatFs
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.parcelize.Parcelize
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Parcelize
data class DeviceInfo(
    val osInfo: OSInfo,
    val cpuInfo: CPUInfo,
    val ramInfo: RAMInfo,
    val storageInfo: StorageInfo,
    val networkInfo: NetworkInfo
) : Parcelable

@Parcelize
data class OSInfo(
    val version: String,
    val sdkVersion: Int,
    val manufacturer: String,
    val model: String,
    val brand: String
) : Parcelable

@Parcelize
data class CPUInfo(
    val cores: Int,
    val architecture: String,
    val frequency: Long // MHz
) : Parcelable

@Parcelize
data class RAMInfo(
    val total: Long, // bytes
    val available: Long, // bytes
    val used: Long, // bytes
    val percentageUsed: Float
) : Parcelable

@Parcelize
data class StorageInfo(
    val total: Long, // bytes
    val available: Long, // bytes
    val used: Long, // bytes
    val percentageUsed: Float
) : Parcelable

@Parcelize
data class NetworkInfo(
    val ipAddress: String?,
    val androidId: String? // Replaces MAC address for privacy compliance
) : Parcelable

@Singleton
class DeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: throw IllegalStateException("ActivityManager not available")
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

    /**
     * Reads the maximum CPU frequency from /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq.
     * 
     * This method may return 0L when:
     * - The file is missing or inaccessible (e.g., SELinux restrictions)
     * - OEM variations in file system structure
     * - Non-linux-like devices
     * 
     * Callers should treat 0L as "unknown/unavailable" and not as a valid frequency.
     * The value is returned in MHz.
     * 
     * @return CPU frequency in MHz, or 0L if unavailable
     */
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
        // Use ANDROID_ID instead of MAC address for privacy compliance (Android 10+)
        val androidId = getAndroidId()

        return NetworkInfo(
            ipAddress = ipAddress,
            androidId = androidId
        )
    }

    /**
     * Gets the local IP address using ConnectivityManager (Android 10+ compatible).
     * Falls back to NetworkInterface if ConnectivityManager is unavailable.
     * 
     * This method avoids the "Operation not permitted" error on Android 10+ by using
     * the recommended ConnectivityManager API instead of direct NetworkInterface access.
     * 
     * @return Local IPv4 address, or null if unavailable
     */
    private fun getLocalIPAddress(): String? {
        return try {
            // Try ConnectivityManager first (recommended for Android 10+)
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    val linkProperties = connectivityManager.getLinkProperties(network)
                    linkProperties?.linkAddresses?.forEach { linkAddress ->
                        val address = linkAddress.address
                        if (address is InetAddress && !address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
            
            // Fallback to NetworkInterface (may fail on Android 10+ due to permissions)
            try {
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
            } catch (e: SecurityException) {
                // Ignore SecurityException on Android 10+ - expected behavior
                // The error "Operation not permitted" is normal for non-privileged apps
            }
            
            null
        } catch (e: Exception) {
            // Silently return null if IP address cannot be determined
            null
        }
    }

    /**
     * Gets the Android ID as a privacy-compliant device identifier.
     * This replaces MAC address collection which is unreliable and disallowed
     * for non-privileged apps on Android 10+.
     * 
     * @return Android ID string, or null if unavailable
     */
    private fun getAndroidId(): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                .takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}

