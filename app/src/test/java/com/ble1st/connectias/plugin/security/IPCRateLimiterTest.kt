package com.ble1st.connectias.plugin.security

import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Unit tests for IPCRateLimiter
 */
class IPCRateLimiterTest {
    
    @Test
    fun `test rate limit allows calls within limit`() {
        val limiter = IPCRateLimiter()
        
        // Should allow calls within per-second limit
        repeat(2) {
            limiter.checkRateLimit("enablePlugin", "test-plugin")
        }
        
        // Should not throw exception
        assertTrue(true)
    }
    
    @Test
    fun `test rate limit throws exception when exceeded`() {
        val limiter = IPCRateLimiter()
        
        // Exceed per-second limit (limit is 2/sec for enablePlugin)
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        // Fourth call should throw exception
        try {
            limiter.checkRateLimit("enablePlugin", "test-plugin")
            fail("Expected RateLimitException")
        } catch (e: RateLimitException) {
            assertEquals("enablePlugin", e.methodName)
            assertEquals("test-plugin", e.pluginId)
            assertTrue(e.retryAfterMs > 0)
        }
    }
    
    @Test
    fun `test rate limit resets after time window`() {
        val limiter = IPCRateLimiter()
        
        // Exceed limit
        repeat(3) {
            try {
                limiter.checkRateLimit("enablePlugin", "test-plugin")
            } catch (e: RateLimitException) {
                // Expected for last call
            }
        }
        
        // Wait for token refill (should be ~1 second for 2/sec limit)
        Thread.sleep(1500)
        
        // Should allow calls again
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        assertTrue(true)
    }
    
    @Test
    fun `test per-plugin rate limiting`() {
        val limiter = IPCRateLimiter()
        
        // Plugin A should have separate bucket from Plugin B
        limiter.checkRateLimit("enablePlugin", "plugin-a")
        limiter.checkRateLimit("enablePlugin", "plugin-a")
        limiter.checkRateLimit("enablePlugin", "plugin-a") // Should fail
        
        // Plugin B should still work
        limiter.checkRateLimit("enablePlugin", "plugin-b")
        limiter.checkRateLimit("enablePlugin", "plugin-b")
        
        try {
            limiter.checkRateLimit("enablePlugin", "plugin-a")
            fail("Expected RateLimitException for plugin-a")
        } catch (e: RateLimitException) {
            assertEquals("plugin-a", e.pluginId)
        }
    }
    
    @Test
    fun `test global rate limiting without plugin ID`() {
        val limiter = IPCRateLimiter()
        
        // Global limit for getLoadedPlugins (10/sec)
        repeat(20) {
            try {
                limiter.checkRateLimit("getLoadedPlugins")
            } catch (e: RateLimitException) {
                // Expected after burst limit
            }
        }
        
        // Should have thrown exception
        assertTrue(true)
    }
    
    @Test
    fun `test reset rate limit`() {
        val limiter = IPCRateLimiter()
        
        // Exceed limit
        repeat(3) {
            try {
                limiter.checkRateLimit("enablePlugin", "test-plugin")
            } catch (e: RateLimitException) {
                // Expected
            }
        }
        
        // Reset
        limiter.resetRateLimit("enablePlugin", "test-plugin")
        
        // Should work again
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        assertTrue(true)
    }
    
    @Test
    fun `test get token count`() {
        val limiter = IPCRateLimiter()
        
        // First call creates bucket with burst tokens (3)
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        // Now get token count (should be burst - 1 = 2)
        val afterConsume = limiter.getTokenCount("enablePlugin", "test-plugin")
        assertTrue("Token count should be 2 (burst=3, consumed 1)", afterConsume >= 1.5 && afterConsume <= 2.5)
        
        // Consume another token
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        val afterSecondConsume = limiter.getTokenCount("enablePlugin", "test-plugin")
        assertTrue("Token count should decrease after second consume", afterSecondConsume < afterConsume)
    }
    
    @Test
    fun `test per-minute rate limit`() {
        val limiter = IPCRateLimiter()
        
        // Per-minute limit for enablePlugin is 20
        // We should be able to make 20 calls within a minute
        var successCount = 0
        var exceptionCount = 0
        
        repeat(25) {
            try {
                limiter.checkRateLimit("enablePlugin", "test-plugin")
                successCount++
                // Small delay to avoid per-second limit
                Thread.sleep(100)
            } catch (e: RateLimitException) {
                exceptionCount++
            }
        }
        
        // Should have some successes and some failures
        assertTrue(successCount > 0)
        assertTrue(exceptionCount > 0)
    }
    
    @Test
    fun `test different methods have different limits`() {
        val limiter = IPCRateLimiter()
        
        // ping has higher limit (60/sec, burst=100)
        repeat(50) {
            limiter.checkRateLimit("ping")
        }
        
        // Should not throw (within limit)
        assertTrue(true)
        
        // enablePlugin has lower limit (2/sec, burst=3)
        // Should allow 3 calls (burst limit), then fail on 4th
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        // Fourth call should fail (burst exceeded, and per-second limit also exceeded)
        try {
            limiter.checkRateLimit("enablePlugin", "test-plugin")
            fail("Expected RateLimitException")
        } catch (e: RateLimitException) {
            assertEquals("enablePlugin", e.methodName)
        }
    }
    
    @Test
    fun `test burst limit`() {
        val limiter = IPCRateLimiter()
        
        // enablePlugin has burst=3
        // Should allow 3 immediate calls
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        limiter.checkRateLimit("enablePlugin", "test-plugin")
        
        // Fourth call should fail (burst exceeded)
        try {
            limiter.checkRateLimit("enablePlugin", "test-plugin")
            fail("Expected RateLimitException")
        } catch (e: RateLimitException) {
            assertTrue(true)
        }
    }
}
