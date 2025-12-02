package com.ble1st.connectias.feature.privacy.appmonitor

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for monitoring app network usage.
 *
 * Features:
 * - Per-app network usage tracking
 * - WiFi vs Mobile data breakdown
 * - Background vs foreground usage
 * - Data usage alerts
 * - Historical tracking
 */
@Singleton
class AppNetworkMonitorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) 
        as NetworkStatsManager
    private val packageManager = context.packageManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) 
        as? TelephonyManager

    private val _appUsageStats = MutableStateFlow<List<AppNetworkUsage>>(emptyList())
    val appUsageStats: StateFlow<List<AppNetworkUsage>> = _appUsageStats.asStateFlow()

    private val _alerts = MutableStateFlow<List<DataAlert>>(emptyList())
    val alerts: StateFlow<List<DataAlert>> = _alerts.asStateFlow()

    /**
     * Gets network usage for all apps in a time period.
     */
    suspend fun getAppNetworkUsage(
        startTime: Long = getStartOfDay(),
        endTime: Long = System.currentTimeMillis()
    ): List<AppNetworkUsage> = withContext(Dispatchers.IO) {
        val usageMap = mutableMapOf<Int, AppNetworkUsage>()

        try {
            // WiFi usage
            queryNetworkStats(ConnectivityManager.TYPE_WIFI, startTime, endTime)?.use { bucket ->
                while (bucket.hasNextBucket()) {
                    val stats = NetworkStats.Bucket()
                    bucket.getNextBucket(stats)

                    val uid = stats.uid
                    val existing = usageMap[uid] ?: createEmptyUsage(uid)
                    usageMap[uid] = existing.copy(
                        wifiRxBytes = existing.wifiRxBytes + stats.rxBytes,
                        wifiTxBytes = existing.wifiTxBytes + stats.txBytes
                    )
                }
            }

            // Mobile usage
            queryNetworkStats(ConnectivityManager.TYPE_MOBILE, startTime, endTime)?.use { bucket ->
                while (bucket.hasNextBucket()) {
                    val stats = NetworkStats.Bucket()
                    bucket.getNextBucket(stats)

                    val uid = stats.uid
                    val existing = usageMap[uid] ?: createEmptyUsage(uid)
                    usageMap[uid] = existing.copy(
                        mobileRxBytes = existing.mobileRxBytes + stats.rxBytes,
                        mobileTxBytes = existing.mobileTxBytes + stats.txBytes
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying network stats")
        }

        val result = usageMap.values
            .filter { it.totalBytes > 0 }
            .sortedByDescending { it.totalBytes }

        _appUsageStats.value = result
        result
    }

    /**
     * Gets network usage for a specific app.
     */
    suspend fun getAppUsage(
        packageName: String,
        startTime: Long = getStartOfMonth(),
        endTime: Long = System.currentTimeMillis()
    ): AppNetworkUsage? = withContext(Dispatchers.IO) {
        try {
            val uid = packageManager.getApplicationInfo(packageName, 0).uid
            val usage = createEmptyUsage(uid)

            // WiFi
            queryNetworkStats(ConnectivityManager.TYPE_WIFI, startTime, endTime, uid)?.use { bucket ->
                var rxBytes = 0L
                var txBytes = 0L
                while (bucket.hasNextBucket()) {
                    val stats = NetworkStats.Bucket()
                    bucket.getNextBucket(stats)
                    rxBytes += stats.rxBytes
                    txBytes += stats.txBytes
                }
                usage.copy(wifiRxBytes = rxBytes, wifiTxBytes = txBytes)
            }

            // Mobile
            queryNetworkStats(ConnectivityManager.TYPE_MOBILE, startTime, endTime, uid)?.use { bucket ->
                var rxBytes = 0L
                var txBytes = 0L
                while (bucket.hasNextBucket()) {
                    val stats = NetworkStats.Bucket()
                    bucket.getNextBucket(stats)
                    rxBytes += stats.rxBytes
                    txBytes += stats.txBytes
                }
                usage.copy(mobileRxBytes = rxBytes, mobileTxBytes = txBytes)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting app usage for $packageName")
            null
        }
    }

    /**
     * Gets daily usage breakdown.
     */
    suspend fun getDailyUsage(
        days: Int = 7
    ): List<DailyUsage> = withContext(Dispatchers.IO) {
        val dailyUsage = mutableListOf<DailyUsage>()
        val calendar = Calendar.getInstance()

        repeat(days) { day ->
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -day)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val endTime = calendar.timeInMillis

            var wifiBytes = 0L
            var mobileBytes = 0L

            try {
                queryNetworkStats(ConnectivityManager.TYPE_WIFI, startTime, endTime)?.use { bucket ->
                    while (bucket.hasNextBucket()) {
                        val stats = NetworkStats.Bucket()
                        bucket.getNextBucket(stats)
                        wifiBytes += stats.rxBytes + stats.txBytes
                    }
                }

                queryNetworkStats(ConnectivityManager.TYPE_MOBILE, startTime, endTime)?.use { bucket ->
                    while (bucket.hasNextBucket()) {
                        val stats = NetworkStats.Bucket()
                        bucket.getNextBucket(stats)
                        mobileBytes += stats.rxBytes + stats.txBytes
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error getting daily usage for day -$day")
            }

            dailyUsage.add(
                DailyUsage(
                    date = startTime,
                    wifiBytes = wifiBytes,
                    mobileBytes = mobileBytes
                )
            )
        }

        dailyUsage.reversed()
    }

    /**
     * Gets top data consuming apps.
     */
    suspend fun getTopApps(limit: Int = 10): List<AppNetworkUsage> = withContext(Dispatchers.IO) {
        getAppNetworkUsage().take(limit)
    }

    /**
     * Sets a data usage alert.
     */
    fun setAlert(alert: DataAlert) {
        _alerts.update { it.filter { a -> a.id != alert.id } + alert }
    }

    /**
     * Removes an alert.
     */
    fun removeAlert(alertId: String) {
        _alerts.update { it.filter { a -> a.id != alertId } }
    }

    /**
     * Checks alerts against current usage.
     */
    suspend fun checkAlerts(): List<AlertTriggered> = withContext(Dispatchers.IO) {
        val triggered = mutableListOf<AlertTriggered>()
        val currentUsage = getAppNetworkUsage(startTime = getStartOfMonth())

        for (alert in _alerts.value) {
            val usage = if (alert.packageName != null) {
                currentUsage.find { it.packageName == alert.packageName }?.totalBytes ?: 0L
            } else {
                currentUsage.sumOf { it.totalBytes }
            }

            if (usage >= alert.thresholdBytes) {
                triggered.add(
                    AlertTriggered(
                        alert = alert,
                        currentUsage = usage,
                        triggeredAt = System.currentTimeMillis()
                    )
                )
            }
        }

        triggered
    }

    private fun queryNetworkStats(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        uid: Int? = null
    ): NetworkStats? {
        return try {
            if (uid != null) {
                networkStatsManager.queryDetailsForUid(networkType, null, startTime, endTime, uid)
            } else {
                networkStatsManager.querySummary(networkType, null, startTime, endTime)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error querying network stats")
            null
        }
    }

    private fun createEmptyUsage(uid: Int): AppNetworkUsage {
        val packages = packageManager.getPackagesForUid(uid)
        val packageName = packages?.firstOrNull() ?: "unknown"
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }

        return AppNetworkUsage(
            uid = uid,
            packageName = packageName,
            appName = appName
        )
    }

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getStartOfMonth(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/**
 * App network usage data.
 */
@Serializable
data class AppNetworkUsage(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val wifiRxBytes: Long = 0,
    val wifiTxBytes: Long = 0,
    val mobileRxBytes: Long = 0,
    val mobileTxBytes: Long = 0
) {
    val wifiTotalBytes: Long get() = wifiRxBytes + wifiTxBytes
    val mobileTotalBytes: Long get() = mobileRxBytes + mobileTxBytes
    val totalBytes: Long get() = wifiTotalBytes + mobileTotalBytes
    val totalRxBytes: Long get() = wifiRxBytes + mobileRxBytes
    val totalTxBytes: Long get() = wifiTxBytes + mobileTxBytes

    fun formatTotalBytes(): String = formatBytes(totalBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
                bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
                bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
}

/**
 * Daily usage data.
 */
@Serializable
data class DailyUsage(
    val date: Long,
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long get() = wifiBytes + mobileBytes

    fun formatDate(): String {
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        return formatter.format(Date(date))
    }
}

/**
 * Data usage alert.
 */
@Serializable
data class DataAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String? = null,
    val thresholdBytes: Long,
    val period: AlertPeriod = AlertPeriod.MONTHLY
)

/**
 * Alert period.
 */
enum class AlertPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}

/**
 * Triggered alert info.
 */
@Serializable
data class AlertTriggered(
    val alert: DataAlert,
    val currentUsage: Long,
    val triggeredAt: Long
)
