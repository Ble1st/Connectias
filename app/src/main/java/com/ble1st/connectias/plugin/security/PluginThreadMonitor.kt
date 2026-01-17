package com.ble1st.connectias.plugin.security

import android.os.Handler
import android.os.Looper
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plugin Thread Monitor for Main Process UI Security
 * 
 * Tracks and manages background threads created by plugin UI fragments
 * to prevent resource leaks and malicious background activity.
 * 
 * SECURITY: Plugin UI runs in Main Process - this monitors for abuse
 */
object PluginThreadMonitor {
    
    // Thread tracking per plugin
    private val pluginThreads = ConcurrentHashMap<String, MutableSet<Thread>>()
    private val pluginThreadCount = ConcurrentHashMap<String, AtomicInteger>()
    private val isMonitoring = AtomicBoolean(false)
    
    // Configuration
    private const val MAX_THREADS_PER_PLUGIN = 5
    private const val MONITOR_INTERVAL_MS = 2000L
    private const val THREAD_CLEANUP_TIMEOUT_MS = 5000L
    
    // Monitoring handler
    private val monitorHandler = Handler(Looper.getMainLooper())
    
    /**
     * Starts monitoring plugin threads
     */
    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            Timber.i("[THREAD MONITOR] Starting plugin thread monitoring")
            scheduleMonitorCheck()
        }
    }
    
    /**
     * Stops monitoring plugin threads
     */
    fun stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            Timber.i("[THREAD MONITOR] Stopping plugin thread monitoring")
            monitorHandler.removeCallbacks(monitorRunnable)
        }
    }
    
    /**
     * Registers a plugin that is about to create UI
     */
    fun registerPlugin(pluginId: String) {
        pluginThreads.putIfAbsent(pluginId, ConcurrentHashMap.newKeySet())
        pluginThreadCount.putIfAbsent(pluginId, AtomicInteger(0))
        Timber.d("[THREAD MONITOR] Plugin registered: $pluginId")
    }
    
    /**
     * Unregisters a plugin and forcefully terminates its threads
     */
    fun unregisterPlugin(pluginId: String) {
        val threads = pluginThreads.remove(pluginId) ?: return
        pluginThreadCount.remove(pluginId)
        
        Timber.i("[THREAD MONITOR] Cleaning up ${threads.size} threads for plugin: $pluginId")
        
        // Attempt graceful shutdown first
        threads.forEach { thread ->
            if (thread.isAlive) {
                try {
                    thread.interrupt()
                    Timber.d("[THREAD MONITOR] Interrupted thread: ${thread.name}")
                } catch (e: Exception) {
                    Timber.w(e, "[THREAD MONITOR] Failed to interrupt thread: ${thread.name}")
                }
            }
        }
        
        // Wait for graceful shutdown
        Thread.sleep(1000)
        
        // Force stop remaining threads
        threads.forEach { thread ->
            if (thread.isAlive) {
                try {
                    @Suppress("DEPRECATION")
                    thread.stop()
                    Timber.w("[THREAD MONITOR] Force-stopped thread: ${thread.name}")
                } catch (e: Exception) {
                    Timber.e(e, "[THREAD MONITOR] Failed to force-stop thread: ${thread.name}")
                }
            }
        }
        
        Timber.i("[THREAD MONITOR] Plugin thread cleanup completed: $pluginId")
    }
    
    /**
     * Tracks a thread created by a plugin
     * Should be called from Thread.setUncaughtExceptionHandler or similar
     */
    fun trackThread(pluginId: String, thread: Thread) {
        val threads = pluginThreads[pluginId]
        if (threads != null) {
            threads.add(thread)
            val count = pluginThreadCount[pluginId]?.incrementAndGet() ?: 0
            
            if (count > MAX_THREADS_PER_PLUGIN) {
                Timber.w("[THREAD MONITOR] Plugin $pluginId exceeded thread limit: $count > $MAX_THREADS_PER_PLUGIN")
                // Don't kill immediately - just warn for now
            }
            
            Timber.d("[THREAD MONITOR] Thread tracked for $pluginId: ${thread.name} (total: $count)")
        }
    }
    
    /**
     * Removes a finished thread from tracking
     */
    fun untrackThread(pluginId: String, thread: Thread) {
        val threads = pluginThreads[pluginId]
        if (threads != null && threads.remove(thread)) {
            pluginThreadCount[pluginId]?.decrementAndGet()
            Timber.d("[THREAD MONITOR] Thread untracked for $pluginId: ${thread.name}")
        }
    }
    
    /**
     * Gets current thread count for a plugin
     */
    fun getThreadCount(pluginId: String): Int {
        return pluginThreadCount[pluginId]?.get() ?: 0
    }
    
    /**
     * Gets debug information about all tracked threads
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Plugin Thread Monitor ===")
            appendLine("Monitoring: ${isMonitoring.get()}")
            appendLine("Tracked plugins: ${pluginThreads.size}")
            
            pluginThreads.forEach { (pluginId, threads) ->
                val count = pluginThreadCount[pluginId]?.get() ?: 0
                appendLine("  $pluginId: $count threads")
                threads.forEach { thread ->
                    val status = if (thread.isAlive) "ALIVE" else "DEAD"
                    appendLine("    - ${thread.name} [$status]")
                }
            }
        }
    }
    
    // Monitoring runnable
    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                monitorThreads()
            } catch (e: Exception) {
                Timber.e(e, "[THREAD MONITOR] Error during thread monitoring")
            } finally {
                if (isMonitoring.get()) {
                    scheduleMonitorCheck()
                }
            }
        }
    }
    
    private fun scheduleMonitorCheck() {
        monitorHandler.postDelayed(monitorRunnable, MONITOR_INTERVAL_MS)
    }
    
    /**
     * Monitors all tracked threads for issues
     */
    private fun monitorThreads() {
        val totalThreads = AtomicInteger(0)
        val deadThreads = mutableListOf<Pair<String, Thread>>()
        
        pluginThreads.forEach { (pluginId, threads) ->
            val iterator = threads.iterator()
            var pluginThreadCount = 0
            
            while (iterator.hasNext()) {
                val thread = iterator.next()
                
                if (!thread.isAlive) {
                    // Thread is dead - remove from tracking
                    deadThreads.add(pluginId to thread)
                    iterator.remove()
                } else {
                    pluginThreadCount++
                    totalThreads.incrementAndGet()
                }
            }
            
            // Update count
            this.pluginThreadCount[pluginId]?.set(pluginThreadCount)
            
            // Warn if too many threads
            if (pluginThreadCount > MAX_THREADS_PER_PLUGIN) {
                Timber.w("[THREAD MONITOR] Plugin $pluginId has excessive threads: $pluginThreadCount")
            }
        }
        
        // Log dead thread cleanup
        if (deadThreads.isNotEmpty()) {
            deadThreads.forEach { (pluginId, thread) ->
                Timber.d("[THREAD MONITOR] Cleaned up dead thread for $pluginId: ${thread.name}")
            }
        }
        
        // Log summary if there are active threads
        if (totalThreads.get() > 0) {
            Timber.v("[THREAD MONITOR] Active plugin threads: ${totalThreads.get()}")
        }
    }
}
