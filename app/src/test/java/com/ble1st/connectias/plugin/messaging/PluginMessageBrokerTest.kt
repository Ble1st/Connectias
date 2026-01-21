package com.ble1st.connectias.plugin.messaging

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Unit tests for PluginMessageBroker
 */
class PluginMessageBrokerTest {
    
    @Test
    fun `test register and unregister plugin`() {
        val broker = PluginMessageBroker()
        
        // Register plugin
        val registered = broker.registerPlugin("test-plugin")
        assertTrue(registered)
        assertTrue(broker.isPluginRegistered("test-plugin"))
        
        // Try to register again (should fail)
        val registeredAgain = broker.registerPlugin("test-plugin")
        assertFalse(registeredAgain)
        
        // Unregister
        broker.unregisterPlugin("test-plugin")
        assertFalse(broker.isPluginRegistered("test-plugin"))
    }
    
    @Test
    fun `test send message to unregistered receiver fails`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        
        val message = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = UUID.randomUUID().toString()
        )
        
        val response = runBlocking {
            broker.sendMessage(message)
        }
        
        assertFalse(response.success)
        assertTrue(response.errorMessage?.contains("not found") == true)
    }
    
    @Test
    fun `test send and receive message`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        val message = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = UUID.randomUUID().toString()
        )
        
        // Send message in background
        val responseDeferred = kotlinx.coroutines.GlobalScope.async {
            broker.sendMessage(message)
        }
        
        // Receive message
        val receivedMessages = broker.receiveMessages("receiver")
        assertEquals(1, receivedMessages.size)
        assertEquals(message.requestId, receivedMessages[0].requestId)
        
        // Send response
        val response = MessageResponse.success(message.requestId, "Response".toByteArray())
        val responseSent = broker.sendResponse(response)
        assertTrue(responseSent)
        
        // Wait for response
        val finalResponse = runBlocking { responseDeferred.await() }
        assertTrue(finalResponse.success)
        assertEquals("Response", String(finalResponse.payload))
    }
    
    @Test
    fun `test message timeout`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        val message = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = UUID.randomUUID().toString()
        )
        
        // Send message but don't send response
        val response = runBlocking {
            broker.sendMessage(message)
        }
        
        // Should timeout and return error
        assertFalse(response.success)
        assertTrue(response.errorMessage?.contains("timeout") == true)
    }
    
    @Test
    fun `test payload size limit`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        // Create message with payload > 1MB
        val largePayload = ByteArray(2 * 1024 * 1024) // 2MB
        val message = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = largePayload,
            requestId = UUID.randomUUID().toString()
        )
        
        val response = runBlocking {
            broker.sendMessage(message)
        }
        
        assertFalse(response.success)
        assertTrue(response.errorMessage?.contains("too large") == true)
    }
    
    @Test
    fun `test rate limiting`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        // Send 101 messages rapidly (limit is 100/sec)
        var successCount = 0
        var failureCount = 0
        
        repeat(105) {
            val message = PluginMessage(
                senderId = "sender",
                receiverId = "receiver",
                messageType = "TEST",
                payload = "Test".toByteArray(),
                requestId = UUID.randomUUID().toString()
            )
            
            val response = runBlocking {
                broker.sendMessage(message)
            }
            
            if (response.success) {
                successCount++
            } else {
                failureCount++
            }
        }
        
        // Should have some failures due to rate limiting
        assertTrue(failureCount > 0)
    }
    
    @Test
    fun `test multiple messages in queue`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        // Send multiple messages
        val messages = (1..5).map { i ->
            PluginMessage(
                senderId = "sender",
                receiverId = "receiver",
                messageType = "TEST",
                payload = "Message $i".toByteArray(),
                requestId = UUID.randomUUID().toString()
            )
        }
        
        messages.forEach { message ->
            kotlinx.coroutines.GlobalScope.launch {
                broker.sendMessage(message)
            }
        }
        
        // Wait a bit for messages to be queued
        Thread.sleep(100)
        
        // Receive all messages
        val received = broker.receiveMessages("receiver")
        assertEquals(5, received.size)
    }
    
    @Test
    fun `test clear broker`() {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("plugin1")
        broker.registerPlugin("plugin2")
        
        assertTrue(broker.isPluginRegistered("plugin1"))
        assertTrue(broker.isPluginRegistered("plugin2"))
        
        broker.clear()
        
        assertFalse(broker.isPluginRegistered("plugin1"))
        assertFalse(broker.isPluginRegistered("plugin2"))
    }
}
