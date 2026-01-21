# API Rate Limiting Documentation

## Overview

API Rate Limiting provides DoS protection by limiting the rate of IPC method calls between the Main Process and Sandbox Process. Uses a Token Bucket algorithm with configurable per-second, per-minute, and burst limits.

## Implementation

### Rate Limiter

Located in: `app/src/main/java/com/ble1st/connectias/plugin/security/IPCRateLimiter.kt`

```kotlin
class IPCRateLimiter {
    fun checkRateLimit(methodName: String, pluginId: String?)
    // Throws RateLimitException if limit exceeded
}
```

### Rate Limits

| Method | Per Second | Per Minute | Burst |
|--------|------------|------------|-------|
| `loadPlugin` | 1 | 10 | 2 |
| `loadPluginFromDescriptor` | 1 | 10 | 2 |
| `enablePlugin` | 2 | 20 | 3 |
| `disablePlugin` | 2 | 20 | 3 |
| `unloadPlugin` | 1 | 10 | 2 |
| `ping` | 60 | 600 | 100 |
| `getLoadedPlugins` | 10 | 100 | 20 |
| `getPluginMetadata` | 10 | 100 | 20 |
| `getSandboxPid` | 10 | 100 | 20 |
| `getSandboxMemoryUsage` | 5 | 60 | 10 |
| `getPluginMemoryUsage` | 5 | 60 | 10 |

## Exception Handling

When a rate limit is exceeded, a `RateLimitException` is thrown:

```kotlin
class RateLimitException(
    val methodName: String,
    val pluginId: String? = null,
    val retryAfterMs: Long = 1000L
) : Exception(...)
```

## Integration

Rate limiting is automatically applied to all IPC methods in `PluginSandboxProxy`:

```kotlin
suspend fun enablePlugin(pluginId: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        // Rate limit check
        rateLimiter.checkRateLimit("enablePlugin", pluginId)
        
        // ... rest of method
    } catch (e: RateLimitException) {
        // Logged to SecurityAuditManager
        Result.failure(e)
    }
}
```

## Security Audit

All rate limit violations are logged to `SecurityAuditManager`:

```kotlin
auditManager.logSecurityEvent(
    eventType = SecurityEventType.API_RATE_LIMITING,
    severity = SecuritySeverity.MEDIUM,
    source = "PluginSandboxProxy",
    pluginId = pluginId,
    message = "Rate limit exceeded for method: $methodName"
)
```

## Best Practices

1. **Handle Rate Limit Exceptions**: Always catch and handle `RateLimitException`
2. **Respect Retry-After**: Wait `retryAfterMs` before retrying
3. **Monitor Logs**: Check SecurityAuditManager for rate limit violations
4. **Optimize Calls**: Reduce IPC call frequency where possible

## Testing

Unit tests are available in:
- `app/src/test/java/com/ble1st/connectias/plugin/security/IPCRateLimiterTest.kt`
- `app/src/test/java/com/ble1st/connectias/core/plugin/PluginSandboxProxyRateLimitTest.kt`

## Configuration

Rate limits are hardcoded in `IPCRateLimiter.kt`. To modify limits, update the `rateLimits` map:

```kotlin
private val rateLimits = mapOf(
    "enablePlugin" to RateLimit(perSecond = 2, perMinute = 20, burst = 3),
    // ... other methods
)
```

## Monitoring

Rate limit violations can be monitored via:
- Security Audit Dashboard
- SecurityAuditManager logs
- Timber logs (warning level)
