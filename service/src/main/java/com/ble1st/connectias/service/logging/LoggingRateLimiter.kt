// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Rate limiter specifically for LoggingService to prevent DoS attacks.
 *
 * Uses Token Bucket algorithm with per-package tracking.
 * Limits:
 * - submitLog: 100 calls/second per package
 * - submitLogWithException: 50 calls/second per package (more expensive)
 *
 * Architecture:
 * - Thread-safe: Uses ConcurrentHashMap with synchronized token consumption
 * - Per-package tracking: Each external app has its own rate limit
 * - Auto-cleanup: Old buckets are removed after 5 minutes of inactivity
 */
internal class LoggingRateLimiter {

    /**
     * Token bucket for rate limiting a single package.
     */
    private data class TokenBucket(
        var tokens: Double,
        var lastRefill: Long = System.currentTimeMillis(),
        val refillRate: Double,
        val maxTokens: Double
    ) {
        /**
         * Refill tokens based on elapsed time.
         */
        fun refill(now: Long) {
            val elapsedSeconds = (now - lastRefill) / 1000.0
            if (elapsedSeconds > 0) {
                tokens = min(maxTokens, tokens + (refillRate * elapsedSeconds))
                lastRefill = now
            }
        }

        /**
         * Try to consume tokens. Returns true if successful.
         */
        fun tryConsume(tokensToConsume: Double = 1.0): Boolean {
            val now = System.currentTimeMillis()
            refill(now)

            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume
                return true
            }
            return false
        }

        /**
         * Get milliseconds until next token is available.
         */
        fun getRetryAfterMs(): Long {
            val now = System.currentTimeMillis()
            refill(now)

            if (tokens >= 1.0) return 0L

            val tokensNeeded = 1.0 - tokens
            val secondsNeeded = tokensNeeded / refillRate
            return (secondsNeeded * 1000).toLong().coerceAtLeast(100L)
        }
    }

    /**
     * Buckets per package and method.
     * Key format: "packageName:methodName"
     */
    private val buckets = ConcurrentHashMap<String, TokenBucket>()

    /**
     * Rate limit configuration per method.
     */
    private val rateLimits = mapOf(
        "submitLog" to RateConfig(tokensPerSecond = 100.0, burst = 150.0),
        "submitLogWithException" to RateConfig(tokensPerSecond = 50.0, burst = 75.0)
    )

    private data class RateConfig(val tokensPerSecond: Double, val burst: Double)

    /**
     * Check if a call from a package is allowed.
     *
     * @param packageName Package making the call
     * @param methodName Method being called (submitLog or submitLogWithException)
     * @return true if allowed, false if rate limit exceeded
     */
    @Synchronized
    fun checkRateLimit(packageName: String, methodName: String): Boolean {
        val config = rateLimits[methodName]
            ?: return true // No limit configured = allow

        val key = "$packageName:$methodName"

        val bucket = buckets.getOrPut(key) {
            TokenBucket(
                tokens = config.burst, // Start with full burst
                refillRate = config.tokensPerSecond,
                maxTokens = config.burst
            )
        }

        val allowed = bucket.tryConsume(1.0)

        if (!allowed) {
            val retryAfter = bucket.getRetryAfterMs()
            Timber.w("[LOGGING_RATE_LIMIT] Rate limit exceeded: $packageName.$methodName (retry after ${retryAfter}ms)")
        }

        return allowed
    }

    /**
     * Clean up old buckets that haven't been used in 5 minutes.
     * Call this periodically to prevent memory leaks.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val threshold = now - 300_000L // 5 minutes

        val toRemove = mutableListOf<String>()

        buckets.forEach { (key, bucket) ->
            if (bucket.lastRefill < threshold) {
                toRemove.add(key)
            }
        }

        toRemove.forEach { buckets.remove(it) }

        if (toRemove.isNotEmpty()) {
            Timber.d("[LOGGING_RATE_LIMIT] Cleaned up ${toRemove.size} inactive buckets")
        }
    }

    /**
     * Reset rate limits for a specific package (for testing).
     */
    fun reset(packageName: String) {
        buckets.keys.removeAll { it.startsWith("$packageName:") }
    }

    /**
     * Reset all rate limits (for testing).
     */
    fun resetAll() {
        buckets.clear()
    }
}
