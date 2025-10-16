package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.api.PluginLogger
import com.ble1st.connectias.storage.PluginDatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class PluginLoggerImpl(
    private val context: Context,
    private val pluginId: String
) : PluginLogger {
    
    private val databaseManager = PluginDatabaseManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun debug(message: String) {
        Timber.d("Plugin $pluginId: $message")
        logToDatabase("DEBUG", message, null)
    }
    
    override fun info(message: String) {
        Timber.i("Plugin $pluginId: $message")
        logToDatabase("INFO", message, null)
    }
    
    override fun warn(message: String) {
        Timber.w("Plugin $pluginId: $message")
        logToDatabase("WARN", message, null)
    }
    
    override fun error(message: String, throwable: Throwable?) {
        Timber.e(throwable, "Plugin $pluginId: $message")
        logToDatabase("ERROR", message, throwable?.stackTraceToString())
    }
    
    private fun logToDatabase(level: String, message: String, stackTrace: String?) {
        scope.launch {
            try {
                // For now, just log to Timber
                // In a real implementation, this would save to database
                Timber.d("Plugin $pluginId [$level]: $message")
                if (stackTrace != null) {
                    Timber.d("Stack trace: $stackTrace")
                }
            } catch (e: Exception) {
                Timber.e(e, "Plugin $pluginId: Error logging to database")
            }
        }
    }
}