package com.ble1st.connectias.storage

import com.ble1st.connectias.api.PluginLogger
import com.ble1st.connectias.storage.database.PluginDatabase
import com.ble1st.connectias.storage.database.entity.PluginLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class PluginLoggerImpl(
    private val pluginId: String,
    private val database: PluginDatabase,
    private val coroutineScope: CoroutineScope
) : PluginLogger {
    
    override fun debug(message: String) {
        log(android.util.Log.DEBUG, "DEBUG", message, null)
    }
    
    override fun info(message: String) {
        log(android.util.Log.INFO, "INFO", message, null)
    }
    
    override fun warn(message: String) {
        log(android.util.Log.WARN, "WARN", message, null)
    }
    
    override fun error(message: String, throwable: Throwable?) {
        log(android.util.Log.ERROR, "ERROR", message, throwable)
    }
    
    private fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        // Log to Timber
        when (level) {
            android.util.Log.DEBUG -> Timber.d("[$pluginId] $message")
            android.util.Log.INFO -> Timber.i("[$pluginId] $message")
            android.util.Log.WARN -> Timber.w("[$pluginId] $message")
            android.util.Log.ERROR -> Timber.e(throwable, "[$pluginId] $message")
        }
        
        // Persist to database
        coroutineScope.launch {
            try {
                database.pluginLogDao().insert(
                    PluginLogEntity(
                        pluginId = pluginId,
                        timestamp = System.currentTimeMillis(),
                        level = level,
                        tag = tag,
                        message = message,
                        throwable = throwable?.stackTraceToString()
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist log entry")
            }
        }
    }
}
