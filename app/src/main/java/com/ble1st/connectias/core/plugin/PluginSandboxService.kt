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
    
    // Broadcast receiver for permission changes
    private var permissionBroadcastReceiver: BroadcastReceiver? = null
    
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
        var lastMemoryUsage: Long = 0L
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
            // Better estimation based on plugin metadata and actual PSS distribution
            val pluginCount = loadedPlugins.size
            val baseMemory = totalPss / pluginCount // Distribute PSS evenly
            
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
            
            // Update last known memory
            pluginInfo.lastMemoryUsage = adjustedMemory
            
            // Check limits
            when {
                adjustedMemory >= pluginCriticalLimit -> {
                    Timber.e("[SANDBOX] Plugin '${pluginInfo.pluginId}' CRITICAL memory: ${formatBytes(adjustedMemory)} - auto-unloading")
                    performUnload(pluginInfo.pluginId)
                }
                adjustedMemory >= pluginWarningLimit -> {
                    Timber.w("[SANDBOX] Plugin '${pluginInfo.pluginId}' WARNING memory: ${formatBytes(adjustedMemory)}")
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
                val pluginClass = classLoader.loadClass(
                    metadata.fragmentClassName ?: throw IllegalArgumentException("fragmentClassName required")
                )
                val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as? IPlugin
                    ?: throw ClassCastException("Plugin does not implement IPlugin")
                
                // Create minimal PluginContext for sandbox with permission manager and hardware bridge
                val pluginDir = File(filesDir, "sandbox_plugins/${metadata.pluginId}")
                val pluginContext = SandboxPluginContext(
                    appContext = applicationContext,
                    pluginDir = pluginDir,
                    pluginId = metadata.pluginId,
                    permissionManager = permissionManager,
                    hardwareBridge = hardwareBridge
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
                
                // Step 3: Extract ALL DEX files from APK for InMemoryDexClassLoader
                val dexBytesList = extractAllDexFromApk(apkBytes)
                val dexBuffers = dexBytesList.map { java.nio.ByteBuffer.wrap(it) }.toTypedArray()
                val classLoader = InMemoryDexClassLoader(dexBuffers, this@PluginSandboxService.classLoader)
                
                // Step 4: Debug - Show metadata
                Timber.d("[SANDBOX] Plugin metadata: fragmentClassName=${metadata.fragmentClassName}")
                Timber.d("[SANDBOX] Attempting to load class: ${metadata.fragmentClassName}")
                
                // Step 5: Load plugin class
                val pluginClass = classLoader.loadClass(metadata.fragmentClassName ?: throw IllegalArgumentException("fragmentClassName required"))
                val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as? IPlugin
                    ?: throw ClassCastException("Plugin does not implement IPlugin")
                
                // Step 6: Register plugin identity session for security
                val callerUid = Binder.getCallingUid()
                val sessionToken = PluginIdentitySession.registerPluginSession(pluginId, callerUid)
                Timber.i("[SANDBOX] Plugin identity registered: $pluginId -> UID:$callerUid, Token:$sessionToken")
                
                // Step 7: Create secure bridge wrappers (no raw bridge access)
                val secureHardwareBridge = hardwareBridge?.let { 
                    SecureHardwareBridgeWrapper(it, this@PluginSandboxService, null)
                }
                val secureFileSystemBridge = fileSystemBridge?.let {
                    SecureFileSystemBridgeWrapper(it, pluginId)
                }
                
                // Step 8: Create plugin context (isolated process has no filesystem access)
                val context = SandboxPluginContext(
                    applicationContext,
                    null, // No cache dir in isolated process
                    pluginId,
                    permissionManager,
                    secureHardwareBridge, // Use secure wrapper
                    secureFileSystemBridge // Use secure wrapper
                )
                
                // Step 9: Store plugin info
                val pluginInfo = SandboxPluginInfo(
                    pluginId = pluginId,
                    metadata = metadata,
                    instance = pluginInstance,
                    classLoader = classLoader,
                    context = context,
                    state = PluginState.ENABLED
                )
                this@PluginSandboxService.loadedPlugins[pluginId] = pluginInfo
                this@PluginSandboxService.classLoaders[pluginId] = classLoader
                
                // Step 10: Initialize plugin
                pluginInstance.onLoad(context)
                pluginInstance.onEnable()
                
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
        
        // Note: No broadcast receiver to unregister (isolated process restriction)
        
        // Stop memory monitoring
        memoryMonitor.stopMonitoring()
        memoryMonitor.logMemoryStats()
    }
    
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
                fragmentClassName = json.getString("fragmentClassName"),
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
                fragmentClassName = json.getString("fragmentClassName"),
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
}
