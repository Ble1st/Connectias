package com.ble1st.connectias.feature.deviceinfo.process

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Debug
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for process monitoring.
 */
@Singleton
class ProcessMonitorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    /**
     * Gets running processes information.
     */
    suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        try {
            val runningProcesses = activityManager.runningAppProcesses ?: return@withContext emptyList()
            
            runningProcesses.mapNotNull { processInfo ->
                val pid = processInfo.pid
                
                // Get memory info for this process
                val memoryInfoList = try {
                    activityManager.getProcessMemoryInfo(intArrayOf(pid))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get memory info for PID $pid")
                    null
                }
                
                val memoryInfo = memoryInfoList?.firstOrNull() ?: return@mapNotNull null
                
                val appName = try {
                    val packageInfo = packageManager.getPackageInfo(processInfo.processName, 0)
                    val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    processInfo.processName
                }
                
                val isSystemApp = try {
                    val appInfo = packageManager.getApplicationInfo(processInfo.processName, 0) ?: return@mapNotNull null
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) {
                    false
                }
                
                ProcessInfo(
                    pid = pid,
                    processName = processInfo.processName,
                    appName = appName,
                    memoryUsage = memoryInfo.totalPss * 1024L, // Convert to bytes
                    importance = processInfo.importance,
                    isSystemApp = isSystemApp
                )
            }.sortedByDescending { it.memoryUsage }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get running processes")
            emptyList()
        }
    }

    /**
     * Gets memory statistics.
     */
    suspend fun getMemoryStats(): MemoryStats = withContext(Dispatchers.IO) {
        try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            MemoryStats(
                totalMemory = memInfo.totalMem,
                availableMemory = memInfo.availMem,
                usedMemory = memInfo.totalMem - memInfo.availMem,
                threshold = memInfo.threshold,
                lowMemory = memInfo.lowMemory
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get memory stats")
            MemoryStats.default()
        }
    }
}

/**
 * Process information.
 */
data class ProcessInfo(
    val pid: Int,
    val processName: String,
    val appName: String,
    val memoryUsage: Long,
    val importance: Int,
    val isSystemApp: Boolean
)

/**
 * Memory statistics.
 */
data class MemoryStats(
    val totalMemory: Long,
    val availableMemory: Long,
    val usedMemory: Long,
    val threshold: Long,
    val lowMemory: Boolean
) {
    companion object {
        fun default() = MemoryStats(
            totalMemory = 0,
            availableMemory = 0,
            usedMemory = 0,
            threshold = 0,
            lowMemory = false
        )
    }
}

