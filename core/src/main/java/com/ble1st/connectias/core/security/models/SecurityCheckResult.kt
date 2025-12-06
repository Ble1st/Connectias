package com.ble1st.connectias.core.security.models

import kotlin.ConsistentCopyVisibility

@ConsistentCopyVisibility
data class SecurityCheckResult private constructor(
    val isSecure: Boolean,
    val threats: List<SecurityThreat>, // Immutable copy from factory
    val failedChecks: List<String>, // Immutable copy from factory
    val allChecksCompleted: Boolean, // Whether all checks completed successfully
    val timestamp: Long
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
            failedChecks: Collection<String> = emptyList(),
            allChecksCompleted: Boolean = true,
            timestamp: Long = System.currentTimeMillis()
        ): SecurityCheckResult {
            // Fail-secure: if checks failed, mark as not secure
            val finalIsSecure = isSecure && allChecksCompleted && failedChecks.isEmpty()
            
            // Perform defensive copies to ensure immutability
            val immutableThreats = threats.toList()
            val immutableFailedChecks = failedChecks.toList()
            
            return SecurityCheckResult(
                isSecure = finalIsSecure,
                threats = immutableThreats,
                failedChecks = immutableFailedChecks,
                allChecksCompleted = allChecksCompleted,
                timestamp = timestamp
            )
        }
    }
}
sealed class SecurityThreat(open val name: String) {
    data class RootDetected(val method: String) : SecurityThreat("Root Detected")
    data class DebuggerDetected(val method: String) : SecurityThreat("Debugger Detected")
    data class EmulatorDetected(val method: String) : SecurityThreat("Emulator Detected")
    data class TamperDetected(val method: String) : SecurityThreat("Tamper Detected")
    data class HookDetected(val method: String) : SecurityThreat("Hook Detected")
}

