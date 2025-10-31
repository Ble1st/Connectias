package com.connectias.connectias

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class PluginProcessService : Service() {
    
    private external fun connectiasExecutePlugin(pluginId: String): String
    
    companion object {
        const val TAG = "PluginProcessService"
        
        init {
            System.loadLibrary("connectias_ffi")
        }
    }
    
    override fun onBind(intent: Intent): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pluginId = intent?.getStringExtra("PLUGIN_ID")
        val resultReceiver = intent?.getParcelableExtra<android.os.ResultReceiver>("RESULT_RECEIVER")
        
        if (pluginId == null) {
            Log.w(TAG, "No plugin ID provided")
            resultReceiver?.send(android.app.Activity.RESULT_CANCELED, android.os.Bundle().apply {
                putString("error", "No plugin ID provided")
            })
            stopSelf(startId)
            return START_NOT_STICKY
        }
        
        // Validiere pluginId vor Verwendung
        if (!isValidPluginId(pluginId)) {
            Log.w(TAG, "Invalid plugin ID: $pluginId")
            resultReceiver?.send(android.app.Activity.RESULT_CANCELED, android.os.Bundle().apply {
                putString("error", "Invalid plugin ID format")
                putString("pluginId", pluginId)
            })
            stopSelf(startId)
            return START_NOT_STICKY
        }
        
        try {
            val result = connectiasExecutePlugin(pluginId)
            Log.d(TAG, "Plugin executed successfully: $result")
            
            // Sende Erfolg an ResultReceiver
            resultReceiver?.send(android.app.Activity.RESULT_OK, android.os.Bundle().apply {
                putString("result", result)
                putString("pluginId", pluginId)
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Plugin execution failed", e)
            
            // Sende Fehler an ResultReceiver
            resultReceiver?.send(android.app.Activity.RESULT_CANCELED, android.os.Bundle().apply {
                putString("error", e.message ?: "Unknown error")
                putString("pluginId", pluginId)
            })
        } finally {
            // Stoppe Service nach Ausführung
            stopSelf(startId)
        }
        
        return START_NOT_STICKY
    }
    
    /// Validiert Plugin-ID gegen Command Injection und andere Sicherheitsprobleme
    /// Erlaubt nur alphanumerische Zeichen, Bindestriche und Unterstriche
    /// Maximale Länge: 64 Zeichen
    /// MINDESTENS ein alphanumerisches Zeichen erforderlich (keine reinen Sonderzeichen)
    private fun isValidPluginId(pluginId: String): Boolean {
        if (pluginId.isEmpty() || pluginId.length > 64) {
            return false
        }
        
        // Erlaube nur alphanumerische Zeichen, Bindestriche und Unterstriche
        // UND erzwinge mindestens ein alphanumerisches Zeichen
        val allowedPattern = Regex("^(?=.*[A-Za-z0-9])[A-Za-z0-9_-]+$")
        return pluginId.matches(allowedPattern)
    }
}
