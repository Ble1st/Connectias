package com.ble1st.connectias.core.security

import com.ble1st.connectias.core.security.debug.DebuggerDetector
import com.ble1st.connectias.core.security.emulator.EmulatorDetector
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.security.models.SecurityThreat
import com.ble1st.connectias.core.security.root.RootDetector
import com.ble1st.connectias.core.security.tamper.TamperDetector
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaspManager @Inject constructor(
    private val rootDetector: RootDetector,
    private val debuggerDetector: DebuggerDetector,
    private val tamperDetector: TamperDetector,
    private val emulatorDetector: EmulatorDetector
) {
    
    suspend fun performSecurityChecks(): SecurityCheckResult {
        Timber.d("Performing comprehensive security checks")
        
        val threats = mutableListOf<SecurityThreat>()
        
        // Root Detection
        val rootResult = rootDetector.detectRoot()
        if (rootResult.isRooted) {
            rootResult.detectionMethods.forEach { method ->
                threats.add(SecurityThreat.RootDetected(method))
            }
        }
        
        // Debugger Detection
        val debuggerResult = debuggerDetector.detectDebugger()
        if (debuggerResult.isDebuggerAttached) {
            debuggerResult.detectionMethods.forEach { method ->
                threats.add(SecurityThreat.DebuggerDetected(method))
            }
        }
        
        // Tamper Detection
        val tamperResult = tamperDetector.detectTampering()
        if (tamperResult.isTampered) {
            tamperResult.detectionMethods.forEach { method ->
                threats.add(SecurityThreat.TamperDetected(method))
            }
        }
        
        // Emulator Detection
        val emulatorResult = emulatorDetector.detectEmulator()
        if (emulatorResult.isEmulator) {
            emulatorResult.detectionMethods.forEach { method ->
                threats.add(SecurityThreat.EmulatorDetected(method))
            }
        }
        
        val isSecure = threats.isEmpty()
        
        Timber.i("Security check completed. Secure: $isSecure, Threats: ${threats.size}")
        
        return SecurityCheckResult(
            isSecure = isSecure,
            threats = threats,
            timestamp = System.currentTimeMillis()
        )
    }
}

