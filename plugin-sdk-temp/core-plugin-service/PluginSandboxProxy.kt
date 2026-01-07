package com.ble1st.connectias.core.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.ble1st.connectias.plugin.IPluginSandbox
import com.ble1st.connectias.plugin.PluginMetadata
import com.ble1st.connectias.plugin.PluginResultParcel
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Proxy for communicating with the plugin sandbox service via IPC
 */
class PluginSandboxProxy(
    private val context: Context
) {
    
    private var sandboxService: IPluginSandbox? = null
    private val isConnected = AtomicBoolean(false)
    private val connectionLock = Any()
    private var bindJob: Job? = null
    
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
                connectionLock.notifyAll()
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
                        connectionLock.wait(100)
                    }
                }
                true
            } ?: false
            
            if (!connected) {
                context.unbindService(serviceConnection)
                return@withContext Result.failure(Exception("Sandbox connection timeout"))
            }
            
            // Verify connection with ping
            val pingSuccess = sandboxService?.ping() ?: false
            if (!pingSuccess) {
                disconnect()
                return@withContext Result.failure(Exception("Sandbox ping failed"))
            }
            
            Timber.i("Successfully connected to plugin sandbox")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to sandbox")
            Result.failure(e)
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
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting from sandbox")
        }
    }
    
    /**
     * Loads a plugin in the sandbox
     */
    suspend fun loadPlugin(pluginPath: String): Result<PluginMetadata> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.loadPlugin(pluginPath)
            } ?: return@withContext Result.failure(Exception("IPC timeout during loadPlugin"))
            
            if (result.success && result.metadata != null) {
                Result.success(result.metadata.toPluginMetadata())
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Enables a plugin in the sandbox
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.enablePlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during enablePlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
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
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.disablePlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during disablePlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
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
            ensureConnected()
            
            val result = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.unloadPlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during unloadPlugin"))
            
            if (result.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(result.errorMessage ?: "Unknown error"))
            }
            
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
            ensureConnected()
            
            val plugins = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.loadedPlugins
            } ?: return@withContext Result.failure(Exception("IPC timeout during getLoadedPlugins"))
            
            Result.success(plugins)
            
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
            ensureConnected()
            
            val metadata = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                sandboxService?.getPluginMetadata(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during getPluginMetadata"))
            
            Result.success(metadata?.toPluginMetadata())
            
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
