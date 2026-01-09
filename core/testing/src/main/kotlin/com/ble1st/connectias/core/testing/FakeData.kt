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
        "root" -> SecurityThreat.RootDetected()
        "debugger" -> SecurityThreat.DebuggerDetected()
        "emulator" -> SecurityThreat.EmulatorDetected()
        "tamper" -> SecurityThreat.TamperDetected()
        "hook" -> SecurityThreat.HookDetected()
        else -> SecurityThreat.RootDetected()
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
