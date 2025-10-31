package com.connectias.connectias.security

import android.content.Context
import java.io.File

class RASPDetector(private val context: Context) {
    
    fun detectRoot(): Boolean {
        // Prüfe klassische Root-Pfade
        val legacyPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        // Prüfe auf Root in Legacy-Pfaden (mit Exception-Handling)
        for (path in legacyPaths) {
            try {
                if (File(path).exists()) {
                    return true
                }
            } catch (e: SecurityException) {
                // Ignoriere SecurityException und prüfe weiter
                continue
            } catch (e: Exception) {
                // Andere Exceptions loggen aber nicht weiterwerfen
                android.util.Log.w("RASPDetector", "Error checking path $path: ${e.message}")
                continue
            }
        }
        
        // Prüfe auf Magisk (moderne Root-Lösung)
        val magiskPaths = arrayOf(
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/sbin/magisk",
            "/data/adb/magisk",
            "/cache/magisk.log"
        )
        
        for (path in magiskPaths) {
            try {
                if (File(path).exists()) {
                    return true
                }
            } catch (e: SecurityException) {
                continue
            } catch (e: Exception) {
                android.util.Log.w("RASPDetector", "Error checking Magisk path $path: ${e.message}")
                continue
            }
        }
        
        // Prüfe auf 'su' in PATH (mit Timeout und asynchronem stdout/stderr handling)
        // WICHTIG: Diese Operation läuft auf Background-Thread, nicht auf Main-Thread
        try {
            val process = Runtime.getRuntime().exec("which su")
            
            // Starte Threads zum Drainen von stdout/stderr (verhindert Deadlock)
            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // Ignoriere Lesefehler
                }
            }
            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // Ignoriere Lesefehler
                }
            }
            stdoutThread.start()
            stderrThread.start()
            
            // Warte auf Prozess-Ende mit Timeout (3 Sekunden)
            val timeout = 3000L // 3 Sekunden
            val processExited = process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            
            if (!processExited) {
                // FIX BUG 5: Timeout - zerstöre Prozess und warte auf Stream-Threads vor Return
                android.util.Log.w("RASPDetector", "Process timeout after ${timeout}ms - destroying process")
                process.destroy()
                try {
                    // Warte kurz auf destroy, dann force-kill falls nötig
                    if (!process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly()
                    }
                } catch (e: InterruptedException) {
                    android.util.Log.w("RASPDetector", "Interrupted while waiting for process destruction: ${e.message}")
                    Thread.currentThread().interrupt()
                }
                
                // FIX BUG 5: Warte auf Stream-Threads auch bei Timeout, um Leaks zu vermeiden
                try {
                    stdoutThread.join(2000) // Max 2 Sekunden
                    stderrThread.join(2000) // Max 2 Sekunden
                } catch (e: InterruptedException) {
                    android.util.Log.w("RASPDetector", "Interrupted while waiting for stream threads after timeout: ${e.message}")
                    Thread.currentThread().interrupt()
                }
                
                // Prüfe ob Threads noch laufen
                if (stdoutThread.isAlive || stderrThread.isAlive) {
                    android.util.Log.w("RASPDetector", "Stream threads still running after process timeout - potential leak")
                }
                
                // Timeout = kein Root gefunden (fail-safe)
                return false
            }
            
            // FIX BUG 5: Warte auf stdout/stderr Threads mit längeren Timeouts und sicherer Behandlung
            // Wenn join() fehlschlägt oder Timeout erreicht, müssen wir sicherstellen, dass Threads
            // nicht unbegrenzt laufen. Da wir Threads als daemon nicht setzen können, müssen wir
            // sicherstellen, dass sie beendet werden.
            try {
                // Erhöhe Timeout auf 3 Sekunden für große Outputs
                val joinTimeout = 3000L
                stdoutThread.join(joinTimeout)
                stderrThread.join(joinTimeout)
                
                // FIX BUG 5: Prüfe ob Threads noch laufen und logge Warnung
                if (stdoutThread.isAlive) {
                    android.util.Log.w("RASPDetector", "stdout thread did not complete within timeout - thread may leak")
                }
                if (stderrThread.isAlive) {
                    android.util.Log.w("RASPDetector", "stderr thread did not complete within timeout - thread may leak")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("RASPDetector", "Interrupted while waiting for stream threads: ${e.message}")
                Thread.currentThread().interrupt()
                // FIX BUG 5: Threads könnten noch laufen - logge Warnung
                if (stdoutThread.isAlive || stderrThread.isAlive) {
                    android.util.Log.w("RASPDetector", "Stream threads may still be running after interrupt")
                }
            }
            
            val exitCode = process.exitValue()
            if (exitCode == 0) {
                return true
            }
        } catch (e: SecurityException) {
            // Ignoriere SecurityException
            android.util.Log.d("RASPDetector", "SecurityException beim Prüfen von 'su': ${e.message}")
        } catch (e: InterruptedException) {
            android.util.Log.w("RASPDetector", "InterruptedException beim Prüfen von 'su': ${e.message}")
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            android.util.Log.w("RASPDetector", "Error checking 'su' in PATH: ${e.message}")
        }
        
        // Keine Root-Indikatoren gefunden
        return false
    }
    
    fun detectDebugger(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
    
    fun detectEmulator(): Boolean {
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        
        return (brand.startsWith("generic") && device.startsWith("generic")) ||
               "google_sdk" == product ||
               model.contains("Emulator") ||
               model.contains("Android SDK")
    }
    
    fun checkIntegrity(): Boolean {
        // Package-Signatur-Prüfung
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // API 28+ - verwende GET_SIGNING_CERTIFICATES
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                // API < 28 - verwende deprecated GET_SIGNATURES als Fallback
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }
            
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                // Neue API - verwende signingInfo
                val signingInfo = packageInfo.signingInfo
                signingInfo != null && (
                    signingInfo.apkContentsSigners.isNotEmpty() ||
                    signingInfo.signingCertificateHistory.isNotEmpty()
                )
            } else {
                // Alte API - verwende signatures
                val signatures = packageInfo.signatures
                signatures != null && signatures.isNotEmpty()
            }
        } catch (e: Exception) {
            return false
        }
    }
}
