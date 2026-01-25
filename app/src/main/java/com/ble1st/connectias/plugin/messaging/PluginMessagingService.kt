package com.ble1st.connectias.plugin.messaging

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ble1st.connectias.plugin.messaging.IPluginMessaging
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Plugin Messaging Service running in Main Process
 * 
 * Provides message routing between plugins via IPC.
 * All plugin-to-plugin communication goes through this service for security and control.
 * 
 * ARCHITECTURE:
 * - Main Process: Runs this service with message broker
 * - Sandbox Process: Plugins call this service via IPC to send/receive messages
 * - Message Broker: Routes messages between plugins and handles request/response matching
 * 
 * SECURITY:
 * - Only registered plugins can send/receive messages
 * - Payload size limits (1MB max)
 * - Rate limiting (100 messages/sec per plugin)
 * - Message validation (sender/receiver must be registered)
 * 
 * @since 2.0.0
 */
class PluginMessagingService : Service() {
    
    private val messageBroker = PluginMessageBroker()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val binder = object : IPluginMessaging.Stub() {
        
        override fun sendMessage(message: PluginMessage): MessageResponse {
            return try {
                Timber.d("[MESSAGING SERVICE] Sending message from ${message.senderId} to ${message.receiverId}, type: ${message.messageType}")
                
                // Run in coroutine scope for async operations
                runBlocking {
                    messageBroker.sendMessage(message)
                }
            } catch (e: Exception) {
                Timber.e(e, "[MESSAGING SERVICE] Error sending message")
                MessageResponse.error(
                    message.requestId,
                    "Error sending message: ${e.message}"
                )
            }
        }
        
        override fun receiveMessages(pluginId: String): MutableList<PluginMessage> {
            return try {
                val messages = messageBroker.receiveMessages(pluginId)
                if (messages.isNotEmpty()) {
                    Timber.d("[MESSAGING SERVICE] Delivered ${messages.size} message(s) to plugin: $pluginId")
                }
                messages.toMutableList()
            } catch (e: Exception) {
                Timber.e(e, "[MESSAGING SERVICE] Error receiving messages for plugin: $pluginId")
                mutableListOf()
            }
        }
        
        override fun sendResponse(response: MessageResponse): Boolean {
            return try {
                Timber.d("[MESSAGING SERVICE] Sending response for request: ${response.requestId}")
                messageBroker.sendResponse(response)
            } catch (e: Exception) {
                Timber.e(e, "[MESSAGING SERVICE] Error sending response")
                false
            }
        }
        
        override fun registerPlugin(pluginId: String): Boolean {
            return try {
                Timber.i("[MESSAGING SERVICE] Registering plugin: $pluginId")
                messageBroker.registerPlugin(pluginId)
            } catch (e: Exception) {
                Timber.e(e, "[MESSAGING SERVICE] Error registering plugin: $pluginId")
                false
            }
        }
        
        override fun unregisterPlugin(pluginId: String) {
            try {
                Timber.i("[MESSAGING SERVICE] Unregistering plugin: $pluginId")
                messageBroker.unregisterPlugin(pluginId)
            } catch (e: Exception) {
                Timber.e(e, "[MESSAGING SERVICE] Error unregistering plugin: $pluginId")
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Timber.i("[MESSAGING SERVICE] PluginMessagingService created")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Timber.i("[MESSAGING SERVICE] Service bound")
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        messageBroker.clear()
        Timber.i("[MESSAGING SERVICE] Service destroyed")
    }
    
    /**
     * Get message broker instance (for direct access if needed)
     */
    fun getMessageBroker(): PluginMessageBroker {
        return messageBroker
    }
}
