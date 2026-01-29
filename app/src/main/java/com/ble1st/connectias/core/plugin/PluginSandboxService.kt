// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.ble1st.connectias.plugin.IPluginSandbox
import com.ble1st.connectias.plugin.PluginMetadataParcel
import com.ble1st.connectias.plugin.PluginResultParcel
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.PluginPermissionBroadcast
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.IPermissionCallback
import com.ble1st.connectias.plugin.security.PluginIdentitySession
import com.ble1st.connectias.plugin.security.SecureHardwareBridgeWrapper
import com.ble1st.connectias.plugin.security.SecureFileSystemBridgeWrapper
import com.ble1st.connectias.core.plugin.security.FilteredParentClassLoader
import com.ble1st.connectias.core.plugin.security.RestrictedClassLoader
import com.ble1st.connectias.core.plugin.logging.PluginDebugLoggingTree
import com.ble1st.connectias.core.plugin.logging.PluginExecutionContext
import com.ble1st.connectias.core.plugin.logging.PluginLogBridgeHolder
import com.ble1st.connectias.plugin.messaging.PluginMessagingProxy
import android.os.ParcelFileDescriptor
import android.os.Binder
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import dalvik.system.InMemoryDexClassLoader
import timber.log.Timber
import com.ble1st.connectias.plugin.logging.IPluginLogBridge
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import org.json.JSONObject
import android.os.Debug
import android.os.Process
import java.util.zip.ZipFile
import kotlinx.coroutines.*

/**
 * Isolated service that runs plugins in a separate process
 * This provides crash isolation and memory isolation from the main app
 */
class PluginSandboxService : Service() {
    
    private val loadedPlugins: ConcurrentHashMap<String, SandboxPluginInfo> = ConcurrentHashMap()
    private val classLoaders: ConcurrentHashMap<String, ClassLoader> = ConcurrentHashMap()
    
    // Permission manager for sandbox process
    private lateinit var permissionManager: PluginPermissionManager
    
    // Hardware bridge for hardware access
    private var hardwareBridge: IHardwareBridge? = null
    private var fileSystemBridge: IFileSystemBridge? = null
    private var permissionCallback: IPermissionCallback? = null
    private var loggingBridge: IPluginLogBridge? = null

    // Messaging bridge for inter-plugin communication (via AIDL from main process)
    private var messagingBridge: com.ble1st.connectias.plugin.messaging.IPluginMessaging? = null

    // Three-Process Architecture UI components (Phase 3)
    // UI Controller: Sends UI state updates from Sandbox to UI Process
    private val uiController = PluginUIControllerImpl()

    // UI Bridge: Receives user events from UI Process
    private lateinit var uiBridge: PluginUIBridgeImpl

    // Broadcast receiver for permission changes
    private var permissionBroadcastReceiver: BroadcastReceiver? = null
    
    // Coroutine scope for async messaging operations
    private val messagingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Memory monitoring
    private val memoryMonitor = PluginMemoryMonitor()
    private val monitorHandler = Handler(Looper.getMainLooper())
    private val memoryCheckInterval = 5000L // Check every 5 seconds
    
    data class SandboxPluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val instance: IPlugin,
        val classLoader: ClassLoader,
        val context: SandboxPluginContext,
        val state: PluginState,
        var lastMemoryUsage: Long = 0L,
        var lastMemoryCheck: Long = 0L,
        var memoryTrend: Long = 0L // bytes/second growth rate
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED
    }
    
    /**
     * Memory monitor for tracking plugin RAM usage
     */
    inner class PluginMemoryMonitor {
        
        // Memory limits (in bytes) - adjusted for mobile devices
        private val pluginWarningLimit = 300 * 1024 * 1024L  // 300 MB warning
        private val pluginCriticalLimit = 400 * 1024 * 1024L // 400 MB critical (auto-unload)
        private val sandboxWarningLimit = 0.7  // 70% of max heap
        private val sandboxCriticalLimit = 0.85 // 85% of max heap
        
        private val runtime = Runtime.getRuntime()
        
        fun startMonitoring() {
            Timber.i("[SANDBOX] Starting memory monitoring (interval: ${memoryCheckInterval}ms)")
            monitorHandler.post(memoryCheckRunnable)
        }
        
        fun stopMonitoring() {
            Timber.i("[SANDBOX] Stopping memory monitoring")
            monitorHandler.removeCallbacks(memoryCheckRunnable)
        }
        
        private val memoryCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    checkMemoryUsage()
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Error during memory check")
                } finally {
                    // Schedule next check
                    monitorHandler.postDelayed(this, memoryCheckInterval)
                }
            }
        }
        
        private fun checkMemoryUsage() {
            // Get precise memory info using Debug API
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            
            // Get overall heap status
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val usagePercentage = usedMemory.toDouble() / maxMemory.toDouble()
            
            // Get PSS (Proportional Set Size) - more accurate memory usage
            val totalPss = memoryInfo.totalPss * 1024L // Convert KB to bytes
            val totalPrivateDirty = memoryInfo.totalPrivateDirty * 1024L
            val totalSharedDirty = memoryInfo.totalSharedDirty * 1024L
            
            // Log detailed memory info (only if significant)
            if (usagePercentage > 0.5) { // Only log if > 50% used
                Timber.d("[SANDBOX] Memory Details:")
                Timber.d("  Heap: ${formatBytes(usedMemory)} / ${formatBytes(maxMemory)} (${(usagePercentage * 100).toInt()}%)")
                Timber.d("  PSS: ${formatBytes(totalPss)} (actual RAM usage)")
                Timber.d("  Private Dirty: ${formatBytes(totalPrivateDirty)}")
                Timber.d("  Shared Dirty: ${formatBytes(totalSharedDirty)}")
            }
            
            // Check sandbox-wide limits using PSS (more accurate)
            val pssPercentage = totalPss.toDouble() / maxMemory.toDouble()
            when {
                pssPercentage >= sandboxCriticalLimit -> {
                    Timber.e("[SANDBOX] CRITICAL: Sandbox PSS memory at ${(pssPercentage * 100).toInt()}% - unloading largest plugin")
                    unloadLargestPlugin()
                }
                pssPercentage >= sandboxWarningLimit -> {
                    Timber.w("[SANDBOX] WARNING: Sandbox PSS memory at ${(pssPercentage * 100).toInt()}%")
                }
            }
            
            // Check per-plugin memory (estimated)
            loadedPlugins.values.forEach { pluginInfo ->
                estimatePluginMemory(pluginInfo, totalPss)
            }
        }
        
        private fun estimatePluginMemory(pluginInfo: SandboxPluginInfo, totalPss: Long) {
            val currentTime = System.currentTimeMillis()
            val pluginCount = loadedPlugins.size
            
            // Base memory estimation with better distribution
            val baseMemory = if (pluginCount > 0) totalPss / pluginCount else totalPss
            
            // Adjust based on plugin characteristics
            val adjustedMemory = when {
                pluginInfo.metadata.dependencies.isNotEmpty() -> {
                    // Plugins with dependencies likely use more memory
                    baseMemory + (baseMemory * 0.3).toLong()
                }
                pluginInfo.metadata.fragmentClassName?.contains("Compose") == true -> {
                    // Compose UIs typically use more memory
                    baseMemory + (baseMemory * 0.2).toLong()
                }
                else -> baseMemory
            }
            
            // Calculate memory growth trend if we have previous data
            if (pluginInfo.lastMemoryCheck in 1..<currentTime) {
                val timeDelta = (currentTime - pluginInfo.lastMemoryCheck) / 1000.0 // seconds
                val memoryDelta = adjustedMemory - pluginInfo.lastMemoryUsage
                
                if (timeDelta > 0) {
                    pluginInfo.memoryTrend = (memoryDelta / timeDelta).toLong()
                    
                    // Warn about rapid memory growth
                    if (pluginInfo.memoryTrend > 1024 * 1024) { // >1MB/sec growth
                        Timber.w("[SANDBOX] Plugin '${pluginInfo.pluginId}' rapid memory growth: ${formatBytes(pluginInfo.memoryTrend)}/sec")
                    }
                }
            }
            
            // Update tracking data
            pluginInfo.lastMemoryUsage = adjustedMemory
            pluginInfo.lastMemoryCheck = currentTime
            
            // Check limits
            when {
                adjustedMemory >= pluginCriticalLimit -> {
                    Timber.e("[SANDBOX] Plugin '${pluginInfo.pluginId}' CRITICAL memory: ${formatBytes(adjustedMemory)} - auto-unloading")
                    performUnload(pluginInfo.pluginId)
                }
                adjustedMemory >= pluginWarningLimit -> {
                    Timber.w("[SANDBOX] Plugin '${pluginInfo.pluginId}' WARNING memory: ${formatBytes(adjustedMemory)} (trend: ${formatBytes(pluginInfo.memoryTrend)}/sec)")
                }
            }
        }
        
        private fun unloadLargestPlugin() {
            // Find plugin with highest estimated memory usage
            val largestPlugin = loadedPlugins.values.maxByOrNull { it.lastMemoryUsage }
            
            if (largestPlugin != null) {
                Timber.w("[SANDBOX] Unloading largest plugin: ${largestPlugin.pluginId} (${formatBytes(largestPlugin.lastMemoryUsage)})")
                monitorHandler.post {
                    try {
                        performUnload(largestPlugin.pluginId)
                        // Force garbage collection
                        System.gc()
                    } catch (e: Exception) {
                        Timber.e(e, "[SANDBOX] Failed to unload largest plugin")
                    }
                }
            }
        }
        
        private fun calculateDirectorySize(directory: File): Long {
            if (!directory.exists() || !directory.isDirectory) return 0L
            
            var size = 0L
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
            return size
        }
        
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                bytes >= 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }
        
        fun logMemoryStats() {
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            
            Timber.i("[SANDBOX] Memory Stats:")
            Timber.i("  Max Heap: ${formatBytes(maxMemory)}")
            Timber.i("  Used: ${formatBytes(usedMemory)}")
            Timber.i("  Free: ${formatBytes(freeMemory)}")
            Timber.i("  Usage: ${(usedMemory.toDouble() / maxMemory.toDouble() * 100).toInt()}%")
            
            loadedPlugins.values.forEach { plugin ->
                Timber.i("  Plugin '${plugin.pluginId}': ~${formatBytes(plugin.lastMemoryUsage)}")
            }
        }
    }
    
    /**
     * Internal method to unload a plugin (can be called from memory monitor)
     */
    private fun performUnload(pluginId: String): PluginResultParcel {
        return try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
            
            // Disable first
            if (pluginInfo.state == PluginState.ENABLED) {
                performDisable(pluginId)
            }
            
            // Call onUnload
            try {
                PluginExecutionContext.withPlugin(pluginId) {
                    pluginInfo.instance.onUnload()
                }
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Plugin onUnload failed")
            }
            
            // Cleanup messaging (if bridge is available)
            val bridge = messagingBridge
            if (bridge != null) {
                messagingScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            bridge.unregisterPlugin(pluginId)
                        }
                        Timber.i("[SANDBOX] Plugin unregistered from messaging: $pluginId")
                    } catch (e: Exception) {
                        Timber.w(e, "[SANDBOX] Failed to unregister plugin from messaging: $pluginId")
                    }
                }
            }
            
            // Cleanup plugin context
            pluginInfo.context.cleanup()
            
            // Cleanup
            classLoaders.remove(pluginId)
            loadedPlugins.remove(pluginId)
            // SECURITY: Clean up identity session
            PluginIdentitySession.unregisterPluginSession(pluginId)
            Timber.i("[SANDBOX] Plugin identity session cleaned up: $pluginId")
            
            // Delete read-only plugin file from sandbox_plugins_ro directory
            // Note: In isolated process, we cannot access filesystem
            /*
                val secureDir = File(filesDir, "sandbox_plugins_ro")
                val pluginFile = File(secureDir, "$pluginId.apk")
                if (pluginFile.exists()) {
                    } else {
                        Timber.w("[SANDBOX] Failed to delete plugin directory: ${pluginDir.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "[SANDBOX] Failed to delete plugin directory for: $pluginId")
            }
            */
            
            Timber.i("[SANDBOX] Plugin unloaded: $pluginId")
            PluginResultParcel.success()
            
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to unload plugin")
            PluginResultParcel.failure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Internal method to disable a plugin
     */
    private fun performDisable(pluginId: String): PluginResultParcel {
        return try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
            
            val disableSuccess = try {
                PluginExecutionContext.withPlugin(pluginId) {
                    pluginInfo.instance.onDisable()
                }
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Plugin onDisable failed")
                false
            }
            
            if (!disableSuccess) {
                return PluginResultParcel.failure("Plugin onDisable() returned false")
            }
            
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
            Timber.i("[SANDBOX] Plugin disabled: $pluginId")
            PluginResultParcel.success()
            
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to disable plugin")
            PluginResultParcel.failure(e.message ?: "Unknown error")
        }
    }
    
    private val binder: IBinder = object : IPluginSandbox.Stub() {
        
        override fun loadPlugin(pluginPath: String): PluginResultParcel {
            return try {
                Timber.d("[SANDBOX] Loading plugin from: $pluginPath")
                
                val pluginFile = File(pluginPath)
                if (!pluginFile.exists()) {
                    return PluginResultParcel.failure("Plugin file not found: $pluginPath")
                }
                
                // Extract metadata
                val metadata = extractPluginMetadata(pluginFile)
                
                // Create DEX output directory
                val dexOutputDir = File(cacheDir, "sandbox_plugins/${metadata.pluginId}")
                dexOutputDir.mkdirs()
                
                // Create DexClassLoader
                val classLoader = DexClassLoader(
                    pluginFile.absolutePath,
                    dexOutputDir.absolutePath,
                    null,
                    this@PluginSandboxService.classLoader
                )
                
                // Load plugin class
                // For new plugins (Three-Process UI), fragmentClassName may be null or empty
                // Try to find plugin class automatically if not specified
                val pluginClassName = metadata.fragmentClassName?.takeIf { it.isNotBlank() }
                    ?: findPluginClass(classLoader, metadata.pluginId)
                
                // Load plugin class directly from DEX (bypass parent filtering for plugin's own classes)
                val pluginClass = if (classLoader is com.ble1st.connectias.core.plugin.security.RestrictedClassLoader) {
                    classLoader.loadClassFromDex(pluginClassName)
                } else {
                    classLoader.loadClass(pluginClassName)
                }
                // Create plugin instance - support both IPlugin versions
                val pluginInstance = createPluginInstance(pluginClass)
                
                // Create minimal PluginContext for sandbox with permission manager and hardware bridge
                val pluginDir = File(filesDir, "sandbox_plugins/${metadata.pluginId}")
                val pluginContext = SandboxPluginContext(
                    appContext = applicationContext,
                    pluginDir = pluginDir,
                    pluginId = metadata.pluginId,
                    sessionToken = 0L,
                    permissionManager = permissionManager,
                    hardwareBridge = hardwareBridge,
                    fileSystemBridge = fileSystemBridge,
                    messagingBridge = messagingBridge,
                    uiController = uiController
                )
                
                // Call onLoad
                val loadSuccess = try {
                    PluginExecutionContext.withPlugin(metadata.pluginId) {
                        pluginInstance.onLoad(pluginContext)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onLoad failed")
                    false
                }
                
                if (!loadSuccess) {
                    dexOutputDir.deleteRecursively()
                    return PluginResultParcel.failure("Plugin onLoad() returned false")
                }
                
                // Store plugin info
                val pluginInfo = SandboxPluginInfo(
                    pluginId = metadata.pluginId,
                    metadata = metadata,
                    instance = pluginInstance,
                    classLoader = classLoader,
                    context = pluginContext,
                    state = PluginState.LOADED,
                    lastMemoryUsage = 0L
                )
                
                this@PluginSandboxService.loadedPlugins[metadata.pluginId] = pluginInfo
                this@PluginSandboxService.classLoaders[metadata.pluginId] = classLoader
                
                Timber.i("[SANDBOX] Plugin loaded: ${metadata.pluginName}")
                memoryMonitor.logMemoryStats()
                PluginResultParcel.success(PluginMetadataParcel.fromPluginMetadata(metadata))
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to load plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun enablePlugin(pluginId: String): PluginResultParcel {
            return try {
                val pluginInfo = this@PluginSandboxService.loadedPlugins[pluginId]
                    ?: return PluginResultParcel.failure("Plugin not found: $pluginId")
                
                val enableSuccess = try {
                    PluginExecutionContext.withPlugin(pluginId) {
                        pluginInfo.instance.onEnable()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onEnable failed")
                    false
                }
                
                if (!enableSuccess) {
                    return PluginResultParcel.failure("Plugin onEnable() returned false")
                }
                
                this@PluginSandboxService.loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.ENABLED)
                Timber.i("[SANDBOX] Plugin enabled: $pluginId")
                PluginResultParcel.success()
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to enable plugin")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun disablePlugin(pluginId: String): PluginResultParcel {
            return this@PluginSandboxService.performDisable(pluginId)
        }
        
        override fun unloadPlugin(pluginId: String): PluginResultParcel {
            return this@PluginSandboxService.performUnload(pluginId)
        }
        
        override fun getLoadedPlugins(): List<String> {
            return this@PluginSandboxService.loadedPlugins.keys.toList()
        }
        
        override fun getPluginMetadata(pluginId: String): PluginMetadataParcel? {
            val pluginInfo = this@PluginSandboxService.loadedPlugins[pluginId] ?: return null
            return PluginMetadataParcel.fromPluginMetadata(pluginInfo.metadata)
        }
        
        override fun ping(): Boolean {
            return true
        }
        
        override fun getSandboxPid(): Int {
            return android.os.Process.myPid()
        }
        
        override fun getSandboxMemoryUsage(): Long {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            return totalMemory - freeMemory
        }
        
        override fun getSandboxMaxMemory(): Long {
            return Runtime.getRuntime().maxMemory()
        }
        
        override fun getPluginMemoryUsage(pluginId: String): Long {
            return try {
                val pluginInfo = this@PluginSandboxService.loadedPlugins[pluginId]
                if (pluginInfo == null) {
                    Timber.w("[SANDBOX] Plugin not found for memory query: $pluginId")
                    return -1L
                }
                
                // Get current memory info for context
                val memoryInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memoryInfo)
                val totalPss = memoryInfo.totalPss * 1024L
                
                // Return the estimated memory for this plugin
                // Note: In isolated process, we can't get precise per-plugin memory
                // This is an estimate based on PSS distribution
                pluginInfo.lastMemoryUsage
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to get plugin memory usage")
                -1L
            }
        }
        
        override fun loadPluginFromDescriptor(
            pluginFd: ParcelFileDescriptor,
            pluginId: String,
            sessionToken: Long
        ): PluginResultParcel {
            return try {
                Timber.i("[SANDBOX] Loading plugin from descriptor: $pluginId")
                
                // Step 1: Read entire APK into memory (isolated process has no file system access)
                val apkBytes = java.io.FileInputStream(pluginFd.fileDescriptor).use { it.readBytes() }
                pluginFd.close()
                
                Timber.d("[SANDBOX] Read ${apkBytes.size} bytes into memory")
                
                // Step 2: Extract plugin metadata from APK bytes
                val metadata = extractPluginMetadataFromBytes(apkBytes)
                
                // Step 3: Extract ALL DEX files from APK for RestrictedClassLoader
                val dexBytesList = extractAllDexFromApk(apkBytes)
                val dexBuffers = dexBytesList.map { java.nio.ByteBuffer.wrap(it) }.toTypedArray()

                // SECURITY: Use filtered parent classloader to block access to app internals
                // Note: Plugin classes (in plugin's own package) should be loaded from DEX, not parent
                val filteredParent = FilteredParentClassLoader(this@PluginSandboxService.classLoader)
                val classLoader = RestrictedClassLoader(dexBuffers, filteredParent, pluginId)

                Timber.i("[SANDBOX] Created restricted classloader for plugin: $pluginId")
                
                // Step 4: Debug - Show metadata
                Timber.d("[SANDBOX] Plugin metadata: fragmentClassName=${metadata.fragmentClassName}")
                
                // Step 5: Load plugin class
                // For new plugins (Three-Process UI), fragmentClassName may be null or empty
                // Try to find plugin class automatically if not specified
                val pluginClassName = metadata.fragmentClassName?.takeIf { it.isNotBlank() } 
                    ?: findPluginClass(classLoader, pluginId)
                Timber.d("[SANDBOX] Attempting to load class: $pluginClassName")
                
                // Load plugin class directly from DEX (bypass parent filtering for plugin's own classes)
                val pluginClass = if (classLoader is com.ble1st.connectias.core.plugin.security.RestrictedClassLoader) {
                    classLoader.loadClassFromDex(pluginClassName)
                } else {
                    classLoader.loadClass(pluginClassName)
                }
                // Create plugin instance - support both IPlugin versions
                val pluginInstance = createPluginInstance(pluginClass)
                
                // Step 6: Register sandbox-local identity (debug/audit only).
                // NOTE: The per-plugin sessionToken used for IPC must be created in the main process
                // and passed into this method (parameter `sessionToken`) so main-process bridges can validate it.
                val callerUid = Binder.getCallingUid()
                val sandboxIdentityToken = PluginIdentitySession.registerPluginSession(pluginId, callerUid)
                Timber.i(
                    "[SANDBOX] Plugin identity registered (sandbox-local): $pluginId -> UID:$callerUid, Token:$sandboxIdentityToken"
                )

                // Register main-process session token for UI/FileSystem IPC from sandbox.
                uiController.registerPluginSession(pluginId, sessionToken)

                // Step 7: IMPORTANT: Do NOT wrap bridges in sandbox process!
                // The bridges are AIDL proxies that go to the main process via IPC.
                // Security verification happens in the MAIN process, not here.
                // Using security wrappers here causes Binder.getCallingUid() to return
                // the sandbox process UID instead of the original caller UID.

                // Step 8: Create plugin context (isolated process has no filesystem access)
                val context = SandboxPluginContext(
                    applicationContext,
                    null, // No cache dir in isolated process
                    pluginId,
                    sessionToken,
                    permissionManager,
                    hardwareBridge, // Use raw bridge (security happens in main process)
                    fileSystemBridge, // Use raw bridge (security happens in main process)
                    messagingBridge, // Messaging bridge for inter-plugin communication (via AIDL)
                    uiController = uiController
                )
                
                // Step 8a: Register plugin for messaging (if bridge is available)
                val bridge = messagingBridge
                if (bridge != null) {
                    messagingScope.launch {
                        try {
                            val registered = withContext(Dispatchers.IO) {
                                bridge.registerPlugin(pluginId)
                            }
                            if (registered) {
                                Timber.i("[SANDBOX] Plugin registered for messaging: $pluginId")
                            } else {
                                Timber.w("[SANDBOX] Failed to register plugin for messaging: $pluginId")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "[SANDBOX] Error registering plugin for messaging: $pluginId")
                        }
                    }
                } else {
                    Timber.d("[SANDBOX] Messaging bridge not available, skipping registration")
                }
                
                // Step 9: Initialize plugin with onLoad
                val loadSuccess = try {
                    PluginExecutionContext.withPlugin(pluginId) {
                        pluginInstance.onLoad(context)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onLoad failed")
                    false
                }
                
                if (!loadSuccess) {
                    return PluginResultParcel.failure("Plugin onLoad() returned false or threw exception")
                }
                
                // Step 10: Enable plugin
                val enableSuccess = try {
                    PluginExecutionContext.withPlugin(pluginId) {
                        pluginInstance.onEnable()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onEnable failed")
                    false
                }
                
                // Step 11: Store plugin info with correct state based on enable result
                val pluginState = if (enableSuccess) PluginState.ENABLED else PluginState.LOADED
                val pluginInfo = SandboxPluginInfo(
                    pluginId = pluginId,
                    metadata = metadata,
                    instance = pluginInstance,
                    classLoader = classLoader,
                    context = context,
                    state = pluginState
                )
                this@PluginSandboxService.loadedPlugins[pluginId] = pluginInfo
                this@PluginSandboxService.classLoaders[pluginId] = classLoader
                
                Timber.i("[SANDBOX] Plugin loaded successfully from memory: $pluginId")
                
                PluginResultParcel.success(
                    PluginMetadataParcel(
                        pluginId = metadata.pluginId,
                        pluginName = metadata.pluginName,
                        version = metadata.version,
                        author = metadata.author,
                        minApiLevel = metadata.minApiLevel,
                        maxApiLevel = metadata.maxApiLevel,
                        minAppVersion = metadata.minAppVersion,
                        nativeLibraries = metadata.nativeLibraries,
                        fragmentClassName = metadata.fragmentClassName ?: "",
                        description = metadata.description,
                        permissions = metadata.permissions,
                        category = metadata.category.name,
                        dependencies = metadata.dependencies
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to load plugin from descriptor")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }

        override fun loadDeclarativePluginFromDescriptor(
            packageFd: ParcelFileDescriptor,
            pluginId: String,
            sessionToken: Long
        ): PluginResultParcel {
            return try {
                Timber.i("[SANDBOX] Loading declarative plugin from descriptor: $pluginId")
                val bytes = java.io.FileInputStream(packageFd.fileDescriptor).use { it.readBytes() }
                packageFd.close()
                loadDeclarativePluginFromBytes(bytes, pluginId, sessionToken)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to load declarative plugin from descriptor")
                PluginResultParcel.failure(e.message ?: "Unknown error")
            }
        }
        
        override fun setHardwareBridge(hardwareBridge: IBinder) {
            try {
                this@PluginSandboxService.hardwareBridge = IHardwareBridge.Stub.asInterface(hardwareBridge)
                Timber.i("[SANDBOX] Hardware bridge set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set hardware bridge")
            }
        }
        
        override fun setFileSystemBridge(fileSystemBridge: IBinder) {
            try {
                this@PluginSandboxService.fileSystemBridge = IFileSystemBridge.Stub.asInterface(fileSystemBridge)
                this@PluginSandboxService.uiController.setFileSystemBridge(
                    this@PluginSandboxService.fileSystemBridge
                )
                Timber.i("[SANDBOX] File system bridge set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set file system bridge")
            }
        }

        override fun setLoggingBridge(loggingBridge: IBinder) {
            try {
                val bridge = IPluginLogBridge.Stub.asInterface(loggingBridge)
                this@PluginSandboxService.loggingBridge = bridge
                PluginLogBridgeHolder.bridge = bridge

                if (PluginLogBridgeHolder.isTreeInstalled.compareAndSet(false, true)) {
                    Timber.plant(PluginDebugLoggingTree())
                    Timber.i("[SANDBOX] Plugin debug logging tree installed")
                }

                Timber.i("[SANDBOX] Logging bridge set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set logging bridge")
            }
        }
        
        override fun setPermissionCallback(callback: IBinder) {
            try {
                this@PluginSandboxService.permissionCallback = IPermissionCallback.Stub.asInterface(callback)
                Timber.i("[SANDBOX] Permission callback set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set permission callback")
            }
        }

        override fun setMessagingBridge(messagingBridge: IBinder) {
            try {
                this@PluginSandboxService.messagingBridge = com.ble1st.connectias.plugin.messaging.IPluginMessaging.Stub.asInterface(messagingBridge)
                Timber.i("[SANDBOX] Messaging bridge set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set messaging bridge")
            }
        }

        override fun setUIBridge(uiBridge: IBinder) {
            // DEPRECATED: Old VirtualDisplay system
            Timber.w("[SANDBOX] setUIBridge called - this is deprecated, use setUIController instead")
        }

        override fun setUIController(uiController: IBinder) {
            try {
                val controller = com.ble1st.connectias.plugin.ui.IPluginUIController.Stub.asInterface(uiController)
                this@PluginSandboxService.uiController.setRemoteController(controller)
                Timber.i("[SANDBOX] UI Controller connected (Three-Process Architecture)")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set UI controller")
            }
        }

        override fun getUIBridge(): IBinder {
            Timber.d("[SANDBOX] Get UI Bridge")
            return this@PluginSandboxService.uiBridge.asBinder()
        }

        override fun requestPluginUIRender(
            pluginId: String,
            width: Int,
            height: Int,
            density: Float,
            densityDpi: Int
        ): android.view.Surface? {
            return try {
                Timber.d("[SANDBOX] Requesting UI render for plugin: $pluginId (${width}x${height})")

                // Get plugin info
                val pluginInfo = this@PluginSandboxService.loadedPlugins[pluginId]
                if (pluginInfo == null) {
                    Timber.e("[SANDBOX] Plugin not found: $pluginId")
                    return null
                }

                // Create UIRenderRequest
                val request = com.ble1st.connectias.plugin.ui.UIRenderRequest().apply {
                    this.pluginId = pluginId
                    this.width = width
                    this.height = height
                    this.density = density
                    this.densityDpi = densityDpi
                    this.hardwareAccelerated = true
                    this.fragmentArgs = null
                    this.isCompose = false
                }

                // NOTE: Fragment rendering deaktiviert - VirtualDisplay funktioniert nicht im isolierten Prozess
                // UI-Rendering erfolgt jetzt im Main Process mit IsolatedPluginContext
                Timber.w("[SANDBOX] requestPluginUIRender() called but fragment renderer is disabled - UI rendering happens in Main Process")
                null

            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error requesting UI render for plugin: $pluginId")
                null
            }
        }

        override fun destroyPluginUI(pluginId: String) {
            try {
                // NOTE: Fragment rendering deaktiviert - UI wird im Main Process verwaltet
                Timber.d("[SANDBOX] destroyPluginUI() called but fragment renderer is disabled - UI cleanup happens in Main Process")
                Timber.i("[SANDBOX] UI cleanup notification for plugin: $pluginId (handled in Main Process)")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error in destroyPluginUI for plugin: $pluginId")
            }
        }

        override fun dispatchPluginTouchEvent(
            pluginId: String,
            action: Int,
            x: Float,
            y: Float,
            eventTime: Long
        ): Boolean {
            return try {
                // Create MotionEventParcel
                val event = com.ble1st.connectias.plugin.ui.MotionEventParcel().apply {
                    this.action = action
                    this.x = x
                    this.y = y
                    this.eventTime = eventTime
                    this.downTime = eventTime
                    this.pressure = 1.0f
                    this.size = 1.0f
                    this.metaState = 0
                    this.buttonState = 0
                    this.deviceId = 0
                    this.edgeFlags = 0
                    this.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    this.flags = 0
                }

                // NOTE: Fragment rendering deaktiviert - Touch-Events werden im Main Process verarbeitet
                Timber.d("[SANDBOX] dispatchPluginTouchEvent() called but fragment renderer is disabled - touch events handled in Main Process")
                false

            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Error dispatching touch event for plugin: $pluginId")
                false
            }
        }

        override fun requestPermissionAsync(pluginId: String, permission: String): Boolean {
            return try {
                // Validate plugin exists
                if (this@PluginSandboxService.loadedPlugins[pluginId as String] == null) {
                    Timber.e("[SANDBOX] Plugin not found: $pluginId")
                    return false
                }
                
                // Check permission manager
                if (!::permissionManager.isInitialized) {
                    Timber.e("[SANDBOX] Permission manager not initialized")
                    return false
                }
                
                // Check if callback is set
                if (permissionCallback == null) {
                    Timber.e("[SANDBOX] Permission callback not set")
                    return false
                }
                
                // Check critical permissions (always denied)
                val criticalPermissions = permissionManager.getCriticalPermissions(listOf(permission))
                if (criticalPermissions.isNotEmpty()) {
                    Timber.w("[SANDBOX] Critical permission denied: $permission")
                    permissionCallback?.onPermissionResult(pluginId, permission, false, "Critical permission cannot be granted")
                    return true
                }
                
                // Check if already granted
                if (permissionManager.isPermissionAllowed(pluginId, permission)) {
                    Timber.d("[SANDBOX] Permission already granted: $permission")
                    permissionCallback?.onPermissionResult(pluginId, permission, true, null)
                    return true
                }
                
                // Send permission request broadcast
                val intent = PluginPermissionBroadcast.createPermissionRequestIntent(pluginId, permission)
                sendBroadcast(intent)
                
                Timber.i("[SANDBOX] Permission request sent: $permission for plugin $pluginId")
                true
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to request permission: $permission")
                false
            }
        }
        
        override fun requestPermissionsAsync(pluginId: String, permissions: List<String>): Boolean {
            return try {
                // Validate plugin exists
                if (this@PluginSandboxService.loadedPlugins[pluginId as String] == null) {
                    Timber.e("[SANDBOX] Plugin not found: $pluginId")
                    return false
                }
                
                // Check permission manager
                if (!::permissionManager.isInitialized) {
                    Timber.e("[SANDBOX] Permission manager not initialized")
                    return false
                }
                
                // Check if callback is set
                if (permissionCallback == null) {
                    Timber.e("[SANDBOX] Permission callback not set")
                    return false
                }
                
                val results = mutableMapOf<String, Boolean>()
                val criticalPermissions = permissionManager.getCriticalPermissions(permissions)
                
                // Process each permission
                for (permission in permissions) {
                    // Check critical permissions
                    if (criticalPermissions.contains(permission)) {
                        results[permission] = false
                        continue
                    }
                    
                    // Check if already granted
                    results[permission] = permissionManager.isPermissionAllowed(pluginId, permission)
                }
                
                // Send permission request broadcast for non-granted permissions
                val nonGrantedPermissions = permissions.filter { 
                    !results[it]!! && !criticalPermissions.contains(it)
                }
                
                if (nonGrantedPermissions.isNotEmpty()) {
                    val intent = PluginPermissionBroadcast.createMultiplePermissionRequestIntent(
                        pluginId, 
                        nonGrantedPermissions
                    )
                    sendBroadcast(intent)
                }
                
                // Send immediate results for already granted/critical permissions
                if (results.isNotEmpty()) {
                    val permissionResults = results.map { (permission, granted) ->
                        com.ble1st.connectias.plugin.PermissionResult(permission, granted)
                    }
                    permissionCallback?.onPermissionsResult(pluginId, permissionResults, null)
                }
                
                Timber.i("[SANDBOX] Multiple permission request sent for plugin $pluginId")
                true
                
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to request permissions for plugin $pluginId")
                false
            }
        }
        
        override fun shutdown() {
            Timber.i("[SANDBOX] Shutting down sandbox")
            this@PluginSandboxService.loadedPlugins.keys.toList().forEach { pluginId ->
                try {
                    this@PluginSandboxService.performUnload(pluginId)
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Error unloading plugin during shutdown")
                }
            }
            stopSelf()
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        Timber.i("[SANDBOX] Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("[SANDBOX] Service created in process: ${android.os.Process.myPid()}")

        // Initialize permission manager for sandbox process
        permissionManager = PluginPermissionManager(applicationContext)

        // Initialize UI Bridge for Three-Process Architecture (Phase 3)
        // This bridge receives user events from UI Process
        // Pass a function that extracts IPlugin instances from loadedPlugins at runtime
        // (loadedPlugins is empty at initialization, plugins are loaded later)
        uiBridge = PluginUIBridgeImpl(
            pluginProvider = { pluginId ->
                val pluginInfo = loadedPlugins[pluginId]
                if (pluginInfo != null) {
                    pluginInfo.instance
                } else {
                    Timber.w("[SANDBOX] No SandboxPluginInfo found for $pluginId. Available plugins: ${loadedPlugins.keys.joinToString()}")
                    null
                }
            },
            uiController = uiController // Pass UI controller reference for resending cached state
        )
        Timber.i("[SANDBOX] UI Bridge initialized (Three-Process Architecture)")

        // Note: UI Controller will be connected to UI Process via setUIController() from Main Process
        Timber.i("[SANDBOX] Waiting for UI Controller connection from Main Process")

        // Note: Messaging bridge will be set via setMessagingBridge() from main process
        Timber.i("[SANDBOX] Waiting for messaging bridge to be set from main process")
        
        // Note: Broadcast receiver cannot be registered in isolated process
        // Permission changes will be handled via IPC through hardware bridge
        Timber.i("[SANDBOX] Skipping broadcast receiver registration (not allowed in isolated process)")
        
        // Start memory monitoring
        memoryMonitor.startMonitoring()
        memoryMonitor.logMemoryStats()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[SANDBOX] Service destroyed")

        // Cancel messaging coroutine scope
        messagingScope.cancel()

        // Disconnect UI Controller
        uiController.disconnect()

        // Clear UI Bridge listeners
        uiBridge.clearLifecycleListeners()

        // Note: No broadcast receiver to unregister (isolated process restriction)
        // Note: Messaging bridge cleanup is handled by main process

        // Stop memory monitoring
        memoryMonitor.stopMonitoring()
        memoryMonitor.logMemoryStats()
    }

    /**
     * Gets the UI Controller for sending UI state updates to UI Process.
     * Used by plugins to update their UI.
     *
     * @return PluginUIControllerImpl instance
     */
    fun getUIController(): PluginUIControllerImpl = uiController

    /**
     * Gets the UI Bridge for receiving user events from UI Process.
     * Used internally for lifecycle management.
     *
     * @return PluginUIBridgeImpl instance
     */
    fun getUIBridge(): PluginUIBridgeImpl = uiBridge
    
    private fun extractPluginMetadata(pluginFile: File): PluginMetadata {
        ZipFile(pluginFile).use { zip ->
            // Try both locations: root (APK) and assets/ (AAR)
            val manifestEntry = zip.getEntry("plugin-manifest.json")
                ?: zip.getEntry("assets/plugin-manifest.json")
                ?: throw IllegalArgumentException("No plugin-manifest.json found")
            
            val jsonString = zip.getInputStream(manifestEntry).bufferedReader().readText()
            val json = JSONObject(jsonString)
            
            val requirements = json.optJSONObject("requirements") ?: JSONObject()
            
            return PluginMetadata(
                pluginId = json.getString("pluginId"),
                pluginName = json.getString("pluginName"),
                version = json.getString("version"),
                author = json.optString("author", "Unknown"),
                minApiLevel = requirements.optInt("minApiLevel", 33),
                maxApiLevel = requirements.optInt("maxApiLevel", 36),
                minAppVersion = requirements.optString("minAppVersion", "1.0.0"),
                nativeLibraries = json.optJSONArray("nativeLibraries")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList(),
                // fragmentClassName is optional - new plugins use onRenderUI() API
                // Support pluginClassName as fallback for new plugins
                // Treat empty strings as null
                fragmentClassName = json.optString("fragmentClassName", null)?.takeIf { it.isNotBlank() }
                    ?: json.optString("pluginClassName", null)?.takeIf { it.isNotBlank() },
                description = json.optString("description", ""),
                permissions = json.optJSONArray("permissions")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList(),
                category = com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                    json.optString("category", "UTILITY")
                ),
                dependencies = json.optJSONArray("dependencies")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList()
            )
        }
    }
    
    private fun extractPluginMetadataFromBytes(apkBytes: ByteArray): PluginMetadata {
        // Create a temporary ZipInputStream from the byte array
        java.util.zip.ZipInputStream(apkBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            
            // Find plugin-manifest.json
            while (entry != null && !entry.name.endsWith("plugin-manifest.json")) {
                entry = zip.nextEntry
            }
            
            if (entry == null) {
                throw IllegalArgumentException("No plugin-manifest.json found in APK")
            }
            
            val jsonString = zip.bufferedReader().readText()
            val json = JSONObject(jsonString)
            
            val requirements = json.optJSONObject("requirements") ?: JSONObject()
            
            return PluginMetadata(
                pluginId = json.getString("pluginId"),
                pluginName = json.getString("pluginName"),
                version = json.getString("version"),
                author = json.optString("author", "Unknown"),
                minApiLevel = requirements.optInt("minApiLevel", 33),
                maxApiLevel = requirements.optInt("maxApiLevel", 36),
                minAppVersion = requirements.optString("minAppVersion", "1.0.0"),
                nativeLibraries = json.optJSONArray("nativeLibraries")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList(),
                // fragmentClassName is optional - new plugins use onRenderUI() API
                // Support pluginClassName as fallback for new plugins
                // Treat empty strings as null
                fragmentClassName = json.optString("fragmentClassName", null)?.takeIf { it.isNotBlank() }
                    ?: json.optString("pluginClassName", null)?.takeIf { it.isNotBlank() },
                description = json.optString("description", ""),
                permissions = json.optJSONArray("permissions")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList(),
                category = com.ble1st.connectias.plugin.sdk.PluginCategory.valueOf(
                    json.optString("category", "UTILITY")
                ),
                dependencies = json.optJSONArray("dependencies")?.let {
                    (0 until it.length()).map { i -> it.getString(i) }
                } ?: emptyList()
            )
        }
    }

    /**
     * Loads a declarative plugin package (".cplug") from in-memory bytes.
     *
     * Full implementation is provided by the sandbox declarative runtime.
     * This method is intentionally isolated so we can keep binder code minimal.
     */
    private fun loadDeclarativePluginFromBytes(
        cplugBytes: ByteArray,
        pluginId: String,
        sessionToken: Long
    ): PluginResultParcel {
        return try {
            val pkg = com.ble1st.connectias.core.plugin.declarative.DeclarativePackageReader.read(cplugBytes)
            val manifest = pkg.manifest

            if (manifest.pluginId != pluginId) {
                Timber.w("[SANDBOX] Declarative pluginId mismatch: arg=$pluginId, manifest=${manifest.pluginId}")
            }

            // Register plugin identity session for security (best-effort)
            try {
                val callerUid = Binder.getCallingUid()
                val sandboxIdentityToken = PluginIdentitySession.registerPluginSession(pluginId, callerUid)
                Timber.i(
                    "[SANDBOX] Plugin identity registered (sandbox-local, declarative): $pluginId -> UID:$callerUid, Token:$sandboxIdentityToken"
                )
            } catch (e: Exception) {
                Timber.w(e, "[SANDBOX] Failed to register identity session for declarative plugin: $pluginId")
            }

            // Register main-process session token for UI/FileSystem IPC from sandbox.
            uiController.registerPluginSession(pluginId, sessionToken)

            val metadata = com.ble1st.connectias.plugin.sdk.PluginMetadata(
                pluginId = manifest.pluginId,
                pluginName = manifest.pluginName,
                version = manifest.versionName,
                versionCode = manifest.versionCode,
                author = manifest.developerId,
                minApiLevel = 33,
                maxApiLevel = 36,
                minAppVersion = "1.0.0",
                nativeLibraries = emptyList(),
                fragmentClassName = null,
                description = manifest.description ?: "",
                permissions = emptyList(),
                category = com.ble1st.connectias.plugin.sdk.PluginCategory.DEVELOPMENT,
                dependencies = emptyList()
            )

            // Create plugin context (isolated process has no filesystem access, but bridge can provide access)
            val context = SandboxPluginContext(
                applicationContext,
                null,
                pluginId,
                sessionToken,
                permissionManager,
                hardwareBridge,
                fileSystemBridge,
                messagingBridge,
                uiController = uiController
            )

            val pluginInstance = com.ble1st.connectias.core.plugin.declarative.DeclarativePluginAdapter(
                metadata = metadata,
                uiController = uiController,
                sandboxContext = context,
                pkg = pkg
            )

            val loadSuccess = try {
                PluginExecutionContext.withPlugin(pluginId) {
                    pluginInstance.onLoad(context)
                }
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Declarative plugin onLoad failed: $pluginId")
                false
            }

            if (!loadSuccess) {
                return PluginResultParcel.failure("Declarative plugin onLoad() returned false")
            }

            val enableSuccess = try {
                PluginExecutionContext.withPlugin(pluginId) {
                    pluginInstance.onEnable()
                }
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Declarative plugin onEnable failed: $pluginId")
                false
            }

            val pluginState = if (enableSuccess) PluginState.ENABLED else PluginState.LOADED
            val pluginInfo = SandboxPluginInfo(
                pluginId = pluginId,
                metadata = metadata,
                instance = pluginInstance,
                classLoader = this@PluginSandboxService.classLoader,
                context = context,
                state = pluginState
            )
            this@PluginSandboxService.loadedPlugins[pluginId] = pluginInfo
            this@PluginSandboxService.classLoaders[pluginId] = this@PluginSandboxService.classLoader

            Timber.i("[SANDBOX] Declarative plugin loaded: $pluginId")
            PluginResultParcel.success(PluginMetadataParcel.fromPluginMetadata(metadata))
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to load declarative plugin from bytes")
            PluginResultParcel.failure(e.message ?: "Unknown error")
        }
    }
    
    private fun extractAllDexFromApk(apkBytes: ByteArray): List<ByteArray> {
        val dexFiles = mutableListOf<ByteArray>()
        
        java.util.zip.ZipInputStream(apkBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            
            // Find all classes*.dex files
            while (entry != null) {
                if (entry.name.matches(Regex("classes[0-9]*\\.dex"))) {
                    Timber.d("[SANDBOX] Found DEX entry: ${entry.name}, size: ${entry.size}")
                    dexFiles.add(zip.readBytes())
                }
                entry = zip.nextEntry
            }
        }
        
        if (dexFiles.isEmpty()) {
            throw IllegalArgumentException("No classes*.dex files found in APK")
        }
        
        Timber.d("[SANDBOX] Extracted ${dexFiles.size} DEX files")
        return dexFiles
    }
    
    /**
     * Creates a plugin instance from a class.
     *
     * Supported API surface: com.ble1st.connectias.plugin.sdk.IPlugin only.
     */
    private fun createPluginInstance(pluginClass: Class<*>): IPlugin {
        val instance = pluginClass.getDeclaredConstructor().newInstance()

        if (instance is IPlugin) {
            Timber.d("[SANDBOX] Plugin implements plugin.sdk.IPlugin")
            return instance
        }

        throw ClassCastException(
            "Plugin class ${pluginClass.name} does not implement com.ble1st.connectias.plugin.sdk.IPlugin"
        )
    }

    /**
     * Checks if a class implements IPlugin interface.
     */
    private fun checkIfImplementsIPlugin(clazz: Class<*>): Boolean {
        // Check interfaces directly via reflection (fastest method)
        val interfaces = clazz.interfaces
        for (iface in interfaces) {
            val ifaceName = iface.name
            if (ifaceName == "com.ble1st.connectias.plugin.sdk.IPlugin") {
                Timber.d("[SANDBOX] Class implements IPlugin via interface: $ifaceName")
                return true
            }
        }
        
        // Check superclass interfaces recursively
        var superClass: Class<*>? = clazz.superclass
        while (superClass != null && superClass != Any::class.java) {
            for (iface in superClass.interfaces) {
                val ifaceName = iface.name
                if (ifaceName == "com.ble1st.connectias.plugin.sdk.IPlugin") {
                    Timber.d("[SANDBOX] Class implements IPlugin via superclass interface: $ifaceName")
                    return true
                }
            }
            superClass = superClass.superclass
        }
        
        // Check for app version (plugin.sdk.IPlugin) using isAssignableFrom
        try {
            val sdkIPlugin = com.ble1st.connectias.plugin.sdk.IPlugin::class.java
            if (sdkIPlugin.isAssignableFrom(clazz)) {
                Timber.d("[SANDBOX] Class implements plugin.sdk.IPlugin (app version)")
                return true
            }
        } catch (e: Exception) {
            Timber.v("[SANDBOX] Could not check plugin.sdk.IPlugin: ${e.message}")
        }

        return false
    }
    
    /**
     * Finds the plugin class automatically when fragmentClassName is not specified.
     * Scans for classes that implement IPlugin interface.
     * 
     * This is a fallback for new plugins using Three-Process UI architecture.
     */
    private fun findPluginClass(classLoader: ClassLoader, pluginId: String): String {
        Timber.d("[SANDBOX] Auto-detecting plugin class for: $pluginId")
        
        // Extract package and simple name from pluginId
        val lastDotIndex = pluginId.lastIndexOf('.')
        val packageName = if (lastDotIndex >= 0) pluginId.take(lastDotIndex) else ""
        val simpleName = if (lastDotIndex >= 0) pluginId.substring(lastDotIndex + 1) else pluginId
        
        // Capitalize first letter for class name (e.g., "test2plugin" -> "Test2Plugin")
        val capitalizedName = simpleName.replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase() else it.toString() 
        }
        
        // Try common naming patterns
        val commonPatterns = listOf(
            "$pluginId.Plugin",                                    // com.example.plugin.Plugin
            "$pluginId.$capitalizedName",                          // com.example.plugin.Test2Plugin
            "$pluginId.$capitalizedName" + "Plugin",               // com.example.plugin.Test2PluginPlugin
            if (packageName.isNotEmpty()) "$packageName.Plugin" else "Plugin",  // com.example.Plugin
            if (packageName.isNotEmpty()) "$packageName.$capitalizedName" else capitalizedName,  // com.example.Test2Plugin
            "${pluginId.replace('.', '_')}.Plugin"                // com_example_plugin.Plugin
        ).distinct()
        
        Timber.d("[SANDBOX] Trying patterns: ${commonPatterns.joinToString(", ")}")
        
        // If classLoader is RestrictedClassLoader, use direct DEX loading to bypass parent filtering
        val restrictedLoader = classLoader as? com.ble1st.connectias.core.plugin.security.RestrictedClassLoader
        
        for (className in commonPatterns) {
            try {
                // Try to load class directly from plugin DEX (bypass parent filtering)
                // Plugin classes should be in the DEX, not in parent classloader
                val clazz = restrictedLoader?.// Use direct DEX loading to bypass FilteredParentClassLoader
                loadClassFromDex(className) ?: // Fallback to normal loading
                classLoader.loadClass(className)
                
                // Check if class implements IPlugin (support both .sdk and non-.sdk versions)
                val implementsIPlugin = checkIfImplementsIPlugin(clazz)
                if (implementsIPlugin) {
                    Timber.i("[SANDBOX] Found plugin class via pattern: $className")
                    return className
                } else {
                    Timber.d("[SANDBOX] Class $className found but does not implement IPlugin")
                }
            } catch (e: ClassNotFoundException) {
                Timber.v("[SANDBOX] Class not found in plugin DEX: $className")
                // Try next pattern
            } catch (e: SecurityException) {
                // Security exception from RestrictedClassLoader (forbidden class)
                Timber.v("[SANDBOX] Class is forbidden: $className")
                // Try next pattern
            } catch (e: Exception) {
                Timber.w(e, "[SANDBOX] Error checking class: $className")
            }
        }
        
        // If patterns don't work, try scanning all classes in DEX
        Timber.d("[SANDBOX] Pattern matching failed, scanning DEX for IPlugin implementations...")
        val foundClass = scanDexForPluginClass(classLoader, pluginId)
        if (foundClass != null) {
            Timber.i("[SANDBOX] Found plugin class via DEX scan: $foundClass")
            return foundClass
        }
        
        // If scanning also fails, throw an exception with helpful message
        val errorMessage = buildString {
            appendLine("Plugin class not found for pluginId: $pluginId")
            appendLine()
            appendLine("SOLUTION: Add 'pluginClassName' to your plugin-manifest.json:")
            appendLine("  {")
            appendLine("    \"pluginId\": \"$pluginId\",")
            appendLine("    \"pluginClassName\": \"com.example.YourActualPluginClass\",")
            appendLine("    ...")
            appendLine("  }")
            appendLine()
            appendLine("The plugin class must:")
            appendLine("  1. Implement IPlugin interface")
            appendLine("  2. Be in the plugin's DEX file")
            appendLine("  3. Have a public no-argument constructor")
            appendLine()
            appendLine("Tried patterns:")
            (commonPatterns + listOf("(extended patterns)")).forEach { pattern ->
                appendLine("  - $pattern")
            }
        }
        throw IllegalArgumentException(errorMessage)
    }
    
    /**
     * Scans for plugin class by trying additional naming patterns.
     * This is a fallback when common patterns fail.
     */
    private fun scanDexForPluginClass(classLoader: ClassLoader, pluginId: String): String? {
        val restrictedLoader = classLoader as? com.ble1st.connectias.core.plugin.security.RestrictedClassLoader
            ?: return null
        
        Timber.d("[SANDBOX] Trying extended pattern matching for plugin class...")
        
        // Try additional patterns based on package structure
        val packageParts = pluginId.split('.')
        val extendedPatterns = mutableListOf<String>()
        
        // Generate patterns from package parts
        if (packageParts.size >= 2) {
            val basePackage = packageParts.dropLast(1).joinToString(".")
            val lastPart = packageParts.last()
            val capitalizedLast = lastPart.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
            
            extendedPatterns.addAll(listOf(
                "$basePackage.$lastPart.Plugin",
                "$basePackage.$lastPart.MainPlugin",
                "$basePackage.$capitalizedLast",
                "$basePackage.$capitalizedLast.Plugin",
                "$basePackage.plugin.Plugin",
                "$basePackage.plugins.Plugin"
            ))
        }
        
        // Try single-word variations
        if (packageParts.isNotEmpty()) {
            val lastPart = packageParts.last()
            val capitalized = lastPart.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
            extendedPatterns.addAll(listOf(
                "$pluginId.$capitalized",
                "$pluginId.Main",
                "$pluginId.App"
            ))
        }
        
        Timber.d("[SANDBOX] Trying ${extendedPatterns.size} extended patterns...")
        
        for (className in extendedPatterns) {
            try {
                val clazz = restrictedLoader.loadClassFromDex(className)
                if (checkIfImplementsIPlugin(clazz)) {
                    Timber.i("[SANDBOX] Found IPlugin implementation via extended scan: $className")
                    return className
                }
            } catch (e: ClassNotFoundException) {
                // Continue searching
            } catch (e: Exception) {
                Timber.v("[SANDBOX] Error checking class $className: ${e.message}")
            }
        }
        
        // If patterns fail, scan all classes in DEX
        Timber.d("[SANDBOX] Extended patterns failed, scanning all classes in DEX...")
        return scanAllDexClasses(restrictedLoader, pluginId)
    }
    
    /**
     * Scans all classes in the DEX file to find IPlugin implementations.
     * This is a fallback when pattern matching fails.
     */
    private fun scanAllDexClasses(
        restrictedLoader: com.ble1st.connectias.core.plugin.security.RestrictedClassLoader,
        pluginId: String
    ): String? {
        return try {
            // Get DEX buffers from RestrictedClassLoader via reflection
            val dexBuffersField = restrictedLoader.javaClass.getDeclaredField("dexBuffers")
            dexBuffersField.isAccessible = true
            val dexBuffers = dexBuffersField.get(restrictedLoader) as Array<java.nio.ByteBuffer>
            
            Timber.d("[SANDBOX] Scanning ${dexBuffers.size} DEX file(s) for IPlugin implementations...")
            
            var scannedCount = 0
            val foundClasses = mutableListOf<String>()
            
            // Scan each DEX buffer
            for ((index, dexBuffer) in dexBuffers.withIndex()) {
                try {
                    // Create a temporary DexFile to enumerate classes
                    // Note: DexFile.openInMemory requires API 26+, but we can use reflection
                    val dexFileClass = Class.forName("dalvik.system.DexFile")
                    val openInMemoryMethod = dexFileClass.getMethod("openInMemory", java.nio.ByteBuffer::class.java)
                    val dexFile = openInMemoryMethod.invoke(null, dexBuffer) as dalvik.system.DexFile
                    
                    // Enumerate all class names
                    val classNames = dexFile.entries()
                    while (classNames.hasMoreElements()) {
                        val className = classNames.nextElement()
                        scannedCount++
                        
                        // Skip SDK classes and system classes
                        if (className.startsWith("com.ble1st.connectias.plugin.sdk.") ||
                            className.startsWith("android.") ||
                            className.startsWith("java.") ||
                            className.startsWith("kotlin.") ||
                            className.startsWith("androidx.")) {
                            continue
                        }
                        
                        // Try to load and check the class
                        try {
                            val clazz = restrictedLoader.loadClassFromDex(className)
                            
                            // Check if it implements IPlugin (both versions)
                            if (checkIfImplementsIPlugin(clazz)) {
                                Timber.i("[SANDBOX] Found IPlugin implementation: $className")
                                foundClasses.add(className)
                            }
                        } catch (e: ClassNotFoundException) {
                            // Class not in this DEX, continue
                        } catch (e: SecurityException) {
                            // Class is forbidden, skip
                            Timber.v("[SANDBOX] Skipping forbidden class: $className")
                        } catch (e: Exception) {
                            // Other error, log and continue
                            Timber.v("[SANDBOX] Error checking class $className: ${e.message}")
                        }
                    }
                    
                    dexFile.close()
                } catch (e: Exception) {
                    Timber.w(e, "[SANDBOX] Error scanning DEX buffer $index")
                }
            }
            
            Timber.d("[SANDBOX] Scanned $scannedCount classes, found ${foundClasses.size} IPlugin implementation(s)")
            
            when {
                foundClasses.isEmpty() -> {
                    Timber.w("[SANDBOX] No IPlugin implementations found in DEX scan")
                    null
                }
                foundClasses.size == 1 -> {
                    Timber.i("[SANDBOX] Found single IPlugin implementation: ${foundClasses[0]}")
                    foundClasses[0]
                }
                else -> {
                    // Multiple implementations found - prefer one matching pluginId
                    val preferred = foundClasses.firstOrNull { 
                        it.contains(pluginId.replace(".", ""), ignoreCase = true) ||
                        it.contains(pluginId.split('.').last(), ignoreCase = true)
                    }
                    if (preferred != null) {
                        Timber.i("[SANDBOX] Found ${foundClasses.size} IPlugin implementations, using: $preferred")
                        preferred
                    } else {
                        Timber.w("[SANDBOX] Found ${foundClasses.size} IPlugin implementations: $foundClasses. Using first: ${foundClasses[0]}")
                        foundClasses[0]
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to scan DEX for IPlugin implementations")
            null
        }
    }
}
