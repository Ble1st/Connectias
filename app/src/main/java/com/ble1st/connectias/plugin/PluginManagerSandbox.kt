package com.ble1st.connectias.plugin

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.core.plugin.PluginSandboxProxy
import com.ble1st.connectias.core.plugin.PluginUIProcessProxy
import com.ble1st.connectias.plugin.PluginPermissionException
import com.ble1st.connectias.plugin.PluginPermissionBroadcast
import com.ble1st.connectias.plugin.security.PluginThreadMonitor
import com.ble1st.connectias.plugin.security.EnhancedPluginResourceLimiter
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.IsolatedPluginContextImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * PluginManager implementation that uses a separate sandbox process for plugin execution.
 * This provides crash isolation - plugin crashes do not affect the main app process.
 *
 * Architecture (Three-Process):
 * - Main Process: Orchestrates plugin lifecycle and process connections
 * - Sandbox Process: Runs plugin business logic in isolation (isolatedProcess=true)
 * - UI Process: Renders plugin UI based on state from sandbox (isolatedProcess=false)
 *
 * Process Communication:
 * - Main → Sandbox: PluginSandboxProxy (lifecycle management)
 * - Main → UI: PluginUIProcessProxy (UI lifecycle)
 * - Sandbox → UI: IPluginUIController (state updates)
 * - UI → Sandbox: IPluginUIBridge (user events)
 *
 * This class implements the same API as PluginManager but delegates plugin lifecycle
 * operations to a separate sandbox process via IPC.
 */
class PluginManagerSandbox @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginDirectory: File,
    private val sandboxProxy: PluginSandboxProxy,
    private val moduleRegistry: ModuleRegistry? = null,
    private val threadMonitor: PluginThreadMonitor,
    private val permissionManager: PluginPermissionManager? = null,
    private val resourceLimiter: EnhancedPluginResourceLimiter,
    private val auditManager: SecurityAuditManager,
    private val manifestParser: PluginManifestParser? = null
) {
    
    private val loadedPlugins = ConcurrentHashMap<String, PluginInfo>()
    private val _pluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dexOutputDir = File(context.cacheDir, "plugin_dex")
    private val nativeLibraryManager = NativeLibraryManager(pluginDirectory)

    // Three-Process Architecture: UI Process proxy
    private val uiProcessProxy = PluginUIProcessProxy(context)

    init {
        dexOutputDir.mkdirs()
    }
    
    private fun updateFlow() {
        _pluginsFlow.value = loadedPlugins.values.toList()
    }
    
    data class PluginInfo(
        val pluginId: String,
        val metadata: PluginMetadata,
        val pluginFile: File,
        val instance: IPlugin, // Dummy instance for compatibility - actual plugin runs in sandbox
        val classLoader: DexClassLoader,
        val context: PluginContextImpl,
        var state: PluginState,
        val loadedAt: Long,
        val permissionInfo: PluginPermissionInfo? = null
    )
    
    enum class PluginState {
        LOADED,
        ENABLED,
        DISABLED,
        ERROR
    }
    
    suspend fun initialize(): Result<List<PluginMetadata>> = withContext(Dispatchers.IO) {
        try {
            Timber.i("[PLUGIN MANAGER] Initializing PluginManagerSandbox (Three-Process Architecture)")

            // Initialize thread monitoring for main process plugin UI security
            PluginThreadMonitor.startMonitoring()

            // ========== Connect to Sandbox Process ==========
            val result = sandboxProxy.connect()
            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "Failed to connect to sandbox")
                throw result.exceptionOrNull() ?: Exception("Sandbox connection failed")
            }

            // Connect hardware bridge
            val hardwareConnected = sandboxProxy.connectHardwareBridge()
            if (!hardwareConnected) {
                Timber.w("Failed to connect hardware bridge - hardware access will be unavailable")
            }

            // Connect file system bridge
            val fileSystemConnected = sandboxProxy.connectFileSystemBridge()
            if (!fileSystemConnected) {
                Timber.w("Failed to connect file system bridge - file access will be unavailable")
            }

            // ========== Connect to UI Process (Three-Process Architecture) ==========
            Timber.i("[PLUGIN MANAGER] Connecting to UI Process...")
            val uiConnectResult = uiProcessProxy.connect()
            if (uiConnectResult.isFailure) {
                Timber.e(uiConnectResult.exceptionOrNull(), "Failed to connect to UI Process")
                // Continue initialization without UI Process (degraded mode)
            } else {
                Timber.i("[PLUGIN MANAGER] UI Process connected successfully")

                // ========== Link Sandbox ↔ UI Process ==========
                try {
                    // Get UI Controller from UI Process and set it in Sandbox
                    // The Sandbox will use this to send UI state updates
                    val uiHost = uiProcessProxy.getUIHost()
                    if (uiHost != null) {
                        // Get UI Controller IBinder from UI Process
                        val uiControllerBinder = uiHost.getUIController()
                        if (uiControllerBinder != null) {
                            // Convert to IPluginUIController interface
                            val uiController = com.ble1st.connectias.plugin.ui.IPluginUIController.Stub.asInterface(uiControllerBinder)
                            
                            // Set UI Controller in Sandbox Process
                            val sandboxService = sandboxProxy.getSandboxService()
                            if (sandboxService != null) {
                                val setResult = sandboxProxy.setUIController(uiController)
                                if (setResult) {
                                    Timber.i("[PLUGIN MANAGER] Sandbox ↔ UI Process bridge connected successfully")
                                    
                                    // Get UI Bridge from Sandbox and register it with UI Process
                                    // The UI Bridge receives user events from UI Process
                                    val sandboxService = sandboxProxy.getSandboxService()
                                    if (sandboxService != null) {
                                        try {
                                            val sandboxUIBridge = sandboxService.getUIBridge()
                                            if (sandboxUIBridge != null) {
                                                uiHost.registerUICallback(sandboxUIBridge)
                                                Timber.i("[PLUGIN MANAGER] UI Bridge registered with UI Process")
                                            } else {
                                                Timber.w("[PLUGIN MANAGER] UI Bridge IBinder is null from Sandbox")
                                            }
                                        } catch (e: Exception) {
                                            Timber.e(e, "[PLUGIN MANAGER] Failed to get UI Bridge from Sandbox")
                                        }
                                    } else {
                                        Timber.w("[PLUGIN MANAGER] Sandbox service not available for UI Bridge registration")
                                    }
                                } else {
                                    Timber.e("[PLUGIN MANAGER] Failed to set UI Controller in Sandbox")
                                }
                            } else {
                                Timber.w("[PLUGIN MANAGER] Sandbox service not available for UI bridge setup")
                            }
                        } else {
                            Timber.e("[PLUGIN MANAGER] UI Controller IBinder is null from UI Process")
                        }
                    } else {
                        Timber.w("[PLUGIN MANAGER] UI Host not available from UI Process")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[PLUGIN MANAGER] Failed to link Sandbox ↔ UI Process")
                }
            }
            
            if (!pluginDirectory.exists()) {
                pluginDirectory.mkdirs()
            }
            
            // Copy plugins from assets on first run
            copyPluginsFromAssets()
            
            val pluginFiles = pluginDirectory.listFiles { file ->
                file.extension in listOf("apk", "jar")
            } ?: emptyArray()
            
            Timber.d("Found ${pluginFiles.size} plugin files")
            
            val loadedMetadata = mutableListOf<PluginMetadata>()
            
            pluginFiles.forEach { pluginFile ->
                try {
                    val loadResult = loadPlugin(pluginFile)
                    loadResult.onSuccess { metadata ->
                        loadedMetadata.add(metadata)
                        Timber.i("Loaded plugin in sandbox: ${metadata.pluginName} v${metadata.version}")
                    }.onFailure { error ->
                        Timber.e(error, "Failed to load plugin: ${pluginFile.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading plugin: ${pluginFile.name}")
                }
            }
            
            Timber.i("Plugin initialization completed: ${loadedMetadata.size} plugins loaded")
            Result.success(loadedMetadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize plugin manager")
            Result.failure(e)
        }
    }
    
    private suspend fun loadPlugin(pluginFile: File): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Extract metadata locally first
            var metadata = extractMetadata(pluginFile)
                ?: return@withContext Result.failure(Exception("Failed to extract metadata"))
            
            // Extract and validate permissions from APK + JSON
            val permissionInfo = manifestParser?.extractPermissions(pluginFile)?.getOrNull()
            
            if (permissionInfo != null) {
                // Block plugins with critical permissions
                if (permissionInfo.hasCriticalPermissions()) {
                    Timber.e("Plugin ${metadata.pluginName} requests critical permissions: ${permissionInfo.critical}")
                    return@withContext Result.failure(
                        SecurityException(
                            "Plugin requests critical permissions: ${permissionInfo.critical.joinToString()}"
                        )
                    )
                }
                
                // Update metadata with merged permissions (APK + JSON)
                // permissionInfo.allPermissions already contains both sources merged
                metadata = metadata.copy(permissions = permissionInfo.allPermissions)
                
                Timber.i("Plugin ${metadata.pluginName} permissions merged: ${permissionInfo.allPermissions.size} total, " +
                    "${permissionInfo.dangerous.size} dangerous, ${permissionInfo.critical.size} critical")
            }
            
            // Load plugin in sandbox process via IPC using ParcelFileDescriptor
            // This works even with isolatedProcess="true"
            val sandboxResult = sandboxProxy.loadPluginFromFile(pluginFile, metadata.pluginId)
            if (sandboxResult.isFailure) {
                return@withContext Result.failure(
                    sandboxResult.exceptionOrNull() ?: Exception("Sandbox load failed")
                )
            }
            
            // Extract native libraries if any (for fragment creation in main process)
            if (metadata.nativeLibraries.isNotEmpty()) {
                nativeLibraryManager.extractNativeLibraries(pluginFile, metadata.pluginId)
            }
            
            // Create local ClassLoader for fragment creation (fragments must run in main process)
            val pluginDexDir = File(dexOutputDir, metadata.pluginId)
            pluginDexDir.mkdirs()
            
            val classLoader = DexClassLoader(
                pluginFile.absolutePath,
                pluginDexDir.absolutePath,
                null,
                context.classLoader
            )
            
            // Create plugin context with permission manager
            val pluginDataDir = File(pluginDirectory, "data/${metadata.pluginId}")
            pluginDataDir.mkdirs()
            
            val pluginContext = PluginContextImpl(
                context,
                metadata.pluginId,
                pluginDataDir,
                nativeLibraryManager,
                permissionManager ?: PluginPermissionManager(context)
            )
            
            // Create a dummy plugin instance for compatibility
            // The actual plugin instance runs in the sandbox process
            // This dummy instance is only used for type compatibility
            val dummyInstance = object : IPlugin {
                override fun getMetadata(): PluginMetadata = metadata
                override fun onLoad(context: com.ble1st.connectias.plugin.sdk.PluginContext): Boolean = true
                override fun onEnable(): Boolean = true
                override fun onDisable(): Boolean = true
                override fun onUnload(): Boolean = true
            }
            
            // Store plugin info with permission information
            val pluginInfo = PluginInfo(
                pluginId = metadata.pluginId,
                metadata = metadata,
                pluginFile = pluginFile,
                instance = dummyInstance, // Dummy for compatibility - actual plugin in sandbox
                classLoader = classLoader,
                context = pluginContext,
                state = PluginState.LOADED,
                loadedAt = System.currentTimeMillis(),
                permissionInfo = permissionInfo
            )
            
            loadedPlugins[metadata.pluginId] = pluginInfo
            updateFlow()
            
            // Register plugin in ModuleRegistry when loaded
            moduleRegistry?.let { registry ->
                val moduleMetadata = com.ble1st.connectias.core.module.ModuleCatalog.ModuleMetadata(
                    id = metadata.pluginId,
                    name = metadata.pluginName,
                    version = metadata.version,
                    fragmentClassName = metadata.fragmentClassName ?: "",
                    category = com.ble1st.connectias.core.module.ModuleCatalog.ModuleCategory.UTILITY,
                    isCore = false,
                    description = metadata.description
                )
                registry.registerFromMetadata(moduleMetadata, isActive = false)
                Timber.i("[PLUGIN MANAGER] Registered plugin in ModuleRegistry: ${metadata.pluginId} (inactive)")
            }
            
            Timber.i("Plugin loaded in sandbox: ${metadata.pluginName}")
            Result.success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: ${pluginFile.name}")
            Result.failure(e)
        }
    }
    
    private fun extractMetadata(pluginFile: File): PluginMetadata? {
        return try {
            java.util.zip.ZipFile(pluginFile).use { zip ->
                // Try both locations: root (APK) and assets/ (AAR)
                val manifestEntry = zip.getEntry("plugin-manifest.json")
                    ?: zip.getEntry("assets/plugin-manifest.json")
                    ?: return null
                
                val jsonString = zip.getInputStream(manifestEntry).bufferedReader().readText()
                val json = JSONObject(jsonString)
                
                val requirements = json.optJSONObject("requirements") ?: JSONObject()
                
                PluginMetadata(
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
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract metadata from: ${pluginFile.name}")
            null
        }
    }
    
    fun getLoadedPlugins(): List<PluginInfo> {
        return loadedPlugins.values.toList()
    }
    
    fun getEnabledPlugins(): List<PluginInfo> {
        return loadedPlugins.values.filter { it.state == PluginState.ENABLED }
    }
    
    fun getPlugin(pluginId: String): PluginInfo? {
        return loadedPlugins[pluginId]
    }
    
    /**
     * Creates a Fragment instance from a plugin.
     *
     * Supports both:
     * - Legacy plugins: Uses fragmentClassName to load Fragment directly from ClassLoader
     * - New plugins: Uses Three-Process Architecture with onRenderUI() API
     *
     * @param pluginId The plugin ID
     * @param onCriticalError Callback invoked when plugin encounters critical error
     */
    fun createPluginFragment(
        pluginId: String,
        onCriticalError: (() -> Unit)? = null
    ): androidx.fragment.app.Fragment? {
        return createIsolatedPluginFragment(pluginId, onCriticalError)
    }

    /**
     * Creates a Fragment instance from a plugin.
     *
     * Supports both legacy plugins (fragmentClassName) and new plugins (onRenderUI()).
     *
     * @param pluginId The plugin ID
     * @param onCriticalError Callback invoked when plugin encounters critical error
     */
    private fun createIsolatedPluginFragment(
        pluginId: String,
        onCriticalError: (() -> Unit)? = null
    ): androidx.fragment.app.Fragment? {
        val pluginInfo = loadedPlugins[pluginId] ?: run {
            Timber.e("[PLUGIN MANAGER] Plugin not found: $pluginId")
            return null
        }

        // Support legacy plugins with fragmentClassName
        if (!pluginInfo.metadata.fragmentClassName.isNullOrEmpty()) {
            Timber.i("[PLUGIN MANAGER] Legacy plugin detected: ${pluginInfo.metadata.pluginName}, using legacy fragment creation")
            return createLegacyPluginFragment(pluginId, pluginInfo, onCriticalError)
        }

        // Check if UI Process is available for new UI API plugins
        if (!uiProcessProxy.isConnected()) {
            Timber.e("[PLUGIN MANAGER] UI Process not connected - cannot create fragment for plugin: $pluginId")
            return null
        }

        Timber.i("[PLUGIN MANAGER] Creating UI Process fragment for plugin: $pluginId")
        // Use runBlocking to call suspend function from non-suspend context
        return runBlocking {
            createUIProcessFragment(pluginId, pluginInfo, onCriticalError)
        }
    }

    /**
     * Creates a Fragment instance from a legacy plugin (with fragmentClassName).
     * The fragment class is loaded from the plugin's ClassLoader.
     */
    private fun createLegacyPluginFragment(
        pluginId: String,
        pluginInfo: PluginInfo,
        onCriticalError: (() -> Unit)? = null
    ): androidx.fragment.app.Fragment? {
        return try {
            val fragmentClassName = pluginInfo.metadata.fragmentClassName
                ?: return null
            
            Timber.d("[PLUGIN MANAGER] Loading legacy fragment class: $fragmentClassName")
            val fragmentClass = pluginInfo.classLoader.loadClass(fragmentClassName)
            val fragment = fragmentClass.getDeclaredConstructor().newInstance() as? androidx.fragment.app.Fragment
                ?: throw ClassCastException("Plugin class is not a Fragment")
            
            Timber.i("[PLUGIN MANAGER] Legacy fragment created successfully: $pluginId")
            fragment
        } catch (e: Exception) {
            Timber.e(e, "[PLUGIN MANAGER] Failed to create legacy fragment for plugin: $pluginId")
            onCriticalError?.invoke()
            null
        }
    }

    /**
     * Creates a fragment using UI Process (Three-Process Architecture).
     *
     * Initializes UI in UI Process and creates a container fragment in Main Process.
     *
     * @param pluginId Plugin identifier
     * @param pluginInfo Plugin information
     * @param onCriticalError Error callback
     * @return Container fragment or null on error
     */
    private suspend fun createUIProcessFragment(
        pluginId: String,
        pluginInfo: PluginInfo,
        onCriticalError: (() -> Unit)? = null
    ): androidx.fragment.app.Fragment? = withContext(Dispatchers.IO) {
        try {
            Timber.i("[PLUGIN MANAGER] Creating UI Process fragment for plugin: $pluginId")

            // Initialize UI in UI Process
            val configuration = Bundle().apply {
                putString("pluginId", pluginId)
                // Add plugin-specific configuration
            }

            val containerId = uiProcessProxy.initializePluginUI(pluginId, configuration)
            if (containerId == -1) {
                Timber.e("[PLUGIN MANAGER] Failed to initialize UI in UI Process for plugin: $pluginId")
                return@withContext null
            }

            Timber.i("[PLUGIN MANAGER] UI initialized in UI Process: $pluginId (containerId: $containerId)")

            // Create container fragment in Main Process
            val containerFragment = com.ble1st.connectias.core.plugin.ui.PluginUIContainerFragment.newInstance(
                pluginId,
                containerId
            )

            // Set UI Process proxy for Surface communication
            containerFragment.setUIProcessProxy(uiProcessProxy)

            Timber.i("[PLUGIN MANAGER] UI Process fragment created successfully: $pluginId")
            containerFragment
        } catch (e: Exception) {
            Timber.e(e, "[PLUGIN MANAGER] Failed to create UI Process fragment for plugin: $pluginId")
            null
        }
    }

    
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.ENABLED) {
                return@withContext Result.success(Unit)
            }
            
            // Validate permissions before enabling
            if (permissionManager != null) {
                val validationResult = permissionManager.validatePermissions(pluginInfo.metadata)
                if (validationResult.isFailure) {
                    Timber.e(validationResult.exceptionOrNull(), "Permission validation failed for plugin: $pluginId")
                    return@withContext Result.failure(
                        validationResult.exceptionOrNull() ?: Exception("Permission validation failed")
                    )
                }
                
                val permValidation = validationResult.getOrNull()
                if (permValidation != null && !permValidation.isValid) {
                    if (permValidation.requiresUserConsent) {
                        // Return special error code for UI to show permission dialog
                        // Use allRequestedPermissions instead of just dangerousPermissions
                        Timber.i("Plugin $pluginId requires user consent for permissions: ${permValidation.allRequestedPermissions}")
                        return@withContext Result.failure(
                            PluginPermissionException(
                                "User consent required for: ${permValidation.allRequestedPermissions.joinToString()}",
                                permValidation.allRequestedPermissions
                            )
                        )
                    }
                    
                    // Other validation failure (e.g., critical permissions)
                    Timber.e("Plugin $pluginId permission validation failed: ${permValidation.reason}")
                    return@withContext Result.failure(
                        SecurityException(permValidation.reason)
                    )
                }
                
                Timber.d("Plugin $pluginId permission validation passed")
            }
            
            // If plugin is in ERROR state, it might have been killed in sandbox
            // We need to reload it completely in the sandbox process
            if (pluginInfo.state == PluginState.ERROR) {
                Timber.i("Plugin is in ERROR state, reloading in sandbox: $pluginId")
                
                // Try to reload plugin in sandbox (will fail if already loaded, that's ok)
                val reloadResult = sandboxProxy.loadPluginFromFile(pluginInfo.pluginFile, pluginId)
                reloadResult.onSuccess {
                    Timber.i("Plugin reloaded in sandbox after ERROR: $pluginId")
                }.onFailure { error ->
                    // If reload fails, it might be already loaded - try to disable first
                    Timber.w(error, "Plugin reload failed, trying disable+load: $pluginId")
                    sandboxProxy.disablePlugin(pluginId) // Ignore result
                    sandboxProxy.unloadPlugin(pluginId) // Ignore result
                    
                    // Try loading again
                    val secondLoadResult = sandboxProxy.loadPluginFromFile(pluginInfo.pluginFile, pluginId)
                    secondLoadResult.onFailure { secondError ->
                        Timber.e(secondError, "Failed to reload plugin after ERROR: $pluginId")
                    }
                }
                
                // Update local state to DISABLED before enabling
                loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
                updateFlow()
            }
            
            // Register plugin with network policy (default: normal network access)
            try {
                // Get hardware bridge service and register plugin
                val hardwareBridge = sandboxProxy.getHardwareBridge()
                hardwareBridge?.registerPluginNetworking(pluginId, false) // false = full network access
                Timber.i("[PLUGIN MANAGER] Plugin registered for network access: $pluginId")
            } catch (e: Exception) {
                auditManager.logSecurityEvent(
                    eventType = SecurityAuditManager.SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
                    severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                    source = "PluginManagerSandbox",
                    pluginId = pluginId,
                    message = "Failed to register plugin networking",
                    exception = e
                )
                Timber.w(e, "[PLUGIN MANAGER] Failed to register plugin networking: $pluginId")
                // Don't fail plugin enable for network registration failure
            }
            
            // Register plugin with enhanced resource limiter
            try {
                // Get sandbox PID for resource monitoring
                val sandboxPid = sandboxProxy.getSandboxPid()
                if (sandboxPid > 0) {
                    val resourceLimits = EnhancedPluginResourceLimiter.ResourceLimits(
                        maxMemoryMB = 300, // 300MB limit per plugin (adjusted for mobile)
                        maxCpuPercent = 40f, // 40% CPU limit
                        maxDiskUsageMB = 1024, // 1GB disk limit
                        maxThreads = 15, // 15 threads max
                        emergencyMemoryMB = 200 // Emergency kill at 200MB
                    )
                    resourceLimiter.registerPlugin(pluginId, sandboxPid, resourceLimits)
                    Timber.i("[PLUGIN MANAGER] Plugin registered with resource limiter: $pluginId (PID: $sandboxPid)")
                } else {
                    Timber.w("[PLUGIN MANAGER] Could not get sandbox PID for resource limiting: $pluginId")
                }
            } catch (e: Exception) {
                auditManager.logSecurityEvent(
                    eventType = SecurityAuditManager.SecurityEventType.SECURITY_CONFIGURATION_CHANGE,
                    severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                    source = "PluginManagerSandbox",
                    pluginId = pluginId,
                    message = "Failed to register plugin with resource limiter",
                    exception = e
                )
                Timber.w(e, "[PLUGIN MANAGER] Failed to register plugin with resource limiter: $pluginId")
            }
            
            // Enable plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.enablePlugin(pluginId)
            if (sandboxResult.isFailure) {
                // Create new PluginInfo with ERROR state to trigger Compose recomposition
                loadedPlugins[pluginId] = loadedPlugins[pluginId]?.copy(state = PluginState.ERROR) 
                    ?: pluginInfo.copy(state = PluginState.ERROR)
                updateFlow()
                return@withContext Result.failure(
                    sandboxResult.exceptionOrNull() ?: Exception("Sandbox enable failed")
                )
            }
            
            // Create new PluginInfo with ENABLED state to trigger Compose recomposition
            loadedPlugins[pluginId] = loadedPlugins[pluginId]?.copy(state = PluginState.ENABLED)
                ?: pluginInfo.copy(state = PluginState.ENABLED)
            updateFlow()
            
            // Update ModuleRegistry to make plugin visible in navigation
            if (moduleRegistry != null) {
                moduleRegistry.updateModuleState(pluginId, isActive = true)
                Timber.i("[PLUGIN MANAGER] Updated ModuleRegistry for plugin: $pluginId (isActive=true)")
            } else {
                Timber.w("[PLUGIN MANAGER] ModuleRegistry is NULL - plugin will not appear in navigation: $pluginId")
            }
            
            // Thread monitoring is already started globally
            
            Timber.i("Plugin enabled in sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin: $pluginId")
            // Update to ERROR state on exception
            loadedPlugins[pluginId]?.let { currentInfo ->
                loadedPlugins[pluginId] = currentInfo.copy(state = PluginState.ERROR)
                updateFlow()
            }
            Result.failure(e)
        }
    }
    
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            if (pluginInfo.state == PluginState.DISABLED) {
                return@withContext Result.success(Unit)
            }
            
            // Disable plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.disablePlugin(pluginId)
            if (sandboxResult.isFailure) {
                Timber.w(sandboxResult.exceptionOrNull(), "Sandbox disable failed, but continuing")
            }
            
            // Unregister plugin from network policy
            try {
                val hardwareBridge = sandboxProxy.getHardwareBridge()
                hardwareBridge?.unregisterPluginNetworking(pluginId)
                Timber.i("[PLUGIN MANAGER] Plugin unregistered from network access: $pluginId")
            } catch (e: Exception) {
                Timber.w(e, "[PLUGIN MANAGER] Failed to unregister plugin networking: $pluginId")
                // Don't fail plugin disable for network unregistration failure
            }
            
            // Unregister plugin from resource limiter
            try {
                resourceLimiter.unregisterPlugin(pluginId)
                Timber.i("[PLUGIN MANAGER] Plugin unregistered from resource limiter: $pluginId")
            } catch (e: Exception) {
                Timber.w(e, "[PLUGIN MANAGER] Failed to unregister plugin from resource limiter: $pluginId")
            }
            
            // Create new PluginInfo with DISABLED state to trigger Compose recomposition
            loadedPlugins[pluginId] = pluginInfo.copy(state = PluginState.DISABLED)
            updateFlow()
            
            // Update ModuleRegistry to hide plugin from navigation
            moduleRegistry?.updateModuleState(pluginId, isActive = false)
            
            Timber.i("Plugin disabled in sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pluginInfo = loadedPlugins.remove(pluginId)
                ?: return@withContext Result.failure(Exception("Plugin not found: $pluginId"))
            
            // Disable first if enabled
            if (pluginInfo.state == PluginState.ENABLED) {
                sandboxProxy.disablePlugin(pluginId)
            }
            
            // Unload plugin in sandbox process via IPC
            val sandboxResult = sandboxProxy.unloadPlugin(pluginId)
            if (sandboxResult.isFailure) {
                Timber.w(sandboxResult.exceptionOrNull(), "Sandbox unload failed, but continuing")
            }
            
            // Cleanup local resources
            nativeLibraryManager.cleanupLibraries(pluginId)
            File(dexOutputDir, pluginId).deleteRecursively()
            
            // Delete the actual plugin file to prevent re-loading on app restart
            try {
                if (pluginInfo.pluginFile.exists()) {
                    val deleted = pluginInfo.pluginFile.delete()
                    if (deleted) {
                        Timber.i("Plugin file deleted: ${pluginInfo.pluginFile.absolutePath}")
                    } else {
                        Timber.w("Failed to delete plugin file: ${pluginInfo.pluginFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete plugin file: ${pluginInfo.pluginFile.absolutePath}")
            }
            
            // Delete plugin data directory
            try {
                val pluginDataDir = File(pluginDirectory, "data/${pluginId}")
                if (pluginDataDir.exists()) {
                    val deleted = pluginDataDir.deleteRecursively()
                    if (deleted) {
                        Timber.i("Plugin data directory deleted: ${pluginDataDir.absolutePath}")
                    } else {
                        Timber.w("Failed to delete plugin data directory: ${pluginDataDir.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to delete plugin data directory for plugin: $pluginId")
            }
            
            updateFlow()
            
            // Remove from ModuleRegistry
            moduleRegistry?.unregisterModule(pluginId)
            
            Timber.i("Plugin unloaded from sandbox: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun loadAndEnablePlugin(pluginId: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Check if plugin is already loaded
            val existingPlugin = loadedPlugins[pluginId]
            if (existingPlugin != null) {
                if (existingPlugin.state != PluginState.ENABLED) {
                    enablePlugin(pluginId)
                }
                return@withContext Result.success(existingPlugin.metadata)
            }
            
            // Find plugin file
            val exactMatchFile = File(pluginDirectory, "$pluginId.apk")
            val pluginFile = if (exactMatchFile.exists() && exactMatchFile.extension in listOf("apk", "jar")) {
                exactMatchFile
            } else {
                pluginDirectory.listFiles { file ->
                    file.extension in listOf("apk", "jar")
                }?.firstOrNull { file ->
                    try {
                        val metadata = extractMetadata(file)
                        metadata?.pluginId == pluginId
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            
            if (pluginFile == null) {
                return@withContext Result.failure(Exception("Plugin file not found for ID: $pluginId"))
            }
            
            // Load the plugin only (don't enable automatically)
            val loadResult = loadPlugin(pluginFile)
            loadResult.onSuccess { metadata ->
                Timber.i("Plugin loaded in sandbox (not enabled): ${metadata.pluginName} v${metadata.version}")
                // Note: Plugin is loaded but not enabled - user must enable it manually
            }
            
            loadResult
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin: $pluginId")
            Result.failure(e)
        }
    }
    
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        try {
            Timber.i("[PLUGIN MANAGER] Shutting down PluginManagerSandbox (Three-Process Architecture)")

            // Disable all enabled plugins first
            val enabledPlugins = getEnabledPlugins()
            enabledPlugins.forEach { pluginInfo ->
                try {
                    disablePlugin(pluginInfo.pluginId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to disable plugin during shutdown: ${pluginInfo.pluginId}")
                }
            }

            // Stop thread monitoring
            PluginThreadMonitor.stopMonitoring()

            // Disconnect from UI Process
            try {
                uiProcessProxy.disconnect()
                Timber.i("[PLUGIN MANAGER] Disconnected from UI Process")
            } catch (e: Exception) {
                Timber.e(e, "[PLUGIN MANAGER] Error disconnecting from UI Process")
            }

            // Disconnect from sandbox
            sandboxProxy.disconnect()

            // Clear all loaded plugins
            loadedPlugins.clear()
            updateFlow()

            Timber.i("[PLUGIN MANAGER] PluginManagerSandbox shutdown completed")
        } catch (e: Exception) {
            Timber.e(e, "Error during PluginManagerSandbox shutdown")
        }
    }
    
    /**
     * Copy plugins from assets to plugin directory
     */
    private fun copyPluginsFromAssets() {
        try {
            val assetManager = context.assets
            val assetPlugins = assetManager.list("plugins") ?: emptyArray()
            
            assetPlugins.forEach { assetFile ->
                if (assetFile.endsWith(".apk") || assetFile.endsWith(".jar")) {
                    val targetFile = File(pluginDirectory, assetFile)
                    
                    if (!targetFile.exists()) {
                        assetManager.open("plugins/$assetFile").use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        targetFile.setReadOnly()
                        Timber.i("Copied plugin from assets: $assetFile")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy plugins from assets")
        }
    }
}

/**
 * Exception thrown when a plugin requires user consent for permissions
 */
class PluginPermissionException(
    message: String,
    val requiredPermissions: List<String>
) : Exception(message)
