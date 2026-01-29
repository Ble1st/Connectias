@file:Suppress("unused")

package com.ble1st.connectias.plugin.security

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Rate limiter for IPC method calls using Token Bucket algorithm
 * 
 * Provides DoS protection by limiting the rate of IPC calls per method and plugin.
 * Uses a token bucket algorithm with configurable per-second, per-minute, and burst limits.
 */
class IPCRateLimiter {
    
    /**
     * Rate limit configuration for a method
     */
    data class RateLimit(
        val perSecond: Int,
        val perMinute: Int,
        val burst: Int
    )
    
    /**
     * Token bucket state for tracking rate limits
     */
    private data class TokenBucket(
        var tokens: Double = 0.0,
        var lastRefill: Long = System.currentTimeMillis(),
        val perSecond: Double,
        val perMinute: Double,
        val burst: Double,
        val maxTokens: Double
    ) {
        fun refill(now: Long) {
            val elapsedSeconds = (now - lastRefill) / 1000.0
            if (elapsedSeconds > 0) {
                // Refill tokens based on per-second rate
                tokens = min(maxTokens, tokens + (perSecond * elapsedSeconds))
                lastRefill = now
            }
        }
        
        fun consume(tokensToConsume: Double = 1.0): Boolean {
            val now = System.currentTimeMillis()
            refill(now)
            
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume
                return true
            }
            return false
        }
        
        fun getRetryAfterMs(): Long {
            val now = System.currentTimeMillis()
            refill(now)
            
            if (tokens >= 1.0) {
                return 0L
            }
            
            // Calculate how long to wait for one token
            val tokensNeeded = 1.0 - tokens
            val secondsNeeded = tokensNeeded / perSecond
            return (secondsNeeded * 1000).toLong().coerceAtLeast(100L)
        }
    }
    
    /**
     * Rate limits per method name
     */
    private val rateLimits = mapOf(
        "loadPlugin" to RateLimit(perSecond = 1, perMinute = 10, burst = 2),
        "loadPluginFromDescriptor" to RateLimit(perSecond = 1, perMinute = 10, burst = 2),
        "enablePlugin" to RateLimit(perSecond = 2, perMinute = 20, burst = 3),
        "disablePlugin" to RateLimit(perSecond = 2, perMinute = 20, burst = 3),
        "unloadPlugin" to RateLimit(perSecond = 1, perMinute = 10, burst = 2),
        "ping" to RateLimit(perSecond = 60, perMinute = 600, burst = 100),
        "getLoadedPlugins" to RateLimit(perSecond = 10, perMinute = 100, burst = 20),
        "getPluginMetadata" to RateLimit(perSecond = 10, perMinute = 100, burst = 20),
        "getSandboxPid" to RateLimit(perSecond = 10, perMinute = 100, burst = 20),
        "getSandboxMemoryUsage" to RateLimit(perSecond = 5, perMinute = 60, burst = 10),
        "getPluginMemoryUsage" to RateLimit(perSecond = 5, perMinute = 60, burst = 10)
    )
    
    /**
     * Token buckets per method and plugin combination
     * Key format: "methodName:pluginId" or "methodName" for global limits
     */
    private val tokenBuckets = ConcurrentHashMap<String, TokenBucket>()
    
    /**
     * Check if a method call is allowed based on rate limits
     * 
     * @param methodName Name of the IPC method
     * @param pluginId Optional plugin ID for per-plugin rate limiting
     * @throws RateLimitException if rate limit is exceeded
     */
    @Synchronized
    fun checkRateLimit(methodName: String, pluginId: String? = null) {
        val limit = rateLimits[methodName]
            ?: return // No rate limit configured for this method
        
        // Use per-plugin bucket if pluginId is provided, otherwise use global bucket
        val bucketKey = if (pluginId != null) {
            "$methodName:$pluginId"
        } else {
            methodName
        }
        
        val bucket = tokenBuckets.getOrPut(bucketKey) {
            TokenBucket(
                tokens = limit.burst.toDouble(),
                perSecond = limit.perSecond.toDouble(),
                perMinute = limit.perMinute.toDouble(),
                burst = limit.burst.toDouble(),
                maxTokens = limit.burst.toDouble()
            )
        }
        
        // Check per-second limit (primary check)
        if (!bucket.consume(1.0)) {
            val retryAfter = bucket.getRetryAfterMs()
            Timber.w("Rate limit exceeded for method: $methodName${pluginId?.let { " (plugin: $it)" } ?: ""}. Retry after ${retryAfter}ms")
            throw RateLimitException(methodName, pluginId, retryAfter)
        }
        
        // Also check per-minute limit using a separate bucket
        val minuteBucketKey = "${bucketKey}_minute"
        val minuteBucket = tokenBuckets.getOrPut(minuteBucketKey) {
            TokenBucket(
                tokens = limit.perMinute.toDouble(),
                perSecond = limit.perMinute.toDouble() / 60.0, // Refill rate for minute bucket
                perMinute = limit.perMinute.toDouble(),
                burst = limit.perMinute.toDouble(),
                maxTokens = limit.perMinute.toDouble()
            )
        }
        
        if (!minuteBucket.consume(1.0)) {
            val retryAfter = 60000L / limit.perMinute // Approximate retry after for minute limit
            Timber.w("Per-minute rate limit exceeded for method: $methodName${pluginId?.let { " (plugin: $it)" } ?: ""}. Retry after ${retryAfter}ms")
            throw RateLimitException(methodName, pluginId, retryAfter)
        }
    }
    
    /**
     * Reset rate limits for a specific method and plugin
     * Useful for testing or manual reset
     */
    fun resetRateLimit(methodName: String, pluginId: String? = null) {
        val bucketKey = if (pluginId != null) {
            "$methodName:$pluginId"
        } else {
            methodName
        }
        tokenBuckets.remove(bucketKey)
        tokenBuckets.remove("${bucketKey}_minute")
    }
    
    /**
     * Reset all rate limits
     */
    fun resetAll() {
        tokenBuckets.clear()
    }
    
    /**
     * Get current token count for a method (for debugging/monitoring)
     */
    fun getTokenCount(methodName: String, pluginId: String? = null): Double {
        val bucketKey = if (pluginId != null) {
            "$methodName:$pluginId"
        } else {
            methodName
        }
        
        val bucket = tokenBuckets[bucketKey] ?: return 0.0
        val now = System.currentTimeMillis()
        bucket.refill(now)
        return bucket.tokens
    }
}
