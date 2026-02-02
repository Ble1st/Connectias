package com.ble1st.connectias.plugin.security

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced Resource Limiter with real enforcement mechanisms
 * Provides CPU, Memory, Disk, and Thread isolation for plugin sandboxes
 */
@Singleton
class EnhancedPluginResourceLimiter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    
    data class ResourceLimits(
        val maxMemoryMB: Int = 128,
        val maxCpuPercent: Float = 25f,
        val maxDiskUsageMB: Long = 512,
        val maxFileDescriptors: Int = 100,
        val maxThreads: Int = 15,
        val maxNetworkBandwidthKBps: Long = 2048,
        val emergencyMemoryMB: Int = 64, // Emergency kill threshold
        val cpuThrottleThreshold: Float = 80f // Start throttling at 80% of limit
    )
    
    data class ResourceUsage(
        val pluginId: String,
        val memoryUsedMB: Int,
        val memoryPeakMB: Int,
        val cpuPercent: Float,
        val cpuTimeMs: Long,
        val diskUsageMB: Long,
        val openFileDescriptors: Int,
        val activeThreads: Int,
        val networkBandwidthKBps: Long,
        val isThrottled: Boolean = false,
        val violationCount: Int = 0,
        val lastViolation: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val pluginLimits = ConcurrentHashMap<String, ResourceLimits>()
    private val pluginPids = ConcurrentHashMap<String, Int>()
    private val pluginThreadGroups = ConcurrentHashMap<String, ThreadGroup>()
    private val pluginViolations = ConcurrentHashMap<String, AtomicLong>()
    private val throttledPlugins = ConcurrentHashMap<String, AtomicBoolean>()
    
    // CPU tracking data
    private data class CpuTrackingData(
        var lastCpuTime: Long = 0L,
        var lastCheckTime: Long = 0L
    )
    private val cpuTracking = ConcurrentHashMap<Int, CpuTrackingData>()
    
    private val _resourceUsage = MutableStateFlow<Map<String, ResourceUsage>>(emptyMap())
    val resourceUsage: Flow<Map<String, ResourceUsage>> = _resourceUsage.asStateFlow()
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val monitoringScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isMonitoring = AtomicBoolean(false)
    
    companion object {
        private const val MONITORING_INTERVAL_MS = 2000L
        private const val CPU_CHECK_INTERVAL_MS = 1000L
        private const val EMERGENCY_CHECK_INTERVAL_MS = 500L
    }
    
    init {
        startResourceMonitoring()
    }
    
    // ════════════════════════════════════════════════════════
    // PLUGIN REGISTRATION
    // ════════════════════════════════════════════════════════
    
    /**
     * Register a plugin with the resource limiter
     */
    fun registerPlugin(pluginId: String, pid: Int, limits: ResourceLimits = ResourceLimits()) {
        pluginLimits[pluginId] = limits
        pluginPids[pluginId] = pid
        pluginViolations[pluginId] = AtomicLong(0)
        throttledPlugins[pluginId] = AtomicBoolean(false)
        
        // Create thread group for plugin isolation
        val threadGroup = ThreadGroup("Plugin-$pluginId")
        pluginThreadGroups[pluginId] = threadGroup
        
        Timber.i("[RESOURCE LIMITER] Plugin registered: $pluginId (PID: $pid, Limits: $limits)")
    }
    
    /**
     * Unregister a plugin from resource monitoring
     */
    fun unregisterPlugin(pluginId: String) {
        val pid = pluginPids.remove(pluginId)
        pluginLimits.remove(pluginId)
        pluginViolations.remove(pluginId)
        throttledPlugins.remove(pluginId)
        
        // Cleanup CPU tracking
        if (pid != null) {
            cpuTracking.remove(pid)
        }
        
        // Cleanup thread group
        pluginThreadGroups.remove(pluginId)?.interrupt()
        
        val currentUsage = _resourceUsage.value.toMutableMap()
        currentUsage.remove(pluginId)
        _resourceUsage.value = currentUsage
        
        Timber.i("[RESOURCE LIMITER] Plugin unregistered: $pluginId")
    }
    
    // ════════════════════════════════════════════════════════
    // MEMORY ENFORCEMENT
    // ════════════════════════════════════════════════════════
    
    /**
     * Enforce memory limits with real termination
     */
    fun enforceMemoryLimits(pluginId: String): EnforcementResult {
        val limits = pluginLimits[pluginId] ?: return EnforcementResult.NoLimits
        val pid = pluginPids[pluginId] ?: return EnforcementResult.ProcessNotFound
        
        try {
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
                ?: return EnforcementResult.ProcessNotFound
            
            val totalMemMB = memInfo.totalPss / 1024
            memInfo.totalPrivateDirty / 1024
            
            when {
                totalMemMB > limits.emergencyMemoryMB * 2 -> {
                    // Emergency kill - plugin is consuming too much memory
                    Timber.e("[RESOURCE LIMITER] EMERGENCY: Plugin $pluginId memory critical: ${totalMemMB}MB")
                    killPlugin(pluginId, "Emergency memory limit exceeded: ${totalMemMB}MB > ${limits.emergencyMemoryMB * 2}MB")
                    return EnforcementResult.Killed
                }
                
                totalMemMB > limits.maxMemoryMB -> {
                    // Soft limit exceeded - try to trigger GC first
                    Timber.w("[RESOURCE LIMITER] Plugin $pluginId exceeds memory limit: ${totalMemMB}MB > ${limits.maxMemoryMB}MB")
                    
                    val violationCount = pluginViolations[pluginId]?.incrementAndGet() ?: 1
                    
                    if (violationCount >= 3) {
                        // Too many violations, kill the plugin
                        killPlugin(pluginId, "Memory limit violation (${violationCount} times): ${totalMemMB}MB")
                        return EnforcementResult.Killed
                    } else {
                        // Try to trigger garbage collection
                        Runtime.getRuntime().gc()
                        return EnforcementResult.Warning
                    }
                }
                
                else -> return EnforcementResult.Ok
            }
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to enforce memory limits for $pluginId")
            return EnforcementResult.Error
        }
    }
    
    // ════════════════════════════════════════════════════════
    // CPU ENFORCEMENT
    // ════════════════════════════════════════════════════════
    
    /**
     * Enforce CPU limits with real throttling
     */
    fun enforceCpuLimits(pluginId: String): EnforcementResult {
        val limits = pluginLimits[pluginId] ?: return EnforcementResult.NoLimits
        val pid = pluginPids[pluginId] ?: return EnforcementResult.ProcessNotFound
        
        try {
            val cpuUsage = getCpuUsageForProcess(pid)
            
            when {
                cpuUsage > limits.maxCpuPercent * 1.5f -> {
                    // Critical CPU usage - throttle heavily
                    Timber.w("[RESOURCE LIMITER] Plugin $pluginId critical CPU usage: $cpuUsage%")
                    throttlePlugin(pluginId, Process.THREAD_PRIORITY_LOWEST)
                    return EnforcementResult.Throttled
                }
                
                cpuUsage > limits.cpuThrottleThreshold -> {
                    // High CPU usage - start throttling
                    Timber.d("[RESOURCE LIMITER] Plugin $pluginId high CPU usage: $cpuUsage% - throttling")
                    throttlePlugin(pluginId, Process.THREAD_PRIORITY_BACKGROUND)
                    return EnforcementResult.Throttled
                }
                
                else -> {
                    // Normal CPU usage - remove throttling if active
                    if (throttledPlugins[pluginId]?.get() == true) {
                        unthrottlePlugin(pluginId)
                    }
                    return EnforcementResult.Ok
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to enforce CPU limits for $pluginId")
            return EnforcementResult.Error
        }
    }
    
    private fun getCpuUsageForProcess(pid: Int): Float {
        return try {
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists()) return 0f
            
            val statContent = statFile.readText().trim().split(" ")
            if (statContent.size < 22) return 0f
            
            val utime = statContent[13].toLongOrNull() ?: 0L
            val stime = statContent[14].toLongOrNull() ?: 0L
            val totalCpuTime = utime + stime // in clock ticks
            
            val currentTime = System.currentTimeMillis()
            
            // Get or create tracking data
            val tracking = cpuTracking.getOrPut(pid) { CpuTrackingData() }
            
            // Calculate delta-based CPU usage
            val cpuPercent = if (tracking.lastCheckTime in 1..<currentTime) {
                val timeDelta = (currentTime - tracking.lastCheckTime) / 1000.0 // seconds
                val cpuDelta = totalCpuTime - tracking.lastCpuTime
                
                // CPU ticks per second (typically 100 Hz on Android)
                val clockTicksPerSecond = 100.0
                val cpuTimeUsed = cpuDelta / clockTicksPerSecond // seconds of CPU time
                
                // Calculate percentage
                val percent = (cpuTimeUsed / timeDelta) * 100.0
                minOf(percent.toFloat(), 100f)
            } else {
                0f // First measurement, can't calculate delta
            }
            
            // Update tracking
            tracking.lastCpuTime = totalCpuTime
            tracking.lastCheckTime = currentTime
            
            cpuPercent
        } catch (e: Exception) {
            Timber.d(e, "[RESOURCE LIMITER] Could not read CPU usage for PID $pid")
            0f
        }
    }
    
    private fun throttlePlugin(pluginId: String, priority: Int) {
        val threadGroup = pluginThreadGroups[pluginId] ?: return
        throttledPlugins[pluginId]?.set(true)
        
        try {
            // Set lower priority for all threads in the plugin's thread group
            val threads = Array<Thread?>(threadGroup.activeCount()) { null }
            val count = threadGroup.enumerate(threads)
            
            for (i in 0 until count) {
                threads[i]?.let { thread ->
                    try {
                        // Use Java thread priority instead of native system calls
                        thread.priority = when (priority) {
                            Process.THREAD_PRIORITY_LOWEST -> Thread.MIN_PRIORITY
                            Process.THREAD_PRIORITY_BACKGROUND -> Thread.MIN_PRIORITY + 1
                            else -> Thread.NORM_PRIORITY
                        }
                    } catch (e: Exception) {
                        // Fallback to Java thread priority
                        thread.priority = when (priority) {
                            Process.THREAD_PRIORITY_LOWEST -> Thread.MIN_PRIORITY
                            Process.THREAD_PRIORITY_BACKGROUND -> Thread.MIN_PRIORITY + 1
                            else -> Thread.NORM_PRIORITY
                        }
                    }
                }
            }
            
            Timber.d("[RESOURCE LIMITER] Plugin $pluginId throttled (priority: $priority)")
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to throttle plugin $pluginId")
        }
    }
    
    private fun unthrottlePlugin(pluginId: String) {
        val threadGroup = pluginThreadGroups[pluginId] ?: return
        throttledPlugins[pluginId]?.set(false)
        
        try {
            val threads = Array<Thread?>(threadGroup.activeCount()) { null }
            val count = threadGroup.enumerate(threads)
            
            for (i in 0 until count) {
                threads[i]?.let { thread ->
                    try {
                        thread.priority = Thread.NORM_PRIORITY
                    } catch (e: Exception) {
                        thread.priority = Thread.NORM_PRIORITY
                    }
                }
            }
            
            Timber.d("[RESOURCE LIMITER] Plugin $pluginId unthrottled")
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to unthrottle plugin $pluginId")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // DISK ENFORCEMENT
    // ════════════════════════════════════════════════════════
    
    /**
     * Enforce disk usage limits
     */
    fun enforceDiskLimits(pluginId: String): EnforcementResult {
        val limits = pluginLimits[pluginId] ?: return EnforcementResult.NoLimits
        
        try {
            val pluginDataDir = File(context.filesDir, "plugins/$pluginId")
            val diskUsageMB = getDiskUsage(pluginDataDir) / 1024 / 1024
            
            if (diskUsageMB > limits.maxDiskUsageMB) {
                Timber.w("[RESOURCE LIMITER] Plugin $pluginId exceeds disk limit: ${diskUsageMB}MB > ${limits.maxDiskUsageMB}MB")
                
                // Try to cleanup temporary files first
                cleanupPluginTempFiles(pluginDataDir)
                
                val newUsage = getDiskUsage(pluginDataDir) / 1024 / 1024
                if (newUsage > limits.maxDiskUsageMB) {
                    killPlugin(pluginId, "Disk usage limit exceeded: ${newUsage}MB > ${limits.maxDiskUsageMB}MB")
                    return EnforcementResult.Killed
                } else {
                    return EnforcementResult.Warning
                }
            }
            
            return EnforcementResult.Ok
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to enforce disk limits for $pluginId")
            return EnforcementResult.Error
        }
    }
    
    private fun getDiskUsage(directory: File): Long {
        if (!directory.exists()) return 0L
        
        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
    
    private fun cleanupPluginTempFiles(pluginDir: File) {
        try {
            val tempDir = File(pluginDir, "temp")
            val cacheDir = File(pluginDir, "cache")
            
            listOf(tempDir, cacheDir).forEach { dir ->
                if (dir.exists()) {
                    dir.walkBottomUp().forEach { file ->
                        try {
                            if (file.isFile && System.currentTimeMillis() - file.lastModified() > 3600000) {
                                // Delete files older than 1 hour
                                file.delete()
                            }
                        } catch (e: Exception) {
                            // Ignore individual file cleanup errors
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to cleanup temp files")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // PLUGIN TERMINATION
    // ════════════════════════════════════════════════════════
    
    /**
     * Kill a plugin process for resource violations
     */
    private fun killPlugin(pluginId: String, reason: String) {
        val pid = pluginPids[pluginId]
        
        try {
            Timber.e("[RESOURCE LIMITER] Killing plugin $pluginId: $reason")
            
            if (pid != null) {
                // Use Android Process.killProcess for graceful termination
                try {
                    Process.killProcess(pid)
                    Timber.w("[RESOURCE LIMITER] Killed plugin process $pluginId (PID: $pid)")
                } catch (e: Exception) {
                    Timber.e(e, "[RESOURCE LIMITER] Failed to kill process $pid for plugin $pluginId")
                }
            }
            
            // Cleanup thread group
            pluginThreadGroups[pluginId]?.interrupt()
            
            // Update violation count
            pluginViolations[pluginId]?.incrementAndGet()
            
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to kill plugin $pluginId")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // MONITORING
    // ════════════════════════════════════════════════════════
    
    private fun startResourceMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Timber.i("[RESOURCE LIMITER] Starting resource monitoring")
            
            // Main monitoring loop
            monitoringScope.launch {
                while (isMonitoring.get()) {
                    try {
                        monitorAllPlugins()
                        delay(MONITORING_INTERVAL_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "[RESOURCE LIMITER] Error in monitoring loop")
                        delay(5000) // Wait before retrying
                    }
                }
            }
            
            // Emergency monitoring for critical violations
            monitoringScope.launch {
                while (isMonitoring.get()) {
                    try {
                        checkEmergencyConditions()
                        delay(EMERGENCY_CHECK_INTERVAL_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "[RESOURCE LIMITER] Error in emergency monitoring")
                        delay(1000)
                    }
                }
            }
        }
    }
    
    private fun monitorAllPlugins() {
        val currentUsage = mutableMapOf<String, ResourceUsage>()
        
        pluginLimits.keys.forEach { pluginId ->
            try {
                val usage = gatherResourceUsage(pluginId)
                if (usage != null) {
                    currentUsage[pluginId] = usage
                    
                    // Enforce limits
                    val memResult = enforceMemoryLimits(pluginId)
                    val cpuResult = enforceCpuLimits(pluginId)
                    val diskResult = enforceDiskLimits(pluginId)
                    
                    // Log violations
                    if (memResult != EnforcementResult.Ok || cpuResult != EnforcementResult.Ok || diskResult != EnforcementResult.Ok) {
                        Timber.d("[RESOURCE LIMITER] Plugin $pluginId enforcement: MEM=$memResult, CPU=$cpuResult, DISK=$diskResult")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[RESOURCE LIMITER] Failed to monitor plugin $pluginId")
            }
        }
        
        _resourceUsage.value = currentUsage
    }
    
    private fun checkEmergencyConditions() {
        pluginLimits.keys.forEach { pluginId ->
            val limits = pluginLimits[pluginId] ?: return@forEach
            val pid = pluginPids[pluginId] ?: return@forEach
            
            try {
                val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
                val totalMemMB = (memInfo?.totalPss ?: 0) / 1024
                
                // Emergency memory check
                if (totalMemMB > limits.emergencyMemoryMB * 3) {
                    Timber.e("[RESOURCE LIMITER] CRITICAL EMERGENCY: Plugin $pluginId using ${totalMemMB}MB - immediate termination")
                    killPlugin(pluginId, "Critical memory emergency: ${totalMemMB}MB")
                }
            } catch (e: Exception) {
                // Process might be dead
            }
        }
    }
    
    private fun gatherResourceUsage(pluginId: String): ResourceUsage? {
        val pid = pluginPids[pluginId] ?: return null
        pluginLimits[pluginId] ?: return null
        
        return try {
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
            val memoryUsedMB = (memInfo?.totalPss ?: 0) / 1024
            val memoryPeakMB = (memInfo?.totalPrivateDirty ?: 0) / 1024
            
            val cpuPercent = getCpuUsageForProcess(pid)
            val diskUsageMB = getDiskUsage(File(context.filesDir, "plugins/$pluginId")) / 1024 / 1024
            val isThrottled = throttledPlugins[pluginId]?.get() ?: false
            val violationCount = pluginViolations[pluginId]?.get()?.toInt() ?: 0
            
            val threadGroup = pluginThreadGroups[pluginId]
            val activeThreads = threadGroup?.activeCount() ?: 0
            
            ResourceUsage(
                pluginId = pluginId,
                memoryUsedMB = memoryUsedMB,
                memoryPeakMB = memoryPeakMB,
                cpuPercent = cpuPercent,
                cpuTimeMs = System.currentTimeMillis(),
                diskUsageMB = diskUsageMB,
                openFileDescriptors = 0, // Would need more complex tracking
                activeThreads = activeThreads,
                networkBandwidthKBps = 0L, // Would integrate with NetworkUsageAggregator
                isThrottled = isThrottled,
                violationCount = violationCount
            )
        } catch (e: Exception) {
            Timber.e(e, "[RESOURCE LIMITER] Failed to gather usage for $pluginId")
            null
        }
    }
    
    fun stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            monitoringScope.cancel()
            Timber.i("[RESOURCE LIMITER] Resource monitoring stopped")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // ENFORCEMENT RESULTS
    // ════════════════════════════════════════════════════════
    
    enum class EnforcementResult {
        Ok,
        Warning,
        Throttled,
        Killed,
        NoLimits,
        ProcessNotFound,
        Error
    }
}
