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
        
        // Write to database asynchronously
        loggingScope.launch {
            try {
                logMessageUseCase(
                    level = level,
                    tag = tag ?: "Unknown",
                    message = message,
                    throwable = t
                )
            } catch (e: Exception) {
                // Don't log logging errors to avoid infinite loops
                // Just print to system log as fallback
                Log.e("ConnectiasLoggingTree", "Failed to persist log", e)
            }
        }
    }
    
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        // Log all levels - filtering can be done in the UI
        return true
    }
}
