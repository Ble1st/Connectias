package com.ble1st.connectias.plugin.logging

import android.util.Log
import com.ble1st.connectias.core.domain.LogMessageUseCase
import com.ble1st.connectias.core.model.LogLevel
import com.ble1st.connectias.plugin.logging.IPluginLogBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receives plugin logs from sandbox/UI processes and persists them in the main-process DB.
 * Also mirrors them to Logcat for local debugging.
 *
 * IMPORTANT:
 * - Must not use Timber here to avoid double-writing via ConnectiasLoggingTree.
 */
@Singleton
class PluginLogBridgeImpl @Inject constructor(
    private val logMessageUseCase: LogMessageUseCase
) : IPluginLogBridge.Stub() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun log(
        pluginId: String,
        priority: Int,
        tag: String?,
        message: String,
        threadName: String?,
        exceptionTrace: String?,
        timestamp: Long
    ) {
        val safePluginId = pluginId.ifBlank { "unknown" }
        val safeTag = tag?.takeIf { it.isNotBlank() } ?: "Plugin"
        val dbTag = "PLUGIN/$safePluginId:$safeTag"

        val fullMessage = buildString {
            append(message)
            if (!threadName.isNullOrBlank()) {
                append(" (thread=")
                append(threadName)
                append(')')
            }
            if (!exceptionTrace.isNullOrBlank()) {
                append("\n")
                append(exceptionTrace)
            }
        }

        // Mirror to Logcat (best-effort).
        Log.println(priority, dbTag, fullMessage)

        val level = when (priority) {
            Log.VERBOSE -> LogLevel.VERBOSE
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            Log.ASSERT -> LogLevel.ASSERT
            else -> LogLevel.DEBUG
        }

        // Persist to DB asynchronously (main process only).
        scope.launch {
            logMessageUseCase(
                level = level,
                tag = dbTag,
                message = fullMessage,
                throwable = null
            )
        }
    }
}

