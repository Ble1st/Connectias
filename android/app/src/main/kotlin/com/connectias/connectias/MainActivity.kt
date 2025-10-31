package com.connectias.connectias

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.connectias.connectias.security.EnhancedRASPDetector

class MainActivity : FlutterActivity() {
    private val CHANNEL = "connectias/security"
    private lateinit var rasp: EnhancedRASPDetector
    
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        rasp = EnhancedRASPDetector(this)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "performSecurityCheck" -> {
                        val check = rasp.performCheck()
                        result.success(mapOf(
                            "passed" to check.passed,
                            "details" to check.details
                        ))
                    }
                    "detectRoot" -> result.success(rasp.custom.detectRoot())
                    "detectDebugger" -> result.success(rasp.custom.detectDebugger())
                    "detectEmulator" -> result.success(rasp.custom.detectEmulator())
                    else -> result.notImplemented()
                }
            }
    }
}
