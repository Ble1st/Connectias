package com.ble1st.connectias.plugin

import android.content.Context
import com.ble1st.connectias.api.PluginLogger
import com.ble1st.connectias.storage.PluginDatabaseManager
import com.ble1st.connectias.storage.database.entity.PluginLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class PluginLoggerImpl(
    private val context: Context,
    private val pluginId: String
) : PluginLogger {
    
    private val databaseManager = PluginDatabaseManager(context)
    
    override fun d(tag: String, message: String) {
        Timber.d("Plugin $pluginId [$tag]: $message")
        logToDatabase("DEBUG", tag, message)
    }
    
    override fun i(tag: String, message: String) {
        Timber.i("Plugin $pluginId [$tag]: $message")
        logToDatabase("INFO", tag, message)
    }
    
    override fun w(tag: String, message: String) {
        Timber.w("Plugin $pluginId [$tag]: $message")
        logToDatabase("WARN", tag, message)
    }
    
    override fun e(tag: String, message: String, throwable: Throwable?) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        
        Timber.e(throwable, "Plugin $pluginId [$tag]: $message")
        logToDatabase("ERROR", tag, fullMessage)
    }
    
    private fun logToDatabase(level: String, tag: String, message: String) {
        // Async logging to database
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val logEntity = PluginLogEntity(
                    pluginId = pluginId,
                    timestamp = System.currentTimeMillis(),
                    level = level,
                    tag = tag,
                    message = message
                )
                
                databaseManager.getPluginLogDao(pluginId).insertLog(logEntity)
            } catch (e: Exception) {
                Timber.e(e, "Failed to log to database for plugin $pluginId")
            }
        }
    }
}
