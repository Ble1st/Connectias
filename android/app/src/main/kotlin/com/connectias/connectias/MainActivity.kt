package com.connectias.connectias

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.connectias.connectias.security.EnhancedRASPDetector
import android.util.Log

class MainActivity : FlutterActivity() {
    private val CHANNEL = "connectias/security"
    private lateinit var rasp: EnhancedRASPDetector
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        rasp = EnhancedRASPDetector(this)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "performSecurityCheck" -> {
                            val check = rasp.performCheck()
                            result.success(mapOf(
                                "passed" to check.passed,
                                "details" to check.details
                            ))
                        }
                        "detectRoot" -> {
                            val rootDetected = rasp.custom.detectRoot()
                            result.success(rootDetected)
                        }
                        "detectDebugger" -> {
                            val debuggerDetected = rasp.custom.detectDebugger()
                            result.success(debuggerDetected)
                        }
                        "detectEmulator" -> {
                            val emulatorDetected = rasp.custom.detectEmulator()
                            result.success(emulatorDetected)
                        }
                        else -> result.notImplemented()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in security method call: ${call.method}", e)
                    result.error("SECURITY_ERROR", 
                                "Security check failed: ${e.message}", 
                                e.toString())
                }
            }
    }
}
