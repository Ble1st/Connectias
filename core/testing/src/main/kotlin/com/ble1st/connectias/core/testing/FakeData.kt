package com.ble1st.connectias.core.testing

import com.ble1st.connectias.core.model.LogEntry
import com.ble1st.connectias.core.model.LogLevel
import com.ble1st.connectias.core.model.SecurityCheckResult
import com.ble1st.connectias.core.model.SecurityThreat

/**
 * Fake data for testing.
 */
object FakeData {
    
    fun createLogEntry(
        id: String = "1",
        timestamp: Long = System.currentTimeMillis(),
        level: LogLevel = LogLevel.INFO,
        tag: String = "TestTag",
        message: String = "Test message",
        throwable: String? = null
    ) = LogEntry(id, timestamp, level, tag, message, throwable)
    
    fun createSecurityThreat(
        type: String = "root"
    ): SecurityThreat = when (type) {
        "root" -> SecurityThreat.RootDetected("su binary found")
        "debugger" -> SecurityThreat.DebuggerDetected("debugger attached")
        "emulator" -> SecurityThreat.EmulatorDetected("running on emulator")
        "tamper" -> SecurityThreat.TamperDetected("signature mismatch")
        "hook" -> SecurityThreat.HookDetected("frida detected")
        else -> SecurityThreat.RootDetected("unknown")
    }
    
    fun createSecurityCheckResult(
        isSecure: Boolean = true,
        threats: List<SecurityThreat> = emptyList(),
        failedChecks: List<String> = emptyList(),
        allChecksCompleted: Boolean = true
    ) = SecurityCheckResult.create(
        isSecure = isSecure,
        threats = threats,
        failedChecks = failedChecks,
        allChecksCompleted = allChecksCompleted
    )
}
