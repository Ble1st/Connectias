package com.ble1st.connectias.plugin.messaging

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

        runBlocking {
            // Send message in background
            val responseDeferred = async {
                broker.sendMessage(message)
            }

            // Wait until the message is enqueued (avoid race with async scheduling)
            var receivedMessages = emptyList<PluginMessage>()
            for (i in 0 until 50) {
                receivedMessages = broker.receiveMessages("receiver")
                if (receivedMessages.isNotEmpty()) break
                delay(10)
            }

            assertEquals(1, receivedMessages.size)
            assertEquals(message.requestId, receivedMessages[0].requestId)

            // Send response
            val response = MessageResponse.success(message.requestId, "Response".toByteArray())
            val responseSent = broker.sendResponse(response)
            assertTrue(responseSent)

            // Wait for response
            val finalResponse = responseDeferred.await()
            assertTrue(finalResponse.success)
            assertEquals("Response", String(finalResponse.payload))
        }
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
    fun `test rate limiting`() = runBlocking {
        val broker = PluginMessageBroker()
        
        broker.registerPlugin("sender")
        broker.registerPlugin("receiver")
        
        // Send 20 messages rapidly (limit is 100/sec, but we test with fewer for speed)
        // To avoid waiting for 5-second timeouts, we send responses immediately in parallel
        val messages = (1..20).map { i ->
            PluginMessage(
                senderId = "sender",
                receiverId = "receiver",
                messageType = "TEST",
                payload = "Test $i".toByteArray(),
                requestId = UUID.randomUUID().toString()
            )
        }
        
        // Send all messages in parallel and process responses immediately
        val responseJobs = messages.map { message ->
            async {
                broker.sendMessage(message)
            }
        }
        
        // Process received messages and send responses immediately
        // This prevents timeout waits and allows rate limiting to be tested quickly
        val responseProcessor = launch {
            var processedCount = 0
            val processedRequestIds = mutableSetOf<String>()
            
            while (processedCount < messages.size) {
                val receivedMessages = broker.receiveMessages("receiver")
                receivedMessages.forEach { msg ->
                    // Only process each message once
                    if (!processedRequestIds.contains(msg.requestId)) {
                        val response = MessageResponse.success(msg.requestId, "OK".toByteArray())
                        broker.sendResponse(response)
                        processedRequestIds.add(msg.requestId)
                        processedCount++
                    }
                }
                if (processedCount < messages.size) {
                    delay(10) // Small delay to avoid busy-waiting
                }
            }
        }
        
        // Wait for all responses
        val responses = responseJobs.map { it.await() }
        
        // Cancel response processor if still running
        responseProcessor.cancel()
        
        val successCount = responses.count { it.success }
        val failureCount = responses.count { !it.success }
        
        // With 20 messages sent rapidly, all should succeed since limit is 100/sec
        // This test verifies that rate limiting doesn't block messages under the limit
        assertEquals("All 20 messages should succeed (under 100/sec limit). Got successCount=$successCount, failureCount=$failureCount", 
                     20, successCount)
        assertEquals("No failures expected for messages under rate limit", 0, failureCount)
    }
    
    @Test
    fun `test multiple messages in queue`() = runBlocking {
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

        // Send messages in parallel (they will be queued)
        val sendJobs = messages.map { message ->
            async {
                broker.sendMessage(message)
            }
        }

        // Wait a bit for messages to be queued
        delay(50)

        // Receive all messages from queue
        val received = broker.receiveMessages("receiver")
        assertEquals("Expected 5 messages in queue, but got ${received.size}", 5, received.size)

        // Send responses to prevent timeout waits
        received.forEach { msg ->
            val response = MessageResponse.success(msg.requestId, "OK".toByteArray())
            broker.sendResponse(response)
        }

        // Wait for all send operations to complete
        sendJobs.forEach { it.await() }
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
