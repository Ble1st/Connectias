package com.ble1st.connectias.plugin.messaging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.ble1st.connectias.plugin.messaging.IPluginMessaging
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Proxy for communicating with PluginMessagingService via IPC
 * 
 * Used by plugins in the sandbox process to send/receive messages.
 * All communication goes through this proxy to the Main Process message broker.
 */
class PluginMessagingProxy(
    private val context: Context
) {
    
    private var messagingService: IPluginMessaging? = null
    private val isConnected = AtomicBoolean(false)
    private val connectionLock = Any()
    
    companion object {
        private const val BIND_TIMEOUT_MS = 5000L
        private const val IPC_TIMEOUT_MS = 10000L
    }
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Timber.i("[MESSAGING PROXY] Connected to messaging service")
            messagingService = IPluginMessaging.Stub.asInterface(service)
            isConnected.set(true)
            synchronized(connectionLock) {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                (connectionLock as java.lang.Object).notifyAll()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName) {
            Timber.w("[MESSAGING PROXY] Disconnected from messaging service")
            messagingService = null
            isConnected.set(false)
        }
        
        override fun onBindingDied(name: ComponentName) {
            Timber.e("[MESSAGING PROXY] Messaging service binding died")
            messagingService = null
            isConnected.set(false)
        }
    }
    
    /**
     * Connect to the messaging service
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isConnected.get()) {
                return@withContext Result.success(Unit)
            }
            
            val intent = Intent(context, PluginMessagingService::class.java)
            val bindSuccess = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
            )
            
            if (!bindSuccess) {
                return@withContext Result.failure(Exception("Failed to bind to messaging service"))
            }
            
            // Wait for connection with timeout
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) {
                synchronized(connectionLock) {
                    while (!isConnected.get()) {
                        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                        (connectionLock as java.lang.Object).wait(100)
                    }
                }
                true
            } ?: false
            
            if (!connected) {
                context.unbindService(serviceConnection)
                return@withContext Result.failure(Exception("Messaging service connection timeout"))
            }
            
            Timber.i("[MESSAGING PROXY] Successfully connected to messaging service")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to connect to messaging service")
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the messaging service
     */
    fun disconnect() {
        try {
            if (isConnected.get()) {
                context.unbindService(serviceConnection)
                messagingService = null
                isConnected.set(false)
                Timber.i("[MESSAGING PROXY] Disconnected from messaging service")
            }
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Error disconnecting from messaging service")
        }
    }
    
    /**
     * Send a message to another plugin
     */
    suspend fun sendMessage(message: PluginMessage): Result<MessageResponse> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val response = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                messagingService?.sendMessage(message)
            } ?: return@withContext Result.failure(Exception("IPC timeout during sendMessage"))
            
            Result.success(response)
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to send message via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Receive pending messages for a plugin
     */
    suspend fun receiveMessages(pluginId: String): Result<List<PluginMessage>> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val messages = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                messagingService?.receiveMessages(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during receiveMessages"))
            
            Result.success(messages ?: emptyList())
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to receive messages via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Send a response to a previous message
     */
    suspend fun sendResponse(response: MessageResponse): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val success = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                messagingService?.sendResponse(response)
            } ?: return@withContext Result.failure(Exception("IPC timeout during sendResponse"))
            
            Result.success(success ?: false)
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to send response via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Register a plugin for message receiving
     */
    suspend fun registerPlugin(pluginId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            val success = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                messagingService?.registerPlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during registerPlugin"))
            
            Result.success(success ?: false)
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to register plugin via IPC")
            Result.failure(e)
        }
    }
    
    /**
     * Unregister a plugin
     */
    suspend fun unregisterPlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()
            
            withTimeoutOrNull(IPC_TIMEOUT_MS) {
                messagingService?.unregisterPlugin(pluginId)
            } ?: return@withContext Result.failure(Exception("IPC timeout during unregisterPlugin"))
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Timber.e(e, "[MESSAGING PROXY] Failed to unregister plugin via IPC")
            Result.failure(e)
        }
    }
    
    private fun ensureConnected() {
        if (!isConnected.get()) {
            throw IllegalStateException("Not connected to messaging service")
        }
    }
}
