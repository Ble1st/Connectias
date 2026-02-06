package com.ble1st.connectias.plugin.messaging

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Message broker for routing messages between plugins
 * 
 * Manages message queues per plugin and handles request/response matching.
 * All operations are thread-safe and designed for concurrent access.
 */
class PluginMessageBroker {
    
    /**
     * Message queues per plugin ID
     */
    private val messageQueues = ConcurrentHashMap<String, LinkedBlockingQueue<PluginMessage>>()
    
    /**
     * Pending requests waiting for responses
     * Key: requestId, Value: CompletableDeferred for response
     */
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<MessageResponse>>()
    
    /**
     * Registered plugins (plugins that can receive messages)
     */
    private val registeredPlugins = ConcurrentHashMap<String, Boolean>()
    
    /**
     * Mutex for thread-safe operations
     */
    private val mutex = Mutex()
    
    /**
     * Default timeout for request/response (5 seconds)
     */
    private val defaultTimeoutMs = 5000L
    
    /**
     * Maximum payload size (1MB)
     */
    private val maxPayloadSize = 1024 * 1024
    
    /**
     * Rate limiter: max 100 messages/sec per plugin
     */
    private val messageCounts = ConcurrentHashMap<String, MutableList<Long>>()
    private val rateLimitPerSecond = 100
    
    /**
     * Send a message to another plugin and wait for response
     * 
     * @param message Message to send
     * @return Response from receiver plugin, or error response
     */
    suspend fun sendMessage(message: PluginMessage): MessageResponse = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Validate payload size
            if (message.payload.size > maxPayloadSize) {
                Timber.e("Message payload too large: ${message.payload.size} bytes (max: $maxPayloadSize)")
                return@withContext MessageResponse.error(
                    message.requestId,
                    "Message payload too large: ${message.payload.size} bytes"
                )
            }
            
            // Check rate limit
            if (!checkRateLimit(message.senderId)) {
                Timber.w("Rate limit exceeded for plugin: ${message.senderId}")
                return@withContext MessageResponse.error(
                    message.requestId,
                    "Rate limit exceeded: max $rateLimitPerSecond messages/sec"
                )
            }
            
            // Check if receiver is registered
            if (!registeredPlugins.containsKey(message.receiverId)) {
                Timber.w("Receiver plugin not found: ${message.receiverId}")
                return@withContext MessageResponse.error(
                    message.requestId,
                    "Receiver plugin not found: ${message.receiverId}"
                )
            }
            
            // Check if sender is registered (security: only registered plugins can send)
            if (!registeredPlugins.containsKey(message.senderId)) {
                Timber.w("Sender plugin not registered: ${message.senderId}")
                return@withContext MessageResponse.error(
                    message.requestId,
                    "Sender plugin not registered: ${message.senderId}"
                )
            }
            
            // Register pending request BEFORE adding to queue to prevent race condition
            // If receiver responds very quickly, we need to be ready to receive it
            val deferred = CompletableDeferred<MessageResponse>()
            pendingRequests[message.requestId] = deferred
            
            // Add to receiver's queue
            val queue = messageQueues.getOrPut(message.receiverId) {
                LinkedBlockingQueue()
            }
            
            val offered = queue.offer(message)
            if (!offered) {
                // Cleanup pending request if queue is full
                pendingRequests.remove(message.requestId)
                Timber.w("Message queue full for plugin: ${message.receiverId}")
                return@withContext MessageResponse.error(
                    message.requestId,
                    "Message queue full for receiver: ${message.receiverId}"
                )
            }
            
            Timber.d("Message sent from ${message.senderId} to ${message.receiverId}, type: ${message.messageType}")
        }
        
        // Wait for response (with timeout)
        
        try {
            val response = withTimeoutOrNull(defaultTimeoutMs) {
                deferred.await()
            } ?: MessageResponse.error(message.requestId, "Request timeout after ${defaultTimeoutMs}ms")
            
            pendingRequests.remove(message.requestId)
            return@withContext response
        } catch (e: Exception) {
            pendingRequests.remove(message.requestId)
            Timber.e(e, "Error waiting for response to message: ${message.requestId}")
            return@withContext MessageResponse.error(message.requestId, "Error: ${e.message}")
        }
    }
    
    /**
     * Receive pending messages for a plugin
     * 
     * @param pluginId Plugin identifier
     * @return List of pending messages (drained from queue)
     */
    fun receiveMessages(pluginId: String): List<PluginMessage> {
        val queue = messageQueues[pluginId] ?: return emptyList()
        
        val messages = mutableListOf<PluginMessage>()
        queue.drainTo(messages)
        
        if (messages.isNotEmpty()) {
            Timber.d("Plugin $pluginId received ${messages.size} messages")
        }
        
        return messages
    }
    
    /**
     * Send a response to a previous message
     * 
     * @param response Response message
     * @return True if response was delivered to waiting request
     */
    fun sendResponse(response: MessageResponse): Boolean {
        val deferred = pendingRequests.remove(response.requestId)
        
        if (deferred != null) {
            if (deferred.isCompleted) {
                Timber.w("Response for request ${response.requestId} already completed")
                return false
            }
            
            deferred.complete(response)
            Timber.d("Response sent for request: ${response.requestId}, success: ${response.success}")
            return true
        } else {
            Timber.w("No pending request found for response: ${response.requestId}")
            return false
        }
    }
    
    /**
     * Register a plugin for message receiving
     * 
     * @param pluginId Plugin identifier
     * @return True if registration successful
     */
    fun registerPlugin(pluginId: String): Boolean {
        synchronized(registeredPlugins) {
            if (registeredPlugins.containsKey(pluginId)) {
                Timber.w("Plugin already registered: $pluginId")
                return false
            }
            
            registeredPlugins[pluginId] = true
            messageQueues.getOrPut(pluginId) { LinkedBlockingQueue() }
            messageCounts[pluginId] = mutableListOf()
            
            Timber.i("Plugin registered for messaging: $pluginId")
            return true
        }
    }
    
    /**
     * Unregister a plugin
     * 
     * @param pluginId Plugin identifier
     */
    fun unregisterPlugin(pluginId: String) {
        synchronized(registeredPlugins) {
            registeredPlugins.remove(pluginId)
            messageQueues.remove(pluginId)
            messageCounts.remove(pluginId)
            
            // Cancel any pending requests from this plugin
            pendingRequests.keys.filter { requestId ->
                // We don't have sender info in requestId, so we cancel all if needed
                // In practice, we'd need to track sender per request
                false // For now, don't cancel - let them timeout
            }
            
            Timber.i("Plugin unregistered from messaging: $pluginId")
        }
    }
    
    /**
     * Check if a plugin is registered
     */
    fun isPluginRegistered(pluginId: String): Boolean {
        return registeredPlugins.containsKey(pluginId)
    }
    
    /**
     * Check rate limit for a plugin
     * 
     * @param pluginId Plugin identifier
     * @return True if within rate limit
     */
    private fun checkRateLimit(pluginId: String): Boolean {
        val now = System.currentTimeMillis()
        val counts = messageCounts.getOrPut(pluginId) { mutableListOf() }
        
        // Remove timestamps older than 1 second
        counts.removeAll { it < now - 1000 }
        
        // Check if within limit
        if (counts.size >= rateLimitPerSecond) {
            return false
        }
        
        // Add current timestamp
        counts.add(now)
        return true
    }
    
    /**
     * Get number of pending messages for a plugin
     */
    fun getPendingMessageCount(pluginId: String): Int {
        return messageQueues[pluginId]?.size ?: 0
    }
    
    /**
     * Get number of pending requests waiting for responses
     */
    fun getPendingRequestCount(): Int {
        return pendingRequests.size
    }
    
    /**
     * Clear all messages and requests (for testing/cleanup)
     */
    fun clear() {
        messageQueues.clear()
        pendingRequests.clear()
        registeredPlugins.clear()
        messageCounts.clear()
    }
}
