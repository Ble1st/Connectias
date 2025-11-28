package com.ble1st.connectias.feature.wasm.plugin

import com.ble1st.connectias.feature.wasm.plugin.models.ResourceLimits
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors resource usage for WASM plugins.
 * 
 * Note: java.lang.management is not available on Android, so CPU monitoring
 * is disabled. Only memory and execution time monitoring are available.
 */
@Singleton
class ResourceMonitor @Inject constructor() {
    
    private val tag = "ResourceMonitor"
    
    /**
     * Monitor memory usage before and after execution.
     */
    suspend fun <T> monitorMemory(
        pluginId: String,
        resourceLimits: ResourceLimits,
        execution: suspend () -> T
    ): T {
        val runtime = Runtime.getRuntime()
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        
        return try {
            val result = execution()
            val afterMemory = runtime.totalMemory() - runtime.freeMemory()
            val memoryDelta = afterMemory - beforeMemory
            
            Timber.d(tag, "Plugin $pluginId memory usage: $memoryDelta bytes")
            
            if (memoryDelta > resourceLimits.maxMemory) {
                throw ResourceLimitExceededException.MemoryLimit(
                    used = memoryDelta,
                    limit = resourceLimits.maxMemory
                )
            }
            
            result
        } catch (e: ResourceLimitExceededException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Memory monitoring failed for plugin $pluginId")
            throw e
        }
    }
    
    /**
     * Monitor CPU usage (if available).
     * 
     * Note: CPU monitoring is not available on Android as java.lang.management
     * is not part of the Android SDK. This method just executes the code.
     */
    suspend fun <T> monitorCpu(
        pluginId: String,
        resourceLimits: ResourceLimits,
        execution: suspend () -> T
    ): T {
        // CPU monitoring not available on Android, just execute
        Timber.d(tag, "CPU monitoring not available on Android for plugin $pluginId")
        return execution()
    }
    
    /**
     * Monitor execution time with timeout.
     */
    suspend fun <T> monitorExecutionTime(
        pluginId: String,
        resourceLimits: ResourceLimits,
        execution: suspend () -> T
    ): T {
        return try {
            withTimeout(resourceLimits.maxExecutionTime) {
                execution()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w("Plugin $pluginId execution timeout")
            throw ResourceLimitExceededException.TimeLimit(
                usedMillis = resourceLimits.maxExecutionTime.inWholeMilliseconds,
                limitMillis = resourceLimits.maxExecutionTime.inWholeMilliseconds
            )
        } catch (e: ResourceLimitExceededException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Execution time monitoring failed for plugin $pluginId")
            throw e
        }
    }
    
    /**
     * Enforce all resource limits.
     */
    suspend fun <T> enforceLimits(
        pluginId: String,
        resourceLimits: ResourceLimits,
        execution: suspend () -> T
    ): T {
        return monitorExecutionTime(pluginId, resourceLimits) {
            monitorMemory(pluginId, resourceLimits) {
                monitorCpu(pluginId, resourceLimits) {
                    execution()
                }
            }
        }
    }
}

