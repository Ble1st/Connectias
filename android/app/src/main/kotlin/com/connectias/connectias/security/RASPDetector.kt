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
            
            // FIX BUG 2: Starte Daemon-Threads zum Drainen von stdout/stderr
            // Daemon-Threads werden automatisch beendet, wenn die VM beendet wird, verhindern Thread-Leaks
            // Zusätzlich verwenden wir readLine() mit Timeout statt readText(), um blockierende Reads zu vermeiden
            val stdoutThread = Thread {
                try {
                    // Verwende readLine() mit begrenztem Puffer statt readText() um unbegrenzte Blockierung zu vermeiden
                    val reader = process.inputStream.bufferedReader()
                    val buffer = CharArray(8192) // 8KB Buffer
                    while (reader.read(buffer) >= 0) {
                        // Lese in Chunks statt alles auf einmal
                    }
                } catch (e: Exception) {
                    // Ignoriere Lesefehler (Stream könnte geschlossen sein)
                } finally {
                    try {
                        process.inputStream.close()
                    } catch (e: Exception) {
                        // Ignoriere Close-Fehler
                    }
                }
            }
            val stderrThread = Thread {
                try {
                    val reader = process.errorStream.bufferedReader()
                    val buffer = CharArray(8192) // 8KB Buffer
                    while (reader.read(buffer) >= 0) {
                        // Lese in Chunks statt alles auf einmal
                    }
                } catch (e: Exception) {
                    // Ignoriere Lesefehler (Stream könnte geschlossen sein)
                } finally {
                    try {
                        process.errorStream.close()
                    } catch (e: Exception) {
                        // Ignoriere Close-Fehler
                    }
                }
            }
            
            // FIX BUG 2: Setze Threads als Daemon, damit sie automatisch beendet werden
            stdoutThread.isDaemon = true
            stderrThread.isDaemon = true
            stdoutThread.start()
            stderrThread.start()
            
            // Warte auf Prozess-Ende mit Timeout (3 Sekunden)
            val timeout = 3000L // 3 Sekunden
            val processExited = process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            
            if (!processExited) {
                // FIX BUG 2: Timeout - zerstöre Prozess und FORCIERE Stream-Schließung VOR Thread-Wartezeit
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
                
                // FIX BUG 2: FORCIERE Stream-Schließung BEVOR auf Thread-Terminierung gewartet wird
                // Dies unterbricht blockierende read() Calls in den Threads und verhindert Thread-Leaks
                try {
                    process.inputStream.close()
                } catch (e: Exception) {
                    android.util.Log.d("RASPDetector", "Failed to close inputStream during timeout: ${e.message}")
                }
                try {
                    process.errorStream.close()
                } catch (e: Exception) {
                    android.util.Log.d("RASPDetector", "Failed to close errorStream during timeout: ${e.message}")
                }
                
                // FIX BUG 2: Warte auf Stream-Threads nach FORCIERTER Stream-Schließung
                // Die Threads sollten jetzt schnell beenden, da Streams geschlossen sind
                try {
                    stdoutThread.join(500) // Max 500ms (Streams sind geschlossen, Threads sollten schnell beenden)
                    stderrThread.join(500) // Max 500ms
                } catch (e: InterruptedException) {
                    android.util.Log.w("RASPDetector", "Interrupted while waiting for stream threads after timeout: ${e.message}")
                    Thread.currentThread().interrupt()
                }
                
                // Prüfe ob Threads noch laufen (unwahrscheinlich nach forcierter Stream-Schließung)
                if (stdoutThread.isAlive || stderrThread.isAlive) {
                    android.util.Log.w("RASPDetector", "Stream threads still running after timeout and forced stream close - daemon threads will terminate on VM exit")
                }
                
                // Timeout = kein Root gefunden (fail-safe)
                return false
            }
            
            // FIX BUG 2: Warte auf stdout/stderr Daemon-Threads mit Timeout
            // Daemon-Threads werden automatisch beendet wenn die VM beendet wird, aber wir warten
            // trotzdem kurz darauf, dass sie ihre Arbeit beenden
            try {
                val joinTimeout = 2000L // 2 Sekunden sollte ausreichen
                stdoutThread.join(joinTimeout)
                stderrThread.join(joinTimeout)
                
                // FIX BUG 2: Prüfe ob Threads noch laufen (unwahrscheinlich für Daemon-Threads mit geschlossenen Streams)
                if (stdoutThread.isAlive) {
                    android.util.Log.w("RASPDetector", "stdout daemon thread still alive after join - should terminate automatically")
                }
                if (stderrThread.isAlive) {
                    android.util.Log.w("RASPDetector", "stderr daemon thread still alive after join - should terminate automatically")
                }
            } catch (e: InterruptedException) {
                android.util.Log.w("RASPDetector", "Interrupted while waiting for stream threads: ${e.message}")
                Thread.currentThread().interrupt()
                // FIX BUG 2: Daemon-Threads werden automatisch beendet, kein Leak-Risiko
                if (stdoutThread.isAlive || stderrThread.isAlive) {
                    android.util.Log.d("RASPDetector", "Stream daemon threads still running after interrupt - will terminate automatically")
                }
            }
            
            // FIX BUG 2: Schließe Streams explizit um sicherzustellen, dass Threads nicht blockieren
            // Dies sollte bereits durch process.exitValue() geschehen, aber sicher ist sicher
            try {
                process.inputStream.close()
            } catch (e: Exception) {
                // Ignoriere Close-Fehler
            }
            try {
                process.errorStream.close()
            } catch (e: Exception) {
                // Ignoriere Close-Fehler
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
