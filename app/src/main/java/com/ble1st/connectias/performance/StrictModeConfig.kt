package com.ble1st.connectias.performance

import android.os.Build
import android.os.StrictMode
import timber.log.Timber

/**
 * StrictMode configuration for detecting performance issues during development.
 * 
 * Phase 8: Performance & Monitoring
 * 
 * StrictMode hilft bei der Erkennung von:
 * - Disk I/O auf Main Thread
 * - Network I/O auf Main Thread
 * - Memory Leaks
 * - Unbuffered I/O
 * - Custom Slow Calls
 * 
 * WICHTIG: Nur in Debug Builds aktivieren!
 */
object StrictModeConfig {
    
    /**
     * Aktiviert StrictMode für Debug Builds.
     * Sollte in Application.onCreate() aufgerufen werden.
     */
    fun enableStrictMode(isDebug: Boolean) {
        if (!isDebug) {
            Timber.d("StrictMode: Disabled (Release Build)")
            return
        }
        
        Timber.d("StrictMode: Enabling for Debug Build")
        
        // Thread Policy - Erkennt Violations auf dem Main Thread
        val threadPolicy = StrictMode.ThreadPolicy.Builder()
            .detectAll() // Alle Thread-Violations erkennen
            .penaltyLog() // In Logcat ausgeben
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(
                        { runnable -> runnable.run() },
                        { violation ->
                            Timber.e(violation, "StrictMode ThreadPolicy Violation")
                        }
                    )
                }
            }
            .build()
        
        StrictMode.setThreadPolicy(threadPolicy)
        
        // VM Policy - Erkennt Memory Leaks und andere VM-Violations
        val vmPolicy = StrictMode.VmPolicy.Builder()
            .detectAll() // Alle VM-Violations erkennen
            .penaltyLog() // In Logcat ausgeben
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(
                        { runnable -> runnable.run() },
                        { violation ->
                            Timber.e(violation, "StrictMode VmPolicy Violation")
                        }
                    )
                }
                
                // Spezifische Detections
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detectContentUriWithoutPermission()
                    detectUntaggedSockets()
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    detectCredentialProtectedWhileLocked()
                    detectImplicitDirectBoot()
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    detectIncorrectContextUse()
                    detectUnsafeIntentLaunch()
                }
            }
            .build()
        
        StrictMode.setVmPolicy(vmPolicy)
        
        Timber.i("StrictMode: Enabled successfully")
    }
    
    /**
     * Deaktiviert StrictMode.
     * Nützlich für spezifische Code-Bereiche, die bekannte Violations haben.
     */
    fun disableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX)
        Timber.d("StrictMode: Disabled")
    }
    
    /**
     * Führt einen Code-Block ohne StrictMode aus.
     * Nützlich für Legacy-Code oder Third-Party Libraries.
     */
    inline fun <T> withoutStrictMode(block: () -> T): T {
        val oldThreadPolicy = StrictMode.getThreadPolicy()
        val oldVmPolicy = StrictMode.getVmPolicy()
        
        try {
            disableStrictMode()
            return block()
        } finally {
            StrictMode.setThreadPolicy(oldThreadPolicy)
            StrictMode.setVmPolicy(oldVmPolicy)
        }
    }
}
