package com.ble1st.connectias.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.core.servicestate.ServiceIds
import com.ble1st.connectias.core.servicestate.ServiceStateRepository
import com.ble1st.connectias.hardware.HardwareBridgeService
import com.ble1st.connectias.hardware.IHardwareBridge
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.IPluginSandbox
import com.ble1st.connectias.plugin.logging.PluginLogBridgeImpl
import com.ble1st.connectias.plugin.messaging.IPluginMessaging
import com.ble1st.connectias.plugin.messaging.PluginMessagingService
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.security.IPCRateLimiter
import com.ble1st.connectias.plugin.security.PluginIdentitySession
import com.ble1st.connectias.plugin.security.RateLimitException
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Proxy for communicating with the plugin sandbox service via IPC
 */
class PluginSandboxProxy(
    private val context: Context,
    private val auditManager: SecurityAuditManager? = null,
    private val pluginLogBridge: PluginLogBridgeImpl? = null,
    private val serviceStateRepository: ServiceStateRepository? = null
) {
    
    private var sandboxService: IPluginSandbox? = null
    private var hardwareBridgeService: IHardwareBridge? = null
    private var messagingBridgeService: IPluginMessaging? = null
    private var fileSystemBridgeConnection: ServiceConnection? = null
    private val isConnected = AtomicBoolean(false)
    private val isHardwareBridgeConnected = AtomicBoolean(false)
    private val isFileSystemBridgeConnected = AtomicBoolean(false)
    private val isMessagingBridgeConnected = AtomicBoolean(false)
    private val connectionLock = Any()
    private val hardwareBridgeLock = Any()
    private val messagingBridgeLock = Any()
    private var bindJob: Job? = null
    private val rateLimiter = IPCRateLimiter()
    
    companion object {
        private const val BIND_TIMEOUT_MS = 5000L
        private const val IPC_TIMEOUT_MS = 10000L
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.i("Connected to plugin sandbox service")
            sandboxService = IPluginSandbox.Stub.asInterface(service)
            isConnected.set(true)
            synchronized(connectionLock) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (connectionLock as Object).notifyAll()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("Disconnected from plugin sandbox service")
            sandboxService = null
            isConnected.set(false)
        }
        
        override fun onBindingDied(name: ComponentName) {
            Timber.e("Plugin sandbox service binding died")
            sandboxService = null
            isConnected.set(false)
        }
    }
    
    private val hardwareBridgeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.i("Connected to hardware bridge service")
            hardwareBridgeService = IHardwareBridge.Stub.asInterface(service)
            isHardwareBridgeConnected.set(true)
            synchronized(hardwareBridgeLock) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (hardwareBridgeLock as Object).notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("Disconnected from hardware bridge service")
            hardwareBridgeService = null
            isHardwareBridgeConnected.set(false)
        }

        override fun onBindingDied(name: ComponentName) {
            Timber.e("Hardware bridge service binding died")
            hardwareBridgeService = null
            isHardwareBridgeConnected.set(false)
        }
    }

    private val messagingBridgeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.i("Connected to messaging bridge service")
            messagingBridgeService = IPluginMessaging.Stub.asInterface(service)
            isMessagingBridgeConnected.set(true)
            synchronized(messagingBridgeLock) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (messagingBridgeLock as Object).notifyAll()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("Disconnected from messaging bridge service")
            messagingBridgeService = null
            isMessagingBridgeConnected.set(false)
        }

        override fun onBindingDied(name: ComponentName) {
            Timber.e("Messaging bridge service binding died")
            messagingBridgeService = null
            isMessagingBridgeConnected.set(false)
        }
    }
    
    /**
     * Connects to the sandbox service
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                return@withContext Result.success(Unit)
            }
            
            val intent = Intent(context, PluginSandboxService::class.java)
            val bindSuccess = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            
            if (!bindSuccess) {
                return@withContext Result.failure(Exception("Failed to bind to sandbox service"))
            }
            
            // Wait for connection with timeout
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                synchronized(connectionLock) {
                    while (!isConnected.get()) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (connectionLock as Object).wait(100)
                    }
                }
                true
            } ?: false
            
            if (!connected) {
                context.unbindService(serviceConnection)
                return@withContext Result.failure(Exception("Sandbox connection timeout"))
            }
            
            // Verify connection with ping (rate limit check)
            try {
                rateLimiter.checkRateLimit("ping")
                val pingSuccess = sandboxService?.ping() ?: false
                if (!pingSuccess) {
                    disconnect()
                    return@withContext Result.failure(Exception("Sandbox ping failed"))
                }
            } catch (e: RateLimitException) {
                Timber.w(e, "Rate limit exceeded for ping during connect")
                disconnect()
                return@withContext Result.failure(e)
            }

            // Set logging bridge (best-effort) for plugin debugging logs.
            try {
                val bridgeBinder = pluginLogBridge?.asBinder()
                if (bridgeBinder != null) {
                    sandboxService?.setLoggingBridge(bridgeBinder)
                    Timber.i("Logging bridge set in sandbox")
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to set logging bridge in sandbox")
            }
            
            // Connect bridges only when enabled in Services Dashboard
            if (serviceStateRepository == null || serviceStateRepository.isEnabled(ServiceIds.HARDWARE_BRIDGE)) {
                val bridgeConnected = connectHardwareBridge()
                if (!bridgeConnected) {
                    Timber.w("Hardware bridge connection failed - plugins will have limited hardware access")
                }
            }
            if (serviceStateRepository == null || serviceStateRepository.isEnabled(ServiceIds.PLUGIN_MESSAGING)) {
                val messagingConnected = connectMessagingBridge()
                if (!messagingConnected) {
                    Timber.w("Messaging bridge connection failed - plugins will have limited messaging capabilities")
                }
            }
            if (serviceStateRepository == null || serviceStateRepository.isEnabled(ServiceIds.FILE_SYSTEM_BRIDGE)) {
                val fileSystemConnected = connectFileSystemBridge()
                if (!fileSystemConnected) {
                    Timber.w("File system bridge connection failed - file access will be unavailable")
                }
            }

            Timber.i("Successfully connected to plugin sandbox")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to sandbox")
            Result.failure(e)
        }
    }
    
    /**
     * Connects to Hardware Bridge Service
     */
    suspend fun connectHardwareBridge(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isHardwareBridgeConnected.get()) {
                return@withContext true
            }
            
            val intent = Intent(context, HardwareBridgeService::class.java)
            val bindSuccess = context.bindService(
                intent,
                hardwareBridgeConnection,
                Context.BIND_AUTO_CREATE
            )
            
            if (!bindSuccess) {
                Timber.w("Failed to bind to hardware bridge service")
                return@withContext false
            }
            
            // Wait for connection with timeout
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                synchronized(hardwareBridgeLock) {
                    while (!isHardwareBridgeConnected.get()) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (hardwareBridgeLock as Object).wait(100)
                    }
                }
                true
            } ?: false
            
            if (!connected) {
                context.unbindService(hardwareBridgeConnection)
                Timber.w("Hardware bridge connection timeout")
                return@withContext false
            }
            
            // Pass hardware bridge to sandbox
            try {
                val bridgeBinder = hardwareBridgeService?.asBinder()
                if (bridgeBinder == null) {
                    Timber.e("Hardware bridge service binder is null")
                    return@withContext false
                }
                sandboxService?.setHardwareBridge(bridgeBinder)
                Timber.i("Hardware bridge set in sandbox")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set hardware bridge in sandbox")
                return@withContext false
            }
            
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect hardware bridge")
            false
        }
    }
    
    /**
     * Connects to Messaging Bridge Service
     */
    suspend fun connectMessagingBridge(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isMessagingBridgeConnected.get()) {
                return@withContext true
            }

            val intent = Intent(context, PluginMessagingService::class.java)
            val bindSuccess = context.bindService(
                intent,
                messagingBridgeConnection,
                Context.BIND_AUTO_CREATE
            )

            if (!bindSuccess) {
                Timber.w("Failed to bind to messaging bridge service")
                return@withContext false
            }

            // Wait for connection with timeout
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                synchronized(messagingBridgeLock) {
                    while (!isMessagingBridgeConnected.get()) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (messagingBridgeLock as Object).wait(100)
                    }
                }
                true
            } ?: false

            if (!connected) {
                context.unbindService(messagingBridgeConnection)
                Timber.w("Messaging bridge connection timeout")
                return@withContext false
            }

            // Pass messaging bridge to sandbox
            try {
                val bridgeBinder = messagingBridgeService?.asBinder()
                if (bridgeBinder == null) {
                    Timber.e("Messaging bridge service binder is null")
                    return@withContext false
                }
                sandboxService?.setMessagingBridge(bridgeBinder)
                Timber.i("Messaging bridge set in sandbox")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set messaging bridge in sandbox")
                return@withContext false
            }

            true

        } catch (e: Exception) {
            Timber.e(e, "Failed to connect messaging bridge")
            false
        }
    }

    /**
     * Connects the file system bridge to the sandbox
     * @return True if connection was successful
     */
    /**
     * Gets the hardware bridge interface for direct method calls
     */
    fun getHardwareBridge(): IHardwareBridge? {
        return if (isHardwareBridgeConnected.get()) {
            hardwareBridgeService
        } else {
            null
        }
    }

    /**
     * Gets the sandbox service interface for direct method calls (e.g., UI rendering)
     */
    fun getSandboxService(): IPluginSandbox? {
        return if (isConnected.get()) {
            sandboxService
        } else {
            null
        }
    }

    /**
     * Gets the sandbox process PID for resource monitoring
     */
    fun getSandboxPid(): Int {
        return try {
            // Rate limit check (synchronous method, so we check but don't throw in non-suspend context)
            try {
                rateLimiter.checkRateLimit("getSandboxPid")
            } catch (e: RateLimitException) {
                Timber.w(e, "Rate limit exceeded for getSandboxPid")
                return 0
            }
            
            if (isConnected.get()) {
                sandboxService?.getSandboxPid() ?: 0
            } else {
                0
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get sandbox PID")
            0
        }
    }
    
    suspend fun connectFileSystemBridge(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isFileSystemBridgeConnected.get()) {
                return@withContext true
            }
            
            // Create reusable ServiceConnection
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val fsBridge = IFileSystemBridge.Stub.asInterface(service)
                    
                    // Pass file system bridge to sandbox
                    try {
                        sandboxService?.setFileSystemBridge(fsBridge.asBinder())
                        isFileSystemBridgeConnected.set(true)
                        Timber.i("File system bridge set in sandbox")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to set file system bridge in sandbox")
                        isFileSystemBridgeConnected.set(false)
                    }
                }
                
                override fun onServiceDisconnected(name: ComponentName?) {
                    isFileSystemBridgeConnected.set(false)
                    Timber.i("File system bridge disconnected")
                }
            }
            
            fileSystemBridgeConnection = connection
            
            // Bind to file system bridge service
            val fsIntent = Intent(context, FileSystemBridgeService::class.java)
            val fsBound = context.bindService(
                fsIntent,
                connection,
                Context.BIND_AUTO_CREATE
            )
            
            if (!fsBound) {
                Timber.w("Failed to bind to file system bridge service")
                fileSystemBridgeConnection = null
                return@withContext false
            }
            
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect file system bridge")
            fileSystemBridgeConnection = null
            false
        }
    }
    
    /**
     * Sets the UI Controller for Three-Process Architecture.
     *
     * Connects the Sandbox Process to the UI Process for state-based rendering.
     * The UI Controller runs in the UI Process and receives UI state updates from sandbox.
     *
     * @param uiController IPluginUIController from UI Process
     * @return True if successfully set
     */
    suspend fun setUIController(uiController: com.ble1st.connectias.plugin.ui.IPluginUIController): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected.get()) {
                Timber.w("[MAIN] Cannot set UI controller - sandbox not connected")
                return@withContext false
            }

            Timber.d("[MAIN] Setting UI controller in sandbox")

            rateLimiter.checkRateLimit("setUIController")
            sandboxService?.setUIController(uiController.asBinder())
            Timber.i("[MAIN] UI controller set in sandbox process")

            true
        } catch (e: RateLimitException) {
            Timber.w(e, "[MAIN] Rate limit exceeded for setUIController")
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                message = "setUIController rate limited"
            )
            false
        } catch (e: Exception) {
            Timber.e(e, "[MAIN] Failed to set UI controller in sandbox")
            false
        }
    }

    /**
     * Disconnects from the sandbox service
     */
    fun disconnect() {
        try {
            if (isConnected.get()) {
                sandboxService?.shutdown()
                context.unbindService(serviceConnection)
                sandboxService = null
                isConnected.set(false)
                Timber.i("Disconnected from plugin sandbox")
            }
            
            if (isHardwareBridgeConnected.get()) {
                context.unbindService(hardwareBridgeConnection)
                hardwareBridgeService = null
                isHardwareBridgeConnected.set(false)
                Timber.i("Disconnected from hardware bridge")
            }
            
            if (isFileSystemBridgeConnected.get() && fileSystemBridgeConnection != null) {
                context.unbindService(fileSystemBridgeConnection!!)
                fileSystemBridgeConnection = null
                isFileSystemBridgeConnected.set(false)
                Timber.i("Disconnected from file system bridge")
            }

            if (isMessagingBridgeConnected.get()) {
                context.unbindService(messagingBridgeConnection)
                messagingBridgeService = null
                isMessagingBridgeConnected.set(false)
                Timber.i("Disconnected from messaging bridge")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from sandbox")
        }
    }

    /**
     * Disconnects only the Hardware Bridge (unbind). Sandbox and other bridges remain connected.
     */
    fun disconnectHardwareBridge() {
        try {
            if (isHardwareBridgeConnected.get()) {
                context.unbindService(hardwareBridgeConnection)
                hardwareBridgeService = null
                isHardwareBridgeConnected.set(false)
                Timber.i("Disconnected from hardware bridge")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting hardware bridge")
        }
    }

    /**
     * Disconnects only the Messaging Bridge (unbind).
     */
    fun disconnectMessagingBridge() {
        try {
            if (isMessagingBridgeConnected.get()) {
                context.unbindService(messagingBridgeConnection)
                messagingBridgeService = null
                isMessagingBridgeConnected.set(false)
                Timber.i("Disconnected from messaging bridge")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting messaging bridge")
        }
    }

    /**
     * Disconnects only the File System Bridge (unbind).
     */
    fun disconnectFileSystemBridge() {
        try {
            if (isFileSystemBridgeConnected.get() && fileSystemBridgeConnection != null) {
                context.unbindService(fileSystemBridgeConnection!!)
                fileSystemBridgeConnection = null
                isFileSystemBridgeConnected.set(false)
                Timber.i("Disconnected from file system bridge")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting file system bridge")
        }
    }
    
    /**
     * Loads a plugin in the sandbox (legacy path-based)
     */
    suspend fun loadPlugin(pluginPath: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("loadPlugin")
            
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.loadPlugin(pluginPath)
            } ?: return@withContext Result.failure(Exception("IPC timeout during loadPlugin"))
            
            if (result.success && result.metadata != null) {
                Result.success(result.metadata.toPluginMetadata())
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for loadPlugin")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = null,
                message = "Rate limit exceeded for method: loadPlugin",
                details = mapOf(
                    "method" to "loadPlugin",
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Loads a plugin in the sandbox using ParcelFileDescriptor
     * This is the preferred method for isolated processes
     */
    suspend fun loadPluginFromFile(pluginFile: java.io.File, pluginId: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            val isDeclarative = pluginFile.extension.equals("cplug", ignoreCase = true)

            // Rate limit check
            rateLimiter.checkRateLimit(
                if (isDeclarative) "loadDeclarativePluginFromDescriptor" else "loadPluginFromDescriptor",
                pluginId
            )
            
            ensureConnected()
            
            // Open file as ParcelFileDescriptor
            val pfd = ParcelFileDescriptor.open(
                pluginFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            // Create a session token in MAIN process to authenticate subsequent IPC calls (e.g. FS bridge)
            val sessionToken = PluginIdentitySession.createSessionToken(pluginId)
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                if (isDeclarative) {
                    sandboxService?.loadDeclarativePluginFromDescriptor(pfd, pluginId, sessionToken)
                } else {
                    sandboxService?.loadPluginFromDescriptor(pfd, pluginId, sessionToken)
                }
            }
            
            pfd.close()
            
            if (result == null) {
                PluginIdentitySession.unregisterPluginSession(pluginId)
                return@withContext Result.failure(
                    Exception(
                        if (isDeclarative) "IPC timeout during loadDeclarativePluginFromDescriptor"
                        else "IPC timeout during loadPluginFromDescriptor"
                    )
                )
            }
            
            if (result.success && result.metadata != null) {
                Result.success(result.metadata.toPluginMetadata())
            } else {
                // Cleanup token on load failure
                PluginIdentitySession.unregisterPluginSession(pluginId)
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for loadPluginFromDescriptor/loadDeclarativePluginFromDescriptor")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = pluginId,
                message = "Rate limit exceeded for method: loadPluginFromDescriptor/loadDeclarativePluginFromDescriptor",
                details = mapOf(
                    "method" to "loadPluginFromDescriptor/loadDeclarativePluginFromDescriptor",
                    "pluginId" to pluginId,
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin from file via IPC")
            // Best-effort cleanup in case token was created earlier
            PluginIdentitySession.unregisterPluginSession(pluginId)
            Result.failure(e)
        }
    }
    
    /**
     * Enables a plugin in the sandbox
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("enablePlugin", pluginId)
            
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.enablePlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during enablePlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for enablePlugin")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = pluginId,
                message = "Rate limit exceeded for method: enablePlugin",
                details = mapOf(
                    "method" to "enablePlugin",
                    "pluginId" to pluginId,
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Disables a plugin in the sandbox
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("disablePlugin", pluginId)
            
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.disablePlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during disablePlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for disablePlugin")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = pluginId,
                message = "Rate limit exceeded for method: disablePlugin",
                details = mapOf(
                    "method" to "disablePlugin",
                    "pluginId" to pluginId,
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Unloads a plugin from the sandbox
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("unloadPlugin", pluginId)
            
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.unloadPlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during unloadPlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for unloadPlugin")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = pluginId,
                message = "Rate limit exceeded for method: unloadPlugin",
                details = mapOf(
                    "method" to "unloadPlugin",
                    "pluginId" to pluginId,
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unload plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Gets list of loaded plugins
     */
    suspend fun getLoadedPlugins(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("getLoadedPlugins")
            
            ensureConnected()
            
            val plugins = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.loadedPlugins
            } ?: return@withContext Result.failure(Exception("IPC timeout during getLoadedPlugins"))
            
            Result.success(plugins)
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for getLoadedPlugins")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = null,
                message = "Rate limit exceeded for method: getLoadedPlugins",
                details = mapOf(
                    "method" to "getLoadedPlugins",
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get loaded plugins via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Gets plugin metadata
     */
    suspend fun getPluginMetadata(pluginId: String): Result<PluginMetadata?> = withContext(Dispatchers.IO) {
        try {
            // Rate limit check
            rateLimiter.checkRateLimit("getPluginMetadata", pluginId)
            
            ensureConnected()
            
            val metadata = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.getPluginMetadata(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during getPluginMetadata"))
            
            Result.success(metadata.toPluginMetadata())
            
        } catch (e: RateLimitException) {
            Timber.w(e, "Rate limit exceeded for getPluginMetadata")
            // Log to security audit
            auditManager?.logSecurityEvent(
                eventType = SecurityAuditManager.SecurityEventType.API_RATE_LIMITING,
                severity = SecurityAuditManager.SecuritySeverity.MEDIUM,
                source = "PluginSandboxProxy",
                pluginId = pluginId,
                message = "Rate limit exceeded for method: getPluginMetadata",
                details = mapOf(
                    "method" to "getPluginMetadata",
                    "pluginId" to pluginId,
                    "retryAfterMs" to e.retryAfterMs.toString()
                ),
                exception = e
            )
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get plugin metadata via IPC")
            Result.failure(e)
        }
    }
    
    private fun ensureConnected() {
        if (!isConnected.get()) {
            throw IllegalStateException("Not connected to sandbox service")
        }
    }
}
