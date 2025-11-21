package com.ble1st.connectias.core.security.models

data class SecurityCheckResult(
    val isSecure: Boolean,
    val threats: List<SecurityThreat>, // Defensive copy in companion factory
    val failedChecks: List<String> = emptyList(), // Detector names that failed
    val allChecksCompleted: Boolean = true, // Whether all checks completed successfully
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(!isSecure || threats.isEmpty()) {
            "isSecure can only be true when threats is empty"
        }
        // If checks failed, device is not secure
        require(!(!allChecksCompleted || failedChecks.isNotEmpty()) || !isSecure) {
            "isSecure cannot be true when checks failed or did not complete"
        }
    }
    
    companion object {
        /**
         * Factory method that creates SecurityCheckResult with defensive copy of threats.
         * This ensures the threats list cannot be modified after construction.
         */
        fun create(
            isSecure: Boolean,
            threats: Collection<SecurityThreat>,
            failedChecks: List<String> = emptyList(),
            allChecksCompleted: Boolean = true,
            timestamp: Long = System.currentTimeMillis()
        ): SecurityCheckResult {
            // Fail-secure: if checks failed, mark as not secure
            val finalIsSecure = isSecure && allChecksCompleted && failedChecks.isEmpty()
            
            return SecurityCheckResult(
                isSecure = finalIsSecure,
                threats = threats.toList(), // Defensive copy
                failedChecks = failedChecks.toList(), // Defensive copy
                allChecksCompleted = allChecksCompleted,
                timestamp = timestamp
            )
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

