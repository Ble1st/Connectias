package com.connectias.connectias.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer

class EnhancedRASPDetector(private val context: Context) {
    private val rootBeer = RootBeer(context)
    private val custom = RASPDetector(context)
    
    fun performCheck(): SecurityResult {
        val rooted = rootBeer.isRooted || custom.detectRoot()
        val debugged = custom.detectDebugger()
        val emulator = custom.detectEmulator()
        val integrityOk = custom.checkIntegrity()
        
        return SecurityResult(
            passed = !rooted && !debugged && !emulator && integrityOk,
            details = mapOf(
                "rooted" to rooted,
                "debugged" to debugged,
                "emulator" to emulator,
                "integrity" to integrityOk
            )
        )
    }
}

data class SecurityResult(
    val passed: Boolean,
    val details: Map<String, Boolean>
)
