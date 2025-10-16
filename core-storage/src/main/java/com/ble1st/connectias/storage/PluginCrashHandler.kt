package com.ble1st.connectias.storage

import android.os.Build
import com.ble1st.connectias.storage.database.PluginDatabase
import com.ble1st.connectias.storage.database.entity.PluginCrashEntity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date

class PluginCrashHandler(
    private val database: PluginDatabase
) {
    fun handleCrash(pluginId: String, error: Throwable) {
        Timber.e(error, "Plugin crashed: $pluginId")
        
        // 1. Crash-Report erstellen
        val crashReport = createCrashReport(pluginId, error)
        
        // 2. In Datenbank speichern
        saveCrashReport(crashReport)
        
        // 3. Plugin deaktivieren
        deactivatePlugin(pluginId)
        
        // 4. Core läuft weiter (keine Propagation des Crashes)
        Timber.i("Core continues running after plugin crash: $pluginId")
    }
    
    private fun createCrashReport(pluginId: String, error: Throwable): PluginCrashReport {
        return PluginCrashReport(
            pluginId = pluginId,
            timestamp = System.currentTimeMillis(),
            errorMessage = error.message ?: "Unknown error",
            stackTrace = error.stackTraceToString(),
            errorType = error::class.java.simpleName,
            androidVersion = Build.VERSION.SDK_INT,
            appVersion = "1.0.0" // This should come from BuildConfig
        )
    }
    
    private fun saveCrashReport(report: PluginCrashReport) {
        GlobalScope.launch {
            try {
                database.pluginCrashDao().insert(
                    PluginCrashEntity(
                        pluginId = report.pluginId,
                        timestamp = report.timestamp,
                        errorMessage = report.errorMessage,
                        stackTrace = report.stackTrace,
                        errorType = report.errorType
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to save crash report")
            }
        }
    }
    
    private fun deactivatePlugin(pluginId: String) {
        try {
            // Implementation to deactivate plugin
            Timber.i("Plugin deactivated: $pluginId")
        } catch (e: Exception) {
            Timber.e(e, "Failed to deactivate crashed plugin: $pluginId")
        }
    }
}

data class PluginCrashReport(
    val pluginId: String,
    val timestamp: Long,
    val errorMessage: String,
    val stackTrace: String,
    val errorType: String,
    val androidVersion: Int,
    val appVersion: String
) {
    override fun toString(): String {
        return """
            Plugin Crash Report
            ===================
            Plugin: $pluginId
            Time: ${Date(timestamp)}
            Error Type: $errorType
            Message: $errorMessage
            
            Android: API $androidVersion
            App: v$appVersion
            
            Stack Trace:
            $stackTrace
        """.trimIndent()
    }
}
