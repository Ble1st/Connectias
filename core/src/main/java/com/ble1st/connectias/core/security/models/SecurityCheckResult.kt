package com.ble1st.connectias.core.security.models

data class SecurityCheckResult(
    val isSecure: Boolean,
    private val _threats: List<SecurityThreat>,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Immutable list of threats. The constructor parameter is copied to ensure
     * the list cannot be modified after construction.
     */
    val threats: List<SecurityThreat> = _threats.toList()
    
    init {
        require(isSecure == threats.isEmpty()) {
            "isSecure must be true only when threats is empty"
        }
    }
}
sealed class SecurityThreat {
    data class RootDetected(val method: String) : SecurityThreat()
    data class DebuggerDetected(val method: String) : SecurityThreat()
    data class EmulatorDetected(val method: String) : SecurityThreat()
    data class TamperDetected(val method: String) : SecurityThreat()
    data class HookDetected(val method: String) : SecurityThreat()
}

