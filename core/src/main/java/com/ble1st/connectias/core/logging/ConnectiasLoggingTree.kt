package com.ble1st.connectias.core.logging

import android.util.Log
import com.ble1st.connectias.core.domain.LogMessageUseCase
import com.ble1st.connectias.core.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom Timber Tree that persists logs to the database.
 * Logs are written asynchronously to avoid blocking the main thread.
 */
@Singleton
class ConnectiasLoggingTree @Inject constructor(
    private val logMessageUseCase: LogMessageUseCase
) : Timber.Tree() {
    
    private val loggingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Convert Android Log priority to LogLevel
        val level = when (priority) {
            Log.VERBOSE -> LogLevel.VERBOSE
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            Log.ASSERT -> LogLevel.ASSERT
            else -> LogLevel.DEBUG
        }
        
        // Security: Mask sensitive data in log messages
        val sanitizedMessage = sanitizeLogMessage(message)
        val sanitizedThrowable = t?.let { sanitizeThrowable(it) }
        
        // Write to database asynchronously
        loggingScope.launch {
            try {
                logMessageUseCase(
                    level = level,
                    tag = tag ?: "Unknown",
                    message = sanitizedMessage,
                    throwable = sanitizedThrowable
                )
            } catch (e: Exception) {
                // Don't log logging errors to avoid infinite loops
                // Just print to system log as fallback
                Log.e("ConnectiasLoggingTree", "Failed to persist log", e)
            }
        }
    }
    
    /**
     * Sanitizes log messages by masking sensitive data patterns.
     * Security: Prevents logging of passwords, tokens, API keys, etc.
     */
    private fun sanitizeLogMessage(message: String): String {
        var sanitized = message
        
        // Mask password patterns (password=xxx, pwd=xxx, passwd=xxx)
        sanitized = Regex("(?i)(password|pwd|passwd)\\s*[:=]\\s*[^\\s\"']+", RegexOption.IGNORE_CASE)
            .replace(sanitized) { "${it.value.substringBefore("=")}=***" }
        
        // Mask API keys and tokens (api_key=xxx, token=xxx, secret=xxx)
        sanitized = Regex("(?i)(api[_-]?key|token|secret|auth[_-]?token)\\s*[:=]\\s*[^\\s\"']+", RegexOption.IGNORE_CASE)
            .replace(sanitized) { "${it.value.substringBefore("=")}=***" }
        
        // Mask email addresses in logs (optional, can be enabled if needed)
        // sanitized = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        //     .replace(sanitized) { "***@***.***" }
        
        return sanitized
    }
    
    /**
     * Sanitizes throwable stack traces by masking sensitive data.
     */
    private fun sanitizeThrowable(throwable: Throwable): Throwable {
        // For now, return as-is. Stack traces typically don't contain sensitive data.
        // If needed, can add sanitization here.
        return throwable
    }
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Log all levels - filtering can be done in the UI
        return true
    }
}
