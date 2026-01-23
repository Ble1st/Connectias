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
import com.ble1st.connectias.plugin.messaging.PluginMessagingProxy
import android.os.ParcelFileDescriptor
import android.os.Binder
import dalvik.system.DexClassLoader
import dalvik.system.DexFile
import dalvik.system.InMemoryDexClassLoader
import timber.log.Timber
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
            val totalPss = memoryInfo.getTotalPss() * 1024L // Convert KB to bytes
            val totalPrivateDirty = memoryInfo.getTotalPrivateDirty() * 1024L
            val totalSharedDirty = memoryInfo.getTotalSharedDirty() * 1024L
            
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
            if (pluginInfo.lastMemoryCheck > 0 && currentTime > pluginInfo.lastMemoryCheck) {
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
                pluginInfo.instance.onUnload()
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
                pluginInfo.instance.onDisable()
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
                    permissionManager = permissionManager,
                    hardwareBridge = hardwareBridge,
                    fileSystemBridge = fileSystemBridge,
                    messagingBridge = messagingBridge
                )
                
                // Call onLoad
                val loadSuccess = try {
                    pluginInstance.onLoad(pluginContext)
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
                    pluginInfo.instance.onEnable()
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
                val totalPss = memoryInfo.getTotalPss() * 1024L
                
                // Return the estimated memory for this plugin
                // Note: In isolated process, we can't get precise per-plugin memory
                // This is an estimate based on PSS distribution
                pluginInfo.lastMemoryUsage
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to get plugin memory usage")
                -1L
            }
        }
        
        override fun loadPluginFromDescriptor(pluginFd: ParcelFileDescriptor, pluginId: String): PluginResultParcel {
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
                
                // Step 6: Register plugin identity session for security
                val callerUid = Binder.getCallingUid()
                val sessionToken = PluginIdentitySession.registerPluginSession(pluginId, callerUid)
                Timber.i("[SANDBOX] Plugin identity registered: $pluginId -> UID:$callerUid, Token:$sessionToken")
                
                // Step 7: Create secure bridge wrappers (no raw bridge access)
                val secureHardwareBridge = hardwareBridge?.let {
                    SecureHardwareBridgeWrapper(it, this@PluginSandboxService, permissionManager, null)
                }
                val secureFileSystemBridge = fileSystemBridge?.let {
                    SecureFileSystemBridgeWrapper(it, pluginId, permissionManager, null)
                }
                
                // Step 8: Create plugin context (isolated process has no filesystem access)
                val context = SandboxPluginContext(
                    applicationContext,
                    null, // No cache dir in isolated process
                    pluginId,
                    permissionManager,
                    secureHardwareBridge, // Use secure wrapper
                    secureFileSystemBridge, // Use secure wrapper
                    messagingBridge // Messaging bridge for inter-plugin communication (via AIDL)
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
                    pluginInstance.onLoad(context)
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX] Plugin onLoad failed")
                    false
                }
                
                if (!loadSuccess) {
                    return PluginResultParcel.failure("Plugin onLoad() returned false or threw exception")
                }
                
                // Step 10: Enable plugin
                val enableSuccess = try {
                    pluginInstance.onEnable()
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
                Timber.i("[SANDBOX] File system bridge set")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to set file system bridge")
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
                if (this@PluginSandboxService.loadedPlugins.get(pluginId as String) == null) {
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
                if (this@PluginSandboxService.loadedPlugins.get(pluginId as String) == null) {
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
        uiBridge = PluginUIBridgeImpl(loadedPlugins as Map<String, Any>)
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
     * Creates a plugin instance from a class, supporting both IPlugin versions.
     * 
     * Supports:
     * - com.ble1st.connectias.plugin.sdk.IPlugin (app version) - uses UIStateParcel, UserActionParcel
     * - com.ble1st.connectias.plugin.IPlugin (SDK version) - uses UIStateData, UserAction
     * 
     * If SDK version is detected, creates an adapter wrapper that converts types.
     */
    private fun createPluginInstance(pluginClass: Class<*>): IPlugin {
        val instance = pluginClass.getDeclaredConstructor().newInstance()
        
        // Try app version first (plugin.sdk.IPlugin)
        if (instance is IPlugin) {
            Timber.d("[SANDBOX] Plugin implements plugin.sdk.IPlugin (app version)")
            return instance
        }
        
        // Try SDK version (plugin.IPlugin without .sdk)
        try {
            // Check if instance implements plugin.IPlugin (SDK version)
            val pluginIPluginClass = try {
                pluginClass.classLoader?.loadClass("com.ble1st.connectias.plugin.IPlugin")
            } catch (e: ClassNotFoundException) {
                // Try parent classloader
                this::class.java.classLoader?.loadClass("com.ble1st.connectias.plugin.IPlugin")
            }
            
            if (pluginIPluginClass != null && pluginIPluginClass.isInstance(instance)) {
                Timber.d("[SANDBOX] Plugin implements plugin.IPlugin (SDK version), creating adapter")
                // Create adapter wrapper that converts between SDK and app types
                return IPluginSDKAdapter(instance, pluginIPluginClass, this@PluginSandboxService)
            }
        } catch (e: Exception) {
            Timber.w(e, "[SANDBOX] Could not check for plugin.IPlugin interface")
        }
        
        throw ClassCastException(
            "Plugin class ${pluginClass.name} does not implement IPlugin interface. " +
            "Expected: com.ble1st.connectias.plugin.sdk.IPlugin or com.ble1st.connectias.plugin.IPlugin"
        )
    }
    
    /**
     * Adapter wrapper for SDK version of IPlugin (plugin.IPlugin) to app version (plugin.sdk.IPlugin).
     * Converts between UIStateData/UserAction (SDK) and UIStateParcel/UserActionParcel (app).
     */
    private inner class IPluginSDKAdapter(
        private val sdkPlugin: Any,
        private val sdkIPluginClass: Class<*>,
        private val service: PluginSandboxService
    ) : IPlugin {
        
        override fun getMetadata(): com.ble1st.connectias.plugin.sdk.PluginMetadata {
            return try {
                val method = sdkIPluginClass.getMethod("getMetadata")
                val sdkMetadata = method.invoke(sdkPlugin) as? Any
                    ?: throw IllegalStateException("getMetadata() returned null")
                
                // Convert SDK PluginMetadata to app PluginMetadata
                convertPluginMetadata(sdkMetadata)
            } catch (e: Exception) {
                throw RuntimeException("Failed to get metadata from SDK plugin", e)
            }
        }
        
        override fun onLoad(context: com.ble1st.connectias.plugin.sdk.PluginContext): Boolean {
            return try {
                // Load SDK PluginContext class from plugin's classloader (CRITICAL for proxy!)
                val pluginClassLoader = sdkPlugin.javaClass.classLoader
                val sdkPluginContextClass = try {
                    pluginClassLoader?.loadClass("com.ble1st.connectias.plugin.PluginContext")
                } catch (e: ClassNotFoundException) {
                    service::class.java.classLoader?.loadClass("com.ble1st.connectias.plugin.PluginContext")
                }

                if (sdkPluginContextClass == null) {
                    Timber.e("[SANDBOX] Could not load SDK PluginContext class")
                    return false
                }

                // Create SDK PluginContext wrapper with plugin's classloader
                val sdkContext = SDKPluginContextAdapter(context, service, sdkPluginContextClass)

                // Load SDK IPlugin interface to get the onLoad method
                val sdkIPluginInterface = try {
                    pluginClassLoader?.loadClass("com.ble1st.connectias.plugin.IPlugin")
                } catch (e: ClassNotFoundException) {
                    service::class.java.classLoader?.loadClass("com.ble1st.connectias.plugin.IPlugin")
                }

                if (sdkIPluginInterface == null) {
                    Timber.e("[SANDBOX] Could not load SDK IPlugin interface")
                    return false
                }

                val onLoadMethod = sdkIPluginInterface.getMethod("onLoad", sdkPluginContextClass)

                // Call onLoad with SDK context wrapper (proxy)
                onLoadMethod.invoke(sdkPlugin, sdkContext.getProxy()) as? Boolean ?: false
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onLoad on SDK plugin")
                false
            }
        }
        
        override fun onEnable(): Boolean {
            return try {
                val method = sdkIPluginClass.getMethod("onEnable")
                method.invoke(sdkPlugin) as? Boolean ?: false
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onEnable on SDK plugin")
                false
            }
        }
        
        override fun onDisable(): Boolean {
            return try {
                val method = sdkIPluginClass.getMethod("onDisable")
                method.invoke(sdkPlugin) as? Boolean ?: false
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onDisable on SDK plugin")
                false
            }
        }
        
        override fun onUnload(): Boolean {
            return try {
                val method = sdkIPluginClass.getMethod("onUnload")
                method.invoke(sdkPlugin) as? Boolean ?: false
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onUnload on SDK plugin")
                false
            }
        }
        
        override fun onRenderUI(screenId: String): com.ble1st.connectias.plugin.ui.UIStateParcel? {
            return try {
                val method = sdkIPluginClass.getMethod("onRenderUI", String::class.java)
                val sdkUIState = method.invoke(sdkPlugin, screenId)
                
                if (sdkUIState == null) {
                    return null
                }
                
                // Convert UIStateData to UIStateParcel
                convertUIStateDataToParcel(sdkUIState)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onRenderUI on SDK plugin")
                null
            }
        }
        
        override fun onUserAction(action: com.ble1st.connectias.plugin.ui.UserActionParcel) {
            try {
                // Convert UserActionParcel to UserAction (SDK)
                val sdkUserAction = convertUserActionParcelToSDK(action)
                val method = sdkIPluginClass.getMethod("onUserAction", 
                    Class.forName("com.ble1st.connectias.plugin.ui.UserAction"))
                method.invoke(sdkPlugin, sdkUserAction)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onUserAction on SDK plugin")
            }
        }
        
        override fun onUILifecycle(event: String) {
            try {
                val method = sdkIPluginClass.getMethod("onUILifecycle", String::class.java)
                method.invoke(sdkPlugin, event)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to call onUILifecycle on SDK plugin")
            }
        }
        
        // Helper methods for type conversion
        private fun convertPluginMetadata(sdkMetadata: Any): com.ble1st.connectias.plugin.sdk.PluginMetadata {
            // Use reflection to extract fields from SDK PluginMetadata
            val metadataClass = sdkMetadata.javaClass
            return try {
                val pluginId = metadataClass.getMethod("getPluginId").invoke(sdkMetadata) as String
                val pluginName = metadataClass.getMethod("getPluginName").invoke(sdkMetadata) as String
                val version = metadataClass.getMethod("getVersion").invoke(sdkMetadata) as String
                val author = try {
                    metadataClass.getMethod("getAuthor").invoke(sdkMetadata) as? String ?: "Unknown"
                } catch (e: Exception) {
                    "Unknown"
                }
                val description = try {
                    metadataClass.getMethod("getDescription").invoke(sdkMetadata) as? String ?: ""
                } catch (e: Exception) {
                    ""
                }
                
                com.ble1st.connectias.plugin.sdk.PluginMetadata(
                    pluginId = pluginId,
                    pluginName = pluginName,
                    version = version,
                    author = author,
                    description = description,
                    minApiLevel = 33,
                    maxApiLevel = 36,
                    minAppVersion = "1.0.0",
                    nativeLibraries = emptyList(),
                    fragmentClassName = null,
                    permissions = emptyList(),
                    category = com.ble1st.connectias.plugin.sdk.PluginCategory.UTILITY,
                    dependencies = emptyList()
                )
            } catch (e: Exception) {
                throw RuntimeException("Failed to convert PluginMetadata", e)
            }
        }
        
        private fun convertUIStateDataToParcel(sdkUIState: Any): com.ble1st.connectias.plugin.ui.UIStateParcel {
            val stateClass = sdkUIState.javaClass
            return try {
                val screenId = stateClass.getMethod("getScreenId").invoke(sdkUIState) as String
                val title = stateClass.getMethod("getTitle").invoke(sdkUIState) as String
                val data = stateClass.getMethod("getData").invoke(sdkUIState) as android.os.Bundle
                val components = stateClass.getMethod("getComponents").invoke(sdkUIState) as? List<*> ?: emptyList<Any>()
                
                // Convert UIComponentData to UIComponentParcel
                val componentParcels = components.mapNotNull { component ->
                    if (component != null) {
                        convertUIComponentDataToParcel(component)
                    } else {
                        null
                    }
                }
                
                // AIDL-generated classes don't support named arguments, use constructor via reflection
                val parcelClass = Class.forName("com.ble1st.connectias.plugin.ui.UIStateParcel")
                
                // Find constructor - AIDL uses array type for components
                val constructors = parcelClass.constructors
                val constructor = constructors.firstOrNull { it.parameterCount == 5 }
                    ?: throw NoSuchMethodException("UIStateParcel constructor not found")
                
                val timestamp = try {
                    stateClass.getMethod("getTimestamp").invoke(sdkUIState) as? Long 
                        ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                
                // Create array of UIComponentParcel for AIDL
                val componentParcelClass = Class.forName("com.ble1st.connectias.plugin.ui.UIComponentParcel")
                val componentsArray = java.lang.reflect.Array.newInstance(componentParcelClass, componentParcels.size)
                componentParcels.forEachIndexed { index, component ->
                    java.lang.reflect.Array.set(componentsArray, index, component)
                }
                
                constructor.newInstance(screenId, title, data, componentsArray, timestamp) as com.ble1st.connectias.plugin.ui.UIStateParcel
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to convert UIStateData to UIStateParcel")
                throw RuntimeException("Failed to convert UIStateData to UIStateParcel", e)
            }
        }
        
        private fun convertUIComponentDataToParcel(component: Any): com.ble1st.connectias.plugin.ui.UIComponentParcel {
            val compClass = component.javaClass
            return try {
                // Try Kotlin property access first (data class)
                val id = try {
                    compClass.getMethod("getId").invoke(component) as String
                } catch (e: NoSuchMethodException) {
                    compClass.getDeclaredField("id").apply { isAccessible = true }.get(component) as String
                }
                
                val type = try {
                    compClass.getMethod("getType").invoke(component) as String
                } catch (e: NoSuchMethodException) {
                    val typeField = compClass.getDeclaredField("type").apply { isAccessible = true }
                    val typeValue = typeField.get(component)
                    // Handle enum or string
                    when (typeValue) {
                        is String -> typeValue
                        is Enum<*> -> typeValue.name
                        else -> typeValue.toString()
                    }
                }
                
                val properties = try {
                    compClass.getMethod("getProperties").invoke(component) as android.os.Bundle
                } catch (e: NoSuchMethodException) {
                    compClass.getDeclaredField("properties").apply { isAccessible = true }.get(component) as android.os.Bundle
                }
                
                val children = try {
                    val childrenMethod = try {
                        compClass.getMethod("getChildren")
                    } catch (e: NoSuchMethodException) {
                        compClass.getDeclaredField("children").apply { isAccessible = true }
                    }
                    val childrenValue = if (childrenMethod is java.lang.reflect.Method) {
                        childrenMethod.invoke(component)
                    } else {
                        (childrenMethod as java.lang.reflect.Field).get(component)
                    }
                    (childrenValue as? List<*>)?.mapNotNull { it } ?: emptyList<Any>()
                } catch (e: Exception) {
                    emptyList<Any>()
                }
                
                // Create UIComponentParcel using AIDL-generated constructor
                // AIDL generates a constructor with all fields
                val parcelClass = Class.forName("com.ble1st.connectias.plugin.ui.UIComponentParcel")
                
                // Find constructor - AIDL uses array type
                val constructors = parcelClass.constructors
                val constructor = constructors.firstOrNull { it.parameterCount == 4 } 
                    ?: throw NoSuchMethodException("UIComponentParcel constructor not found")
                
                val childrenList = children.mapNotNull { child ->
                    if (child != null) {
                        convertUIComponentDataToParcel(child)
                    } else {
                        null
                    }
                }
                
                // Create array of correct type
                val childrenArray = java.lang.reflect.Array.newInstance(parcelClass, childrenList.size)
                childrenList.forEachIndexed { index, child ->
                    java.lang.reflect.Array.set(childrenArray, index, child)
                }
                
                constructor.newInstance(id, type, properties, childrenArray) as com.ble1st.connectias.plugin.ui.UIComponentParcel
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to convert UIComponentData to UIComponentParcel")
                throw RuntimeException("Failed to convert UIComponentData to UIComponentParcel", e)
            }
        }
        
        private fun convertUserActionParcelToSDK(action: com.ble1st.connectias.plugin.ui.UserActionParcel): Any {
            // Create SDK UserAction object via reflection
            return try {
                val userActionClass = Class.forName("com.ble1st.connectias.plugin.ui.UserAction")
                val constructor = userActionClass.getConstructor(
                    String::class.java,  // actionType
                    String::class.java,  // targetId
                    android.os.Bundle::class.java,  // data
                    Long::class.java  // timestamp
                )
                constructor.newInstance(
                    action.actionType,
                    action.targetId,
                    action.data,
                    action.timestamp
                )
            } catch (e: Exception) {
                throw RuntimeException("Failed to create SDK UserAction", e)
            }
        }
        
        /**
         * Adapter that wraps App PluginContext (plugin.sdk.PluginContext) to SDK PluginContext (plugin.PluginContext).
         * This allows SDK plugins to use the App context seamlessly.
         */
        private inner class SDKPluginContextAdapter(
            private val appContext: com.ble1st.connectias.plugin.sdk.PluginContext,
            private val service: PluginSandboxService,
            private val pluginContextClass: Class<*>
        ) : java.lang.reflect.InvocationHandler {

            // Create proxy instance that implements SDK PluginContext interface from plugin's classloader
            private val sdkContextProxy: Any by lazy {
                try {
                    // Use the plugin's classloader to create the proxy
                    java.lang.reflect.Proxy.newProxyInstance(
                        pluginContextClass.classLoader ?: this::class.java.classLoader,
                        arrayOf(pluginContextClass),
                        this
                    )
                } catch (e: Exception) {
                    throw RuntimeException("Failed to create SDK PluginContext proxy", e)
                }
            }

            fun getProxy(): Any = sdkContextProxy
            
            override fun invoke(proxy: Any?, method: java.lang.reflect.Method?, args: Array<out Any>?): Any? {
                val methodName = method?.name ?: return null
                val argsArray = args ?: emptyArray()
                
                return when (methodName) {
                    "getApplicationContext" -> {
                        appContext.getApplicationContext()
                    }
                    "getPluginDirectory" -> {
                        appContext.getPluginDirectory()
                    }
                    "getNativeLibraryManager" -> {
                        // SDK has INativeLibraryManager, App version might not have this method
                        // Try to get it from app context if available, otherwise create stub
                        try {
                            // Check if app context has getNativeLibraryManager method
                            val nativeLibManagerMethod = try {
                                appContext.javaClass.getMethod("getNativeLibraryManager")
                            } catch (e: NoSuchMethodException) {
                                null
                            }
                            
                            if (nativeLibManagerMethod != null) {
                                val nativeLibManager = nativeLibManagerMethod.invoke(appContext)
                                // Create adapter if needed
                                createSDKNativeLibraryManagerAdapter(nativeLibManager)
                            } else {
                                // App version doesn't have this method, create stub
                                Timber.d("[SANDBOX] App PluginContext doesn't have getNativeLibraryManager, creating stub")
                                createStubNativeLibraryManager()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "[SANDBOX] Could not get native library manager, creating stub")
                            createStubNativeLibraryManager()
                        }
                    }
                    "registerService" -> {
                        if (argsArray.size >= 2) {
                            appContext.registerService(argsArray[0] as String, argsArray[1])
                        }
                        Unit
                    }
                    "getService" -> {
                        if (argsArray.isNotEmpty()) {
                            appContext.getService(argsArray[0] as String)
                        } else {
                            null
                        }
                    }
                    "logDebug" -> {
                        if (argsArray.isNotEmpty()) {
                            appContext.logDebug(argsArray[0] as String)
                        }
                        Unit
                    }
                    "logError" -> {
                        if (argsArray.size >= 1) {
                            val message = argsArray[0] as String
                            val throwable = if (argsArray.size >= 2) argsArray[1] as? Throwable else null
                            appContext.logError(message, throwable)
                        }
                        Unit
                    }
                    "logInfo" -> {
                        if (argsArray.isNotEmpty()) {
                            appContext.logInfo(argsArray[0] as String)
                        }
                        Unit
                    }
                    "logWarning" -> {
                        if (argsArray.isNotEmpty()) {
                            appContext.logWarning(argsArray[0] as String)
                        }
                        Unit
                    }
                    "getUIController" -> {
                        // SDK plugins call getUIController() to get UI controller for Three-Process UI
                        // Must create a proxy that implements the plugin's version of IPluginUIController
                        try {
                            val uiController = service.getUIController()

                            // Load IPluginUIController interface from plugin's classloader
                            val pluginUIControllerClass = try {
                                pluginContextClass.classLoader?.loadClass("com.ble1st.connectias.plugin.ui.IPluginUIController")
                            } catch (e: ClassNotFoundException) {
                                Timber.e(e, "[SANDBOX] Could not load IPluginUIController from plugin classloader")
                                return@invoke null
                            }

                            if (pluginUIControllerClass == null) {
                                Timber.e("[SANDBOX] IPluginUIController class is null")
                                return@invoke null
                            }

                            // Create proxy that implements the plugin's IPluginUIController interface
                            java.lang.reflect.Proxy.newProxyInstance(
                                pluginUIControllerClass.classLoader ?: this::class.java.classLoader,
                                arrayOf(pluginUIControllerClass)
                            ) { _, proxyMethod, proxyArgs ->
                                // Delegate all calls to the real uiController
                                try {
                                    // Find the method by name and parameter count (not exact parameter types)
                                    // because parameter types come from different classloaders
                                    val args = proxyArgs ?: emptyArray()
                                    val realMethod = uiController.javaClass.methods.find {
                                        it.name == proxyMethod.name && it.parameterCount == args.size
                                    } ?: throw NoSuchMethodException("Method ${proxyMethod.name} with ${args.size} parameters not found")

                                    // Convert Parcelable parameters from plugin classloader to app classloader
                                    // This is needed because UIStateParcel from plugin ClassLoader != UIStateParcel from app ClassLoader
                                    val convertedArgs = args.mapIndexed { index, arg ->
                                        if (arg != null && android.os.Parcelable::class.java.isAssignableFrom(arg.javaClass)) {
                                            // Serialize and deserialize the Parcelable to convert between ClassLoaders
                                            try {
                                                val parcel = android.os.Parcel.obtain()
                                                try {
                                                    parcel.writeParcelable(arg as android.os.Parcelable, 0)
                                                    parcel.setDataPosition(0)

                                                    // Get the target parameter type from the real method
                                                    val targetParamType = realMethod.parameterTypes[index]
                                                    val targetClassLoader = targetParamType.classLoader

                                                    // Use readParcelable with the target ClassLoader
                                                    parcel.readParcelable<android.os.Parcelable>(targetClassLoader)
                                                } finally {
                                                    parcel.recycle()
                                                }
                                            } catch (e: Exception) {
                                                Timber.w(e, "[SANDBOX] Failed to convert Parcelable parameter for ${proxyMethod.name}")
                                                arg // Fall back to original arg
                                            }
                                        } else {
                                            arg // Keep non-Parcelable args as-is
                                        }
                                    }.toTypedArray()

                                    realMethod.invoke(uiController, *convertedArgs)
                                } catch (e: Exception) {
                                    Timber.w(e, "[SANDBOX] Failed to invoke UIController method: ${proxyMethod.name}")
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "[SANDBOX] Could not create UI controller proxy: ${e.message}")
                            null
                        }
                    }
                    else -> {
                        Timber.w("[SANDBOX] Unknown method in SDK PluginContext: $methodName")
                        null
                    }
                }
            }
            
            private fun createSDKNativeLibraryManagerAdapter(appManager: Any): Any {
                // Create proxy for INativeLibraryManager
                return try {
                    val sdkINativeLibManagerClass = Class.forName("com.ble1st.connectias.plugin.native.INativeLibraryManager")
                    java.lang.reflect.Proxy.newProxyInstance(
                        sdkINativeLibManagerClass.classLoader,
                        arrayOf(sdkINativeLibManagerClass),
                        NativeLibraryManagerAdapter(appManager)
                    )
                } catch (e: Exception) {
                    Timber.w(e, "[SANDBOX] Could not create SDK NativeLibraryManager adapter")
                    createStubNativeLibraryManager()
                }
            }
            
            private fun createStubNativeLibraryManager(): Any {
                // Create a stub implementation
                return try {
                    val sdkINativeLibManagerClass = Class.forName("com.ble1st.connectias.plugin.native.INativeLibraryManager")
                    java.lang.reflect.Proxy.newProxyInstance(
                        sdkINativeLibManagerClass.classLoader,
                        arrayOf(sdkINativeLibManagerClass),
                        StubInvocationHandler()
                    )
                } catch (e: Exception) {
                    throw RuntimeException("Failed to create stub NativeLibraryManager", e)
                }
            }
        }
        
        /**
         * Adapter for NativeLibraryManager between App and SDK versions
         */
        private inner class NativeLibraryManagerAdapter(
            private val appManager: Any
        ) : java.lang.reflect.InvocationHandler {
            override fun invoke(proxy: Any?, method: java.lang.reflect.Method?, args: Array<out Any>?): Any? {
                val methodName = method?.name ?: return null
                val argsArray = args ?: emptyArray()
                
                return try {
                    // Try to call corresponding method on app manager
                    val appMethod = appManager.javaClass.getMethod(methodName, *method!!.parameterTypes)
                    appMethod.invoke(appManager, *argsArray)
                } catch (e: Exception) {
                    Timber.w(e, "[SANDBOX] Could not delegate $methodName to app NativeLibraryManager")
                    when (methodName) {
                        "isLoaded" -> false
                        "getLoadedLibraries" -> emptyList<String>()
                        else -> null
                    }
                }
            }
        }
        
        /**
         * Stub handler for methods that don't have implementations
         */
        private inner class StubInvocationHandler : java.lang.reflect.InvocationHandler {
            override fun invoke(proxy: Any?, method: java.lang.reflect.Method?, args: Array<out Any>?): Any? {
                val methodName = method?.name ?: return null
                Timber.v("[SANDBOX] Stub method called: $methodName")
                return when (method?.returnType?.name) {
                    "boolean" -> false
                    "int" -> 0
                    "long" -> 0L
                    "java.util.List" -> emptyList<Any>()
                    "void" -> Unit
                    else -> null
                }
            }
        }
    }
    
    /**
     * Checks if a class implements IPlugin interface.
     * Supports both IPlugin versions:
     * - com.ble1st.connectias.plugin.sdk.IPlugin (app version)
     * - com.ble1st.connectias.plugin.IPlugin (SDK version)
     */
    private fun checkIfImplementsIPlugin(clazz: Class<*>): Boolean {
        // Check interfaces directly via reflection (fastest method)
        val interfaces = clazz.interfaces
        for (iface in interfaces) {
            val ifaceName = iface.name
            if (ifaceName == "com.ble1st.connectias.plugin.sdk.IPlugin" || 
                ifaceName == "com.ble1st.connectias.plugin.IPlugin") {
                Timber.d("[SANDBOX] Class implements IPlugin via interface: $ifaceName")
                return true
            }
        }
        
        // Check superclass interfaces recursively
        var superClass: Class<*>? = clazz.superclass
        while (superClass != null && superClass != Any::class.java) {
            for (iface in superClass.interfaces) {
                val ifaceName = iface.name
                if (ifaceName == "com.ble1st.connectias.plugin.sdk.IPlugin" || 
                    ifaceName == "com.ble1st.connectias.plugin.IPlugin") {
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
        
        // Check for SDK version (plugin.IPlugin without .sdk)
        // Try to load the interface from different classloaders
        val classLoadersToTry = listOfNotNull(
            clazz.classLoader,
            this::class.java.classLoader,
            Thread.currentThread().contextClassLoader
        )
        
        for (loader in classLoadersToTry) {
            try {
                val pluginIPlugin = loader.loadClass("com.ble1st.connectias.plugin.IPlugin")
                if (pluginIPlugin.isAssignableFrom(clazz)) {
                    Timber.d("[SANDBOX] Class implements plugin.IPlugin (SDK version)")
                    return true
                }
            } catch (e: ClassNotFoundException) {
                // Interface not found in this classloader, try next
            } catch (e: Exception) {
                Timber.v("[SANDBOX] Error checking plugin.IPlugin in ${loader}: ${e.message}")
            }
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
        val packageName = if (lastDotIndex >= 0) pluginId.substring(0, lastDotIndex) else ""
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
                val clazz = if (restrictedLoader != null) {
                    // Use direct DEX loading to bypass FilteredParentClassLoader
                    restrictedLoader.loadClassFromDex(className)
                } else {
                    // Fallback to normal loading
                    classLoader.loadClass(className)
                }
                
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
            var foundClasses = mutableListOf<String>()
            
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
