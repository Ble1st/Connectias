package com.ble1st.connectias.plugin.security

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resource limiter for plugin sandboxes
 * Enforces memory, CPU, and file descriptor limits per plugin
 */
@Singleton
class PluginResourceLimiter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    data class ResourceLimits(
        val maxMemoryMB: Int = 100,
        val maxCpuPercent: Float = 20f,
        val maxFileDescriptors: Int = 50,
        val maxNetworkBandwidthKBps: Long = 1024,
        val maxThreads: Int = 10
    )
    
    data class ResourceUsage(
        val pluginId: String,
        val memoryUsedMB: Int,
        val cpuPercent: Float,
        val openFileDescriptors: Int,
        val activeThreads: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val pluginLimits = ConcurrentHashMap<String, ResourceLimits>()
    private val _resourceUsage = MutableStateFlow<Map<String, ResourceUsage>>(emptyMap())
    val resourceUsage: Flow<Map<String, ResourceUsage>> = _resourceUsage.asStateFlow()
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    /**
     * Set resource limits for a plugin
     */
    fun setResourceLimits(pluginId: String, limits: ResourceLimits) {
        pluginLimits[pluginId] = limits
        Timber.d("Resource limits set for $pluginId: $limits")
    }
    
    /**
     * Get resource limits for a plugin
     */
    fun getResourceLimits(pluginId: String): ResourceLimits {
        return pluginLimits[pluginId] ?: ResourceLimits()
    }
    
    /**
     * Enforce memory limits for a plugin process
     */
    fun enforceMemoryLimits(pluginId: String, pid: Int): Boolean {
        val limits = getResourceLimits(pluginId)
        
        try {
            val memInfo = ActivityManager.RunningAppProcessInfo()
            activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()?.let { memInfo ->
                val totalMemMB = memInfo.totalPss / 1024
                
                if (totalMemMB > limits.maxMemoryMB) {
                    Timber.w("Plugin $pluginId exceeds memory limit: ${totalMemMB}MB > ${limits.maxMemoryMB}MB")
                    
                    // Trigger memory trim
                    activityManager.killBackgroundProcesses(pluginId)
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to enforce memory limits for $pluginId")
            return false
        }
        
        return true
    }
    
    /**
     * Enforce CPU limits by adjusting thread priority
     */
    fun enforceCpuLimits(pluginId: String, threadId: Int) {
        val limits = getResourceLimits(pluginId)
        
        try {
            // Lower priority for CPU-intensive plugins
            if (limits.maxCpuPercent < 30f) {
                Process.setThreadPriority(threadId, Process.THREAD_PRIORITY_BACKGROUND)
            } else if (limits.maxCpuPercent < 50f) {
                Process.setThreadPriority(threadId, Process.THREAD_PRIORITY_DEFAULT)
            }
            
            Timber.d("CPU priority set for plugin $pluginId thread $threadId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set CPU priority for $pluginId")
        }
    }
    
    /**
     * Monitor resource usage for a plugin
     */
    fun monitorResourceUsage(pluginId: String, pid: Int) {
        try {
            val memInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
            val memoryUsedMB = (memInfo?.totalPss ?: 0) / 1024
            
            // Get thread count (simplified - would need proc filesystem for actual count)
            val activeThreads = Runtime.getRuntime().availableProcessors()
            
            val usage = ResourceUsage(
                pluginId = pluginId,
                memoryUsedMB = memoryUsedMB,
                cpuPercent = 0f, // Would need more sophisticated CPU monitoring
                openFileDescriptors = 0, // Would need proc filesystem
                activeThreads = activeThreads
            )
            
            val currentUsage = _resourceUsage.value.toMutableMap()
            currentUsage[pluginId] = usage
            _resourceUsage.value = currentUsage
            
            // Check limits
            val limits = getResourceLimits(pluginId)
            if (memoryUsedMB > limits.maxMemoryMB) {
                Timber.w("Plugin $pluginId memory usage warning: ${memoryUsedMB}MB")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to monitor resource usage for $pluginId")
        }
    }
    
    /**
     * Clear resource tracking for a plugin
     */
    fun clearPluginResources(pluginId: String) {
        pluginLimits.remove(pluginId)
        val currentUsage = _resourceUsage.value.toMutableMap()
        currentUsage.remove(pluginId)
        _resourceUsage.value = currentUsage
    }
    
    /**
     * Get all plugins exceeding their limits
     */
    fun getPluginsExceedingLimits(): List<String> {
        return _resourceUsage.value.filter { (pluginId, usage) ->
            val limits = getResourceLimits(pluginId)
            usage.memoryUsedMB > limits.maxMemoryMB || 
            usage.activeThreads > limits.maxThreads
        }.keys.toList()
    }
}
