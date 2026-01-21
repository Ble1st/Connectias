package com.ble1st.connectias.plugin.messaging

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PluginMessage and MessageResponse
 */
class PluginMessageTest {
    
    @Test
    fun `test plugin message creation`() {
        val message = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = "req-123"
        )
        
        assertEquals("sender", message.senderId)
        assertEquals("receiver", message.receiverId)
        assertEquals("TEST", message.messageType)
        assertEquals("Hello", String(message.payload))
        assertEquals("req-123", message.requestId)
        assertTrue(message.timestamp > 0)
    }
    
    @Test
    fun `test message response success`() {
        val response = MessageResponse.success("req-123", "Data".toByteArray())
        
        assertEquals("req-123", response.requestId)
        assertTrue(response.success)
        assertEquals("Data", String(response.payload))
        assertNull(response.errorMessage)
    }
    
    @Test
    fun `test message response error`() {
        val response = MessageResponse.error("req-123", "Error occurred")
        
        assertEquals("req-123", response.requestId)
        assertFalse(response.success)
        assertEquals("Error occurred", response.errorMessage)
    }
    
    @Test
    fun `test message equality`() {
        val message1 = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = "req-123"
        )
        
        val message2 = PluginMessage(
            senderId = "sender",
            receiverId = "receiver",
            messageType = "TEST",
            payload = "Hello".toByteArray(),
            requestId = "req-123"
        )
        
        assertEquals(message1, message2)
        assertEquals(message1.hashCode(), message2.hashCode())
    }
    
    @Test
    fun `test response equality`() {
        val response1 = MessageResponse.success("req-123", "Data".toByteArray())
        val response2 = MessageResponse.success("req-123", "Data".toByteArray())
        
        assertEquals(response1, response2)
        assertEquals(response1.hashCode(), response2.hashCode())
    }
}
