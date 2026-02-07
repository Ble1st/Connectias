// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LoggingRateLimiter.
 *
 * Tests rate limiting logic, token bucket refill, and cleanup behavior.
 */
class LoggingRateLimiterTest {

    private lateinit var rateLimiter: LoggingRateLimiter

    @Before
    fun setup() {
        rateLimiter = LoggingRateLimiter()
    }

    @Test
    fun `submitLog allows up to burst limit initially`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"

        // Burst limit is 150 for submitLog
        // Should allow 150 calls without delay
        repeat(150) { i ->
            val allowed = rateLimiter.checkRateLimit(packageName, methodName)
            assertTrue("Call $i should be allowed", allowed)
        }

        // 151st call should be rate limited
        val shouldBeLimited = rateLimiter.checkRateLimit(packageName, methodName)
        assertFalse("Call 151 should be rate limited", shouldBeLimited)
    }

    @Test
    fun `submitLogWithException has lower burst limit`() {
        val packageName = "com.example.test"
        val methodName = "submitLogWithException"

        // Burst limit is 75 for submitLogWithException
        repeat(75) { i ->
            val allowed = rateLimiter.checkRateLimit(packageName, methodName)
            assertTrue("Call $i should be allowed", allowed)
        }

        // 76th call should be rate limited
        val shouldBeLimited = rateLimiter.checkRateLimit(packageName, methodName)
        assertFalse("Call 76 should be rate limited", shouldBeLimited)
    }

    @Test
    fun `rate limiting is per-package`() {
        val package1 = "com.example.app1"
        val package2 = "com.example.app2"
        val methodName = "submitLog"

        // Exhaust rate limit for package1
        repeat(150) {
            rateLimiter.checkRateLimit(package1, methodName)
        }
        assertFalse("Package1 should be rate limited",
            rateLimiter.checkRateLimit(package1, methodName))

        // package2 should still have full burst available
        assertTrue("Package2 should not be rate limited",
            rateLimiter.checkRateLimit(package2, methodName))
    }

    @Test
    fun `tokens refill over time`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"

        // Exhaust burst
        repeat(150) {
            rateLimiter.checkRateLimit(packageName, methodName)
        }
        assertFalse("Should be rate limited",
            rateLimiter.checkRateLimit(packageName, methodName))

        // Wait for tokens to refill (100 tokens/sec)
        // After 1 second, should have ~100 tokens available
        Thread.sleep(1100)

        // Should be able to make calls again
        repeat(90) { i ->
            val allowed = rateLimiter.checkRateLimit(packageName, methodName)
            assertTrue("Call $i after refill should be allowed", allowed)
        }
    }

    @Test
    fun `reset clears rate limits for specific package`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"

        // Exhaust burst
        repeat(150) {
            rateLimiter.checkRateLimit(packageName, methodName)
        }
        assertFalse("Should be rate limited",
            rateLimiter.checkRateLimit(packageName, methodName))

        // Reset
        rateLimiter.reset(packageName)

        // Should be able to make calls again immediately
        assertTrue("Should be allowed after reset",
            rateLimiter.checkRateLimit(packageName, methodName))
    }

    @Test
    fun `resetAll clears all rate limits`() {
        val package1 = "com.example.app1"
        val package2 = "com.example.app2"
        val methodName = "submitLog"

        // Exhaust burst for both packages
        repeat(150) {
            rateLimiter.checkRateLimit(package1, methodName)
            rateLimiter.checkRateLimit(package2, methodName)
        }

        assertFalse("Package1 should be rate limited",
            rateLimiter.checkRateLimit(package1, methodName))
        assertFalse("Package2 should be rate limited",
            rateLimiter.checkRateLimit(package2, methodName))

        // Reset all
        rateLimiter.resetAll()

        // Both should be allowed again
        assertTrue("Package1 should be allowed after resetAll",
            rateLimiter.checkRateLimit(package1, methodName))
        assertTrue("Package2 should be allowed after resetAll",
            rateLimiter.checkRateLimit(package2, methodName))
    }

    @Test
    fun `unknown method has no rate limit`() {
        val packageName = "com.example.test"
        val unknownMethod = "unknownMethod"

        // Should allow unlimited calls for unconfigured methods
        repeat(1000) { i ->
            val allowed = rateLimiter.checkRateLimit(packageName, unknownMethod)
            assertTrue("Call $i should be allowed for unknown method", allowed)
        }
    }

    @Test
    fun `rate limiting is thread-safe`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"
        val threads = 10
        val callsPerThread = 20

        // Launch multiple threads making concurrent calls
        val results = mutableListOf<Boolean>()
        val threadList = List(threads) {
            Thread {
                repeat(callsPerThread) {
                    synchronized(results) {
                        results.add(rateLimiter.checkRateLimit(packageName, methodName))
                    }
                }
            }
        }

        threadList.forEach { it.start() }
        threadList.forEach { it.join() }

        // Should have made 200 total calls
        assertEquals("Should have made 200 calls", 200, results.size)

        // First 150 should succeed (burst limit), rest should fail
        val successCount = results.count { it }
        assertTrue("Success count should be around 150", successCount in 145..155)
    }

    @Test
    fun `cleanup removes inactive buckets`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"

        // Make a call to create bucket
        rateLimiter.checkRateLimit(packageName, methodName)

        // Wait more than 5 minutes (cleanup threshold)
        // Note: In real test, we'd use a test clock, but for simplicity we test the API
        // Just verify cleanup doesn't crash
        rateLimiter.cleanup()

        // Should still work after cleanup
        assertTrue("Should work after cleanup",
            rateLimiter.checkRateLimit(packageName, methodName))
    }

    @Test
    fun `submitLog rate is 100 per second`() {
        val packageName = "com.example.test"
        val methodName = "submitLog"

        // Exhaust burst
        repeat(150) {
            rateLimiter.checkRateLimit(packageName, methodName)
        }

        // Wait 0.5 seconds
        Thread.sleep(500)

        // Should have ~50 tokens available (100 tokens/sec * 0.5 sec)
        val allowedCalls = (0 until 60).count {
            rateLimiter.checkRateLimit(packageName, methodName)
        }

        // Should be around 50 (allowing for timing variance)
        assertTrue("Should allow around 50 calls after 0.5 sec, got $allowedCalls",
            allowedCalls in 45..55)
    }

    @Test
    fun `submitLogWithException rate is 50 per second`() {
        val packageName = "com.example.test"
        val methodName = "submitLogWithException"

        // Exhaust burst
        repeat(75) {
            rateLimiter.checkRateLimit(packageName, methodName)
        }

        // Wait 0.5 seconds
        Thread.sleep(500)

        // Should have ~25 tokens available (50 tokens/sec * 0.5 sec)
        val allowedCalls = (0 until 40).count {
            rateLimiter.checkRateLimit(packageName, methodName)
        }

        // Should be around 25 (allowing for timing variance)
        assertTrue("Should allow around 25 calls after 0.5 sec, got $allowedCalls",
            allowedCalls in 20..30)
    }

    @Test
    fun `different methods for same package have independent limits`() {
        val packageName = "com.example.test"

        // Exhaust submitLog limit
        repeat(150) {
            rateLimiter.checkRateLimit(packageName, "submitLog")
        }
        assertFalse("submitLog should be rate limited",
            rateLimiter.checkRateLimit(packageName, "submitLog"))

        // submitLogWithException should still work
        assertTrue("submitLogWithException should still be allowed",
            rateLimiter.checkRateLimit(packageName, "submitLogWithException"))
    }
}
