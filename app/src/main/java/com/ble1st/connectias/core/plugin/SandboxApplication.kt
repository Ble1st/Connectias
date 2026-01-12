package com.ble1st.connectias.core.plugin

import android.app.Application
import timber.log.Timber

/**
 * Minimal Application class for isolated sandbox process
 * 
 * IMPORTANT: This runs in an isolated process with NO access to:
 * - SharedPreferences
 * - Database
 * - KeyStore / EncryptedSharedPreferences
 * - Network (without permission)
 * - External Storage
 * 
 * Keep this class MINIMAL - no Hilt, no heavy dependencies
 * 
 * @since 2.0.0
 */
class SandboxApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Minimal Timber setup for logging
        if (timber.log.Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("[SANDBOX APPLICATION] Isolated process started: ${android.os.Process.myPid()}")
        Timber.i("[SANDBOX APPLICATION] Process name: ${getCurrentProcessName()}")
    }
    
    private fun getCurrentProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val manager = getSystemService(android.app.ActivityManager::class.java)
            manager?.runningAppProcesses
                ?.find { it.pid == pid }
                ?.processName ?: "unknown"
        } catch (e: Exception) {
            "unknown (${e.message})"
        }
    }
}
