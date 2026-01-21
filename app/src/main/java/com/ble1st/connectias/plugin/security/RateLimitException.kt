package com.ble1st.connectias.plugin.security

/**
 * Exception thrown when an IPC method call exceeds its rate limit
 * 
 * @param methodName The name of the IPC method that was rate limited
 * @param pluginId The plugin ID that triggered the rate limit (if applicable)
 * @param retryAfterMs Milliseconds to wait before retrying
 */
class RateLimitException(
    val methodName: String,
    val pluginId: String? = null,
    val retryAfterMs: Long = 1000L
) : Exception("Rate limit exceeded for method: $methodName${pluginId?.let { " (plugin: $it)" } ?: ""}. Retry after ${retryAfterMs}ms")
