package com.ble1st.connectias.pluginmanager

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.io.File

class PluginOperationQueue(
    private val coroutineScope: CoroutineScope
) {
    private val operationQueue = Channel<PluginOperation>(Channel.UNLIMITED)
    private var isProcessing = false
    
    companion object {
        const val MAX_CPU_PERCENTAGE = 75
        const val CPU_CHECK_INTERVAL_MS = 2000L
    }
    
    init {
        startQueueProcessor()
    }
    
    suspend fun queueOperation(operation: PluginOperation) {
        operationQueue.send(operation)
    }
    
    private fun startQueueProcessor() {
        coroutineScope.launch {
            while (true) {
                // Warten bis CPU-Last niedrig genug ist
                while (getCpuUsage() > MAX_CPU_PERCENTAGE) {
                    Timber.w("CPU usage too high (${getCpuUsage()}%), waiting...")
                    delay(CPU_CHECK_INTERVAL_MS)
                }
                
                // Nächste Operation aus Queue holen
                val operation = operationQueue.receive()
                
                // Operation parallel ausführen (aber Queue-basiert)
                launch {
                    try {
                        operation.execute()
                    } catch (e: Exception) {
                        Timber.e(e, "Plugin operation failed: ${operation.type}")
                        operation.onError(e)
                    }
                }
            }
        }
    }
    
    private fun getCpuUsage(): Int {
        // CPU-Usage lesen aus /proc/stat
        try {
            val reader = File("/proc/stat").bufferedReader()
            val cpuLine = reader.readLine()
            reader.close()
            
            val values = cpuLine.split("\\s+".toRegex())
            val idle = values[4].toLongOrNull() ?: 0
            val total = values.drop(1).take(7).mapNotNull { it.toLongOrNull() }.sum()
            
            val usage = ((total - idle) * 100.0 / total).toInt()
            return usage
        } catch (e: Exception) {
            Timber.e(e, "Failed to read CPU usage")
            return 0 // Fallback
        }
    }
}

sealed class PluginOperation(
    val type: PluginOperationType,
    val onError: (Exception) -> Unit
) {
    abstract suspend fun execute()
}

class InstallPluginOperation(
    val pluginFile: File,
    val pluginLoader: PluginLoader,
    onError: (Exception) -> Unit
) : PluginOperation(PluginOperationType.INSTALL, onError) {
    override suspend fun execute() {
        pluginLoader.loadPlugin(pluginFile)
    }
}

class UninstallPluginOperation(
    val pluginId: String,
    val pluginLoader: PluginLoader,
    onError: (Exception) -> Unit
) : PluginOperation(PluginOperationType.UNINSTALL, onError) {
    override suspend fun execute() {
        pluginLoader.unloadPlugin(pluginId)
    }
}

enum class PluginOperationType {
    INSTALL,
    UNINSTALL,
    UPDATE,
    RELOAD
}
