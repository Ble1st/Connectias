package com.ble1st.connectias.core.security.models

data class SecurityCheckResult(
    val isSecure: Boolean,
    val threats: List<SecurityThreat>,
    val timestamp: Long = System.currentTimeMillis()
)

sealed class SecurityThreat {
    data class RootDetected(val method: String) : SecurityThreat()
    data class DebuggerDetected(val method: String) : SecurityThreat()
    data class EmulatorDetected(val method: String) : SecurityThreat()
    data class TamperDetected(val method: String) : SecurityThreat()
    data class HookDetected(val method: String) : SecurityThreat()
}

